(ns omnext-todo.service
  (:require [clojure.edn]
            [datomic.api :as d]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [com.cognitect.vase :as vase]
            [om.next.server :as om]
            [omnext-todo.demand :as demand]))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

(defn home-page
  [request]
  (ring-resp/response "Hello World!"))

(def demand-parser (om/parser {:read demand/demand-read
                               :mutate demand/demand-write}))

(defn params->query
  "Given a map of filter parameters (as from the query-string),
  Return a valid Datomic Datalog query that can be passed to `q`."
  ([params]
   (params->query params
                  '[:find ?todo
                    :in $
                    :where
                    [?todo :todo/title]]))
  ([params initial-query]
   (if-let [dlog-query (:raw-datalog params)]
     (clojure.edn/read-string dlog-query)
     (if (some? params)
       (cond-> initial-query
         (:completed params) (conj '[?todo :todo/completed true]))
       initial-query))))

(defn query-results
  "A read-only demand-like query interface for clients that can't speak 'Om'
  Allows a"
  ([request params]
   (d/q (params->query params) (:db request)))
  ([request params initial-query]
   (d/q (params->query params initial-query) (:db request))))

(defn handle-demand
  "Handle a demand-driven request.
  If the request has a parameter `:demand`, it is an Om.next query.
  Otherwise, handle the request with our limited 'demand-style' function, 'query-results'"
  [request]
  (let [demand-params (get-in request [:transit-params :demand]
                              (get-in request [:params :demand]))
        parsed-demand-params (if (string? demand-params)
                               (clojure.edn/read-string demand-params)
                               demand-params)]
    (if parsed-demand-params
      ;; We shouldn't need `trim` here, but we're defensively guarding against a `mutate` client call

      ;; NOTE: :db-ref is an atom that holds the result db value from demand mutations so
      ;; subsequent reads in a transaction containing a mutation can access the resulting db
      ;; (without the potential pitfalls of requesting a new db value from the datomic connection
      ;;  with other requests in flight)
      (let [env (assoc request :db-ref (atom nil))]
        (demand/post-process request (demand-parser env parsed-demand-params)))
      (set (query-results request (:params request))))))

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])

;; Tabular routes
(def routes #{["/" :get (conj common-interceptors `home-page)]
              ["/about" :get (conj common-interceptors `about-page)]
              ["/api/demand" :get (conj common-interceptors `handle-demand)]})

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
   ::vase/spec-resources ["omnext-todo_service.edn"]

   ;; Root for resource interceptor that is available by default.
   ::http/resource-path "/public"

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

