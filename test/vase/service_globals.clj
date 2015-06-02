(ns vase.service-globals
  (:import [java.util UUID])
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.log :as log]
            [ring.util.response :as ring-resp]
            [vase.interceptor :as interceptor]
            [vase]
            ;[vase.util :as util]
            [vase.config :as conf]))

(def config (conf/default-config))
(def vase-context (atom (vase/map->Context {:config config})))

(defn clj-ver
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::clj-ver))))
(defn health-check
  [request]
  (ring-resp/response "alive"))

(def master-routes
  `["/" {:get health-check} ^:interceptors [interceptor/attach-received-time
                                            interceptor/attach-request-id
                                            ;; In the future, html-body should be json-body
                                            http/html-body
                                            (interceptor/bind-vase-context vase-context)]
    ["/about" {:get clj-ver}]
    ^:vase/api-root ["/api" {:get vase/show-routes}
                     ^:interceptors [http/json-body
                                     interceptor/vase-error-ring-response]]])

;; Initialize Vase on Service Load
(vase/bootstrap-vase-context! vase-context master-routes)

(def service-map
  {:env :prod
   ;:vase/context vase-context ;; For testing, shouldn't need this otherwise
   ::http/routes (if (config :enable-upsert) #(:routes @vase-context) (:routes @vase-context))
   ::http/resource-path "/public"
   ::http/type :jetty
   ::http/port (conf/get-key config :service-port)})

