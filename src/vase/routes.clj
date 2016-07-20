(ns vase.routes
  (:require [io.pedestal.http :as http]
            [io.pedestal.interceptor :as i]
            [io.pedestal.http.body-params :as body-params]
            [vase.datomic :as datomic]
            [vase.interceptor :as interceptor]
            [clojure.string :as string]))

(defn- keyword->dot-case
  [kw]
  (if (qualified-keyword? kw)
    (str (namespace kw)
         "." (name kw))
    (name kw)))

(defn- describe-api
  "Creates an interceptor that returns a list of all active routes.
  Optionally filter the list with the query param, `f`, which is a fuzzy match
  string value."
  [routes interceptor-ns]
  (let [kind :describe-api]
    (with-meta (i/interceptor
                {:name (keyword interceptor-ns (name kind))
                 :enter (fn [context]
                          (let [{:keys [f sep edn]
                                 :or {f "" sep "<br/>" edn false}} (get-in context [:request :query-params])
                                results (mapv #(take 2 %) routes)]
                            (assoc context :response
                                   (if edn
                                     (http/edn-response results)
                                     {:status 200
                                      :body   (string/join sep (map #(string/join " " %) results))}))))})
      {:kind kind})))

(defn- common-api-interceptors
  [interceptor-ns]
  [(interceptor/attach-received-time interceptor-ns)
   (interceptor/attach-request-id interceptor-ns)
   http/json-body])

(defn- app-interceptors
  [spec]
  (let [{:keys [descriptor activated-apis datomic-uri]} spec
        datomic-conn       (datomic/connect datomic-uri)
        headers-to-forward (get-in descriptor [:vase/apis activated-apis :vase.api/forward-headers] [])
        headers-to-forward (conj headers-to-forward interceptor/request-id-header)
        version-interceptors (mapv i/interceptor (get-in descriptor [:vase/apis activated-apis :vase.api/interceptors] []))
        interceptor-ns (keyword->dot-case activated-apis)
        base-interceptors (conj (common-api-interceptors interceptor-ns)
                                (datomic/insert-datomic datomic-conn)
                                (body-params/body-params (body-params/default-parser-map :edn-options {:readers *data-readers*}))
                                (interceptor/forward-headers headers-to-forward interceptor-ns))]
    (into base-interceptors
          version-interceptors)))

(defn- specified-routes
  [spec]
  (let [{:keys [activated-apis descriptor]} spec]
    (get-in descriptor [:vase/apis activated-apis :vase.api/routes])))

(defn- api-routes
  "Given a descriptor map, an app-name keyword, and a version keyword,
  return route vectors in Pedestal's tabular format. Routes will all be
  subordinated under `base`"
  [base spec make-interceptors-fn]
  (let [common (app-interceptors spec)]
    (for [[path verb-map] (specified-routes spec)
          [verb action]   verb-map
          :let [interceptors (if (vector? action)
                               (into common (map i/interceptor action))
                               (conj common (i/interceptor action)))]]
      (if (= path "/")
        [(str base) verb (make-interceptors-fn interceptors)]
        [(str base path) verb (make-interceptors-fn interceptors)]))))

(defn- api-base
  [api-root spec]
  (let [{:keys [activated-apis]} spec]
   (str api-root "/" (namespace activated-apis) "/" (name activated-apis))))

(defn- api-description-route-name
  [spec]
  (let [{:keys [activated-apis]} spec]
    (keyword (keyword->dot-case activated-apis)
             "describe")))

(defn api-description-route
  [api-root make-interceptors-fn routes route-name]
  (let [interceptor-ns (namespace route-name)]
    [api-root
     :get
     (make-interceptors-fn
      (conj (common-api-interceptors interceptor-ns) (describe-api routes interceptor-ns)))
     :route-name
     route-name]))

(defn spec-routes
  "Return a seq of route vectors from a single specification"
  [api-root make-interceptors-fn spec]
  (if (nil? (:activated-apis spec))
    []
    (let [app-version-root   (api-base api-root spec)
          app-version-routes (api-routes app-version-root spec make-interceptors-fn)
          app-api-route      (api-description-route app-version-root
                                                    make-interceptors-fn
                                                    app-version-routes
                                                    (api-description-route-name spec))]
      (cons app-api-route app-version-routes))))
