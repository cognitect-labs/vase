(ns vasebi.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [com.cognitect.vase :as vase]
            [clojure.java.io :as io]))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))


(def pivot-response {:status 200
                     :headers {}
                     :body (slurp (io/resource "public/index.html"))})
(defn home-page
  [request]
  pivot-response)

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])

;; Tabular routes
(def routes #{["/" :get (conj common-interceptors `home-page)]
              ["/about" :get (conj common-interceptors `about-page)]})

(def service
  {:env :prod
   ;; You can bring your own non-default interceptors. Make
   ;; sure you include routing and set it up right for
   ;; dev-mode. If you do, many other keys for configuring
   ;; default interceptors will be ignored.
   ;; ::http/interceptors []

   ;; Uncomment next line to enable CORS support, add
   ;; string(s) specifying scheme, host and port for
   ;; allowed source(s):
   ;;
   ;; "http://localhost:8080"
   ;;
   ;;::http/allowed-origins ["scheme://host:port"]

   ::route-set routes
   ::vase/api-root "/api"
   ::vase/spec-resources ["vasebi_service.edn"]

   ;; Root for resource interceptor that is available by default.
   ::http/resource-path "/public"
   ;; We need to relax the secure headers a bit (specifically the CSP controls)
   ::http/secure-headers {:content-security-policy-settings {:object-src "none"}}

   ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
   ::http/type :jetty
   ;;::http/host "localhost"
   ::http/port 8080
   ;; Options to pass to the container (Jetty)
   ::http/container-options {:h2c? true
                             :h2? false
                             ;:keystore "test/hp/keystore.jks"
                             ;:key-password "password"
                             ;:ssl-port 8443
                             :ssl? false}})

