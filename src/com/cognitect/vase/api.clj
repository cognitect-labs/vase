(ns com.cognitect.vase.api
  "Public functions for submitting a data structure to Vase and
  getting back routes, specs, and even a whole Pedestal service map."
  (:require [clojure.spec :as s]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as i]
            [com.cognitect.vase.routes :as routes]
            [clojure.string :as str]
            [fern :as f]))

(s/def ::path string?)

(defn- interceptor? [v]
  (satisfies? i/IntoInterceptor v))

(s/def ::interceptor  interceptor?)
(s/def ::interceptors (s/coll-of ::interceptor :min-count 1))
(s/def ::route        (s/cat :path ::path
                             :verb #{:get :put :post :delete :head :options :patch}
                             :interceptors (s/or :single ::interceptor
                                                 :vector ::interceptors)))
(s/def ::routes       (s/coll-of ::route :min-count 1))
(s/def ::on-startup   (s/nilable ::interceptors))
(s/def ::on-request   (s/nilable ::interceptors))
(s/def ::api          (s/keys :req-un [::on-startup ::on-request ::routes ::path]))
(s/def ::apis         (s/coll-of ::api :min-count 1))
(s/def ::service-map  (s/keys))
(s/def ::service      (s/keys :req-un [::apis] :opt-un [::service-map]))

(defn- base-route
  [base {:keys [path]}]
  (let [s (str base path)]
    (str/replace s #"//" "/")))

;; TODO - coll? returns true on records. We need to be more specific
;; about whether we have one thing or several
(defn- base-interceptors
  [on-request {:keys [interceptors]}]
  (let [[one-or-many intc] interceptors]
    (case one-or-many
      :single (conj on-request intc)
      :vector (into on-request intc))))

(defn- routes-for-api
  [api]
  (let [base   (:path api "/")
        on-req (:on-request api [])]
    (mapv
     (fn [route]
       [(base-route base route)
        (:verb route)
        (base-interceptors on-req route)])
     (:routes api #{}))))

(defn- collect-routes
  [spec]
  (reduce into #{}
          (map routes-for-api (:apis spec))))

(defn- add-routes
  [service-map all-routes]
  (assoc service-map ::http/routes all-routes))

(defn- collect-startups
  [spec]
  (reduce into []
          (map #(:on-startup %) (:apis spec))))

(defn- add-startups
  [service-map startups]
  (assoc service-map ::startups startups))

(defn dev-mode
  [service-map]
  (assoc service-map ::http/join? false))

(def default-service-map
  {::http/type :jetty
   ::http/port 80})

(defn service-map
  "Given a spec, return a Pedestal service-map
  or :clojure.spec/invalid. If starter-map is provided, the spec's
  settings will be merged into it. If starter-map is not provided then
  the `default-service-map` is used as a starter."
  ([spec]
   (service-map spec default-service-map))
  ([spec starter-map]
   (let [conformed (s/conform ::service spec)]
     (if (s/invalid? conformed)
       conformed
       (-> starter-map
           (merge (:service-map conformed))
           (add-routes (collect-routes conformed))
           (add-startups (collect-startups conformed)))))))

(defn start-service
  [service-map]
  (-> service-map
      (http/create-server)
      (http/start)))
