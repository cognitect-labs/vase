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

(s/def ::route  (s/cat :path ::path
                       :verb #{:get :put :post :delete :head :options :patch}
                       :interceptors (s/or :single ::interceptor
                                           :vector ::interceptors)))
(s/def ::routes (s/coll-of ::route :min-count 1))

(defn routes
  "Return routes for all the APIs in the spec. Each one conforms to
  the spec ::route"
  [spec])

(s/def ::on-startup  (s/nilable ::interceptors))
(s/def ::on-request  (s/nilable ::interceptors))
(s/def ::api         (s/keys :req-un [::on-startup ::on-request ::routes ::path]))
(s/def ::apis        (s/coll-of ::api :min-count 1))
(s/def ::service-map (s/keys))
(s/def ::service     (s/keys :req-un [::apis] :opt-un [::service-map]))

(defn- base-route
  [base path]
  (let [s (str base path)]
    (str/replace s #"//" "/")))

(defn- base-interceptors
  [on-request route-specific]
  (if (coll? route-specific)
    (into on-request route-specific)
    (conj on-request route-specific)))

(defn- routes-for-api
  [api]
  (let [base   (:path api "/")
        on-req (:on-request api [])]
    (mapv
     (fn [r]
       (-> r
          (update 0 #(base-route base %))
          (update 2 #(base-interceptors on-req %))))
     (:routes api []))))

(defn- collect-routes
  [spec]
  (reduce into []
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
           (merge (:service-map spec))
           (add-routes (collect-routes spec))
           (add-startups (collect-startups spec)))))))


(comment

  ;; It's not the easiest thing in the world yet, but it works.
  ;; (Except connection and schema creation)

  (-> (com.cognitect.vase.fern/load-from-file "test/resources/test_descriptor.fern")
      (fern/evaluate 'vase/service)
      (service-map)
      (assoc :io.pedestal.http/join? false)
      (io.pedestal.http/create-server)
      (io.pedestal.http/start))


  )
