(ns com.cognitect.vase.api
  "Public functions for submitting a data structure to Vase and
  getting back routes, specs, and even a whole Pedestal service map."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as i]
            [io.pedestal.interceptor.chain :as chain]))

(s/def ::path string?)

(defn- interceptor? [v]
  (satisfies? i/IntoInterceptor v))

(s/def ::interceptor  interceptor?)
(s/def ::interceptors (s/coll-of ::interceptor :min-count 1))
(s/def ::route-name   keyword?)
(s/def ::route        (s/cat :path ::path
                             :verb #{:get :put :post :delete :head :options :patch}
                             :interceptors (s/or :single ::interceptor
                                             :vector ::interceptors)
                             :route-name (s/? ::route-name)))
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

(defn- base-interceptors
  [on-request {:keys [interceptors]}]
  (let [[one-or-many intc] interceptors]
    (case one-or-many
      :single (conj on-request intc)
      :vector (into on-request intc))))

(defn- routes-for-api
  [api]
  (let [base   (:path api "/")
        on-req (or (:on-request api) [])]
    (mapv
      (fn [route]
        (cond-> [(base-route base route)
                 (:verb route)
                 (base-interceptors on-req route)]
          (some? (:route-name route))
          (into [:route-name (:route-name route)])))
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
   ::http/port 80
   ::http/routes #{}})

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
       (throw (ex-info (str "Can't create service map.\n" (s/explain ::service spec)) {}))
       (-> starter-map
           (merge (:service-map conformed))
           (add-routes (collect-routes conformed))
           (add-startups (collect-startups conformed)))))))

(defn execute-startups
  [service-map]
  (let [startups (map i/-interceptor (get service-map ::startups []))]
    (chain/execute service-map startups)))

(defn start-service
  [service-map]
  (-> service-map
      execute-startups
      http/create-server
      http/start))
