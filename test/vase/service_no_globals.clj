(ns vase.service-no-globals
  (:import [java.util UUID])
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.log :as log]
            [ring.util.response :as ring-resp]
            [vase.interceptor :as interceptor]
            [vase]
            ;[vase.util :as util]
            [vase.config :as conf]))

(defn unique-config
     "Returns a unique Vase config map"
     []
     {:db-uri (str "datomic:mem://" (UUID/randomUUID))
      :service-port 8080
      :http-app-root "/"
      :enable-upsert true
      :http-upsert true
      :transact-upsert true
      :initial-descriptor "test_descriptor.edn"
      :initial-version [:example :v1]
      :test-trials 36})

(defn clj-ver
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::clj-ver))))
(defn health-check
  [request]
  (ring-resp/response "alive"))


(defn make-master-routes
  [vase-context-atom]
  `["/" {:get health-check} ^:interceptors [interceptor/attach-received-time
                                            interceptor/attach-request-id
                                            ;; In the future, html-body should be json-body
                                            http/html-body
                                            ~(interceptor/bind-vase-context vase-context-atom)]
    ["/about" {:get clj-ver}]
    ^:vase/api-root ["/api" {:get vase/show-routes}
                     ^:interceptors [http/json-body
                                     interceptor/vase-error-ring-response]]])

(defn service-map
  "Return a new, fully initialized service map"
  []
  (let [config (unique-config)
        vase-context (atom (vase/map->Context {:config config}))]
    (vase/bootstrap-vase-context! vase-context (make-master-routes vase-context))
    {:env :prod
     :vase/context vase-context ;; For testing, shouldn't need this otherwise
     ::http/routes (if (config :enable-upsert) #(:routes @vase-context) (:routes @vase-context))
     ::http/resource-path "/public"
     ::http/type :jetty
     ::http/port (conf/get-key config :service-port)}))

