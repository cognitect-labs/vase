(ns vase.service
  (:require [clojure.string :as cstr]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.log :as log]
            [ring.util.response :as ring-resp]
            [vase.interceptor :as interceptor]
            [vase.config :refer [config]]
            [vase]))

(defn clj-ver
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::clj-ver))))

(defn health-check
  [request]
  (ring-resp/response "alive"))

(def routes (atom nil))

(def master-routes `["/" {:get health-check} ^:interceptors [interceptor/attach-received-time
                                                             interceptor/attach-request-id
                                                             ;; In the future, html-body should be json-body
                                                             bootstrap/html-body
                                                             (interceptor/bind-routes routes)]
                     ["/about" {:get clj-ver}]
                     ^:vase/api-root ["/api" {:get vase/show-routes}
                                        ^:interceptors [bootstrap/json-body
                                                        interceptor/vase-error-ring-response]]])

(vase/init-descriptor-routes! :master-routes master-routes :routes-atom routes)

;; Consumed by vase.server/create-server
;; See bootstrap/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; :bootstrap/interceptors []
              ::bootstrap/routes (if (config :enable-upsert) #(deref routes) @routes)

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::bootstrap/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty or :tomcat (see comments in project.clj
              ;; to enable Tomcat)
              ;;::bootstrap/host "localhost"
              ::bootstrap/type :jetty
              ::bootstrap/port (config :service-port)})

