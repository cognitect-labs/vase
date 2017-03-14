(ns vase-component.endpoint
  "Pedestal server and HTTP request handling"
  (:require
   [com.stuartsierra.component :as component]
   [io.pedestal.http :as http]
   [io.pedestal.http.route :as http.route]
   [io.pedestal.log :as log]
   [com.cognitect.vase :as vase]))

;;; Response helpers

(defn ok
  [s]
  {:status 200
   :body s})

(defn bad-request
  [s]
  {:status 400
   :body s})

(defn with-headers
  [r h]
  (assoc r :headers h))

;;; Health check route

(defn healthcheck
  "Pedestal response handler: simple liveness check."
  [_]
  (ok "OK"))

;;; Pedestal routes and service component

(defn routes [& contribs]
  (http.route/expand-routes
   (reduce into #{["/healthcheck" :get `healthcheck]} (map :routes contribs))))

(defrecord HTTPEndpoint [service api]
  component/Lifecycle
  (start [this]
    (log/info :message "Starting HTTP endpoint" :port (::http/port service) :apis (:activated-apis api))
    (assoc this :service
           (-> service
               ;; TODO - make ::http/routes a val instead of a fn in prod.
               (assoc ::http/routes #(routes api))
               http/default-interceptors
               (cond-> (::dev? this) http/dev-interceptors)
               http/create-server
               http/start)))

  (stop [this]
    (when service
      (log/info :message "Stopping HTTP endpoint" :port (::http/port service))
      (try (http/stop service)
           (catch Throwable t
             (log/error :message "Error in Pedestal stop" :exception t))))
    (assoc this :service nil)))

(defn service-map
  "Returns initial service map for the Pedestal server."
  []
  {::http/type   :jetty
   ::http/port   8000
   ::http/join?  false})

(defn http-endpoint
  "Returns the Pedestal server component."
  []
  (map->HTTPEndpoint {:service (service-map)}))

(defn with-port
  "Updates http-endpoint component to bind to port. Must be called
  before start."
  [component port]
  (assoc-in component [:service ::http/port] port))

(defn with-automatic-port
  "Updates http-endpoint to bind to a random free port. Must be called
  before start."
  [component]
  (with-port component 0))

(defn port
  "Returns bound port of the (started) http-endpoint component."
  [component]
  (some-> component :service ::http/server
          .getConnectors (aget 0) .getLocalPort))

(defn dev-mode
  "Adds development-mode interceptors to http-endpoint component. Must
  be called before start."
  [component]
  (assoc component ::dev? true))

(defn join
  "Joins the server thread, blocking the current thread."
  [component]
  (.join ^org.eclipse.jetty.server.Server
         (get-in component [:service ::http/server])))
