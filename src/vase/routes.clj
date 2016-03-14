(ns vase.routes
  (:require [io.pedestal.http :as http]
            [io.pedestal.interceptor :as i]
            [io.pedestal.http.body-params :as body-params]
            [vase.datomic :as datomic]
            [vase.interceptor :as interceptor]
            [clojure.string :as str]))

(defn- describe-api
  "Return a list of all active routes.
  Optionally filter the list with the query param, `f`, which is a fuzzy match
  string value"
  [routes]
  (i/-interceptor
   {:enter (fn [context]
             (let [{:keys [f sep edn] :or {f "" sep "<br/>" edn false}} (-> context :request :query-params)
                   results                                              (mapv #(take 2 %) routes)]
               (assoc context :response
                      (if edn
                        (http/edn-response results)
                        {:status 200
                         :body   (str/join sep (map #(str/join " " %) results))}))))}))

(def ^:private common-api-interceptors
  [interceptor/attach-received-time
   interceptor/attach-request-id
   http/json-body])

(defn- app-interceptors
  [{:keys [descriptor app-name version datomic-uri]}]
  (let [datomic-conn       (datomic/connect datomic-uri)
        headers-to-forward (get-in descriptor [app-name version :forward-headers] [])
        headers-to-forward (conj headers-to-forward interceptor/request-id-header)]
    (conj common-api-interceptors
          (datomic/insert-datomic datomic-conn)
          (body-params/body-params (body-params/default-parser-map :edn-options {:readers *data-readers*}))
          (interceptor/forward-headers headers-to-forward))))

(defn- specified-routes
  [{:keys [descriptor app-name version]}]
  (get-in descriptor [app-name version :routes]))

(defn- api-routes
  "Given a descriptor map, an app-name keyword, and a version keyword,
   return route vectors in Pedestal's tabular format. Routes will all be
   subordinated under `base`"
  [base spec make-interceptors-fn]
  (let [common (app-interceptors spec)]
    (for [[path verb-map] (specified-routes spec)
          [verb action]   verb-map]
      [(str base path) verb (make-interceptors-fn (conj common (i/-interceptor action)))])))

(defn- api-base
  [base {:keys [app-name version]}]
  (str base "/" (name app-name) "/" (name version)))

(defn- api-description-route-name
  [{:keys [app-name version]}]
  (keyword (str (name app-name) "-" (name version)) "describe"))

(defn api-description-route
  [api-root make-interceptors-fn routes route-name]
  [api-root :get (make-interceptors-fn (conj common-api-interceptors (describe-api routes))) :route-name route-name])

(defn spec-routes
  "Return a seq of route vectors from a single specification"
  [api-root make-interceptors-fn spec]
  (let [app-version-root   (api-base api-root spec)
        app-version-routes (api-routes app-version-root spec make-interceptors-fn)
        app-api-route      (api-description-route app-version-root make-interceptors-fn app-version-routes (api-description-route-name spec))]
    (cons app-api-route app-version-routes)))
