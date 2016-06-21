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

(def ^:private body-params
  (body-params/body-params
   (body-params/default-parser-map :edn-options {:readers *data-readers*})))

(defn- headers-to-forward
  [{:keys [vase.descriptor/forward-headers] :or {forward-headers []}}]
  (conj forward-headers interceptor/request-id-header))

(defn- app-interceptors
  [db-injector endpoint]
  (conj common-api-interceptors
        db-injector
        body-params
        (interceptor/forward-headers (headers-to-forward endpoint))))

(defn- describe-route-name [ident]
  (keyword (str (namespace ident) "-" (name ident)) "describe"))

(defn- api-description-route
  [path route-name make-interceptors-fn routes]
  [path :get (make-interceptors-fn (conj common-api-interceptors (describe-api routes))) :route-name route-name])

(defn- endpoint-description
  [endpoint-base ident make-interceptors-fn routes]
  (api-description-route endpoint-base
                         (describe-route-name ident)
                         make-interceptors-fn routes))

(defn- endpoint-routes
  [base db-injector make-interceptors-fn {:keys [vase.descriptor/routes vase.descriptor/ident] :as endpoint}]
  (let [endpoint-base     (str base "/" (subs (str ident) 1))
        common            (app-interceptors db-injector endpoint)
        routes            (for [[path verb-map] routes
                                [verb action]   verb-map
                                :let [intc (make-interceptors-fn (conj common (i/-interceptor action)))]]
                            [(str endpoint-base path) verb intc])
        description-route (endpoint-description endpoint-base ident make-interceptors-fn routes)]
    (cons description-route routes)))

(defn- api-routes
  "Given a descriptor map, an app-name keyword, and a version keyword,
   return route vectors in Pedestal's tabular format. Routes will all be
   subordinated under `base`"
  [base {:keys [vase.descriptor/endpoints] :as descriptor} make-interceptors-fn]
  (mapcat (partial endpoint-routes base (datomic/insert-datomic descriptor) make-interceptors-fn) endpoints))

(defn descriptor-routes
  "Return a seq of route vectors from a single specification"
  [api-root make-interceptors-fn descriptor]
  (let [routes            (api-routes api-root descriptor make-interceptors-fn)
        description-route (api-description-route api-root :describe-apis make-interceptors-fn routes)]
    (cons description-route routes)))
