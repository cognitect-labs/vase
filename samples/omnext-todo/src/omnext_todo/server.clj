(ns omnext-todo.server
  (:gen-class) ; for -main method in uberjar
  (:require [io.pedestal.http :as server]
            [io.pedestal.http.route :as route]
            [com.cognitect.vase :as vase]
            [omnext-todo.service :as service]))

(defn activate-vase
  ([base-routes api-root spec-paths]
   (activate-vase base-routes api-root spec-paths vase/load-edn-resource))
  ([base-routes api-root spec-paths vase-load-fn]
   (let [vase-specs (mapv vase-load-fn spec-paths)]
     (when (seq vase-specs)
       (vase/ensure-schema vase-specs)
       (vase/specs vase-specs))
     {::routes (if (empty? vase-specs)
                 base-routes
                 (into base-routes (vase/routes api-root vase-specs)))
      ::specs vase-specs})))

(defn vase-service
  "Optionally given a default service map and any number of string paths
  to Vase API Specifications,
  Return a Pedestal Service Map with all Vase APIs parsed, ensured, and activated."
  ([]
   (vase-service service/service))
  ([service-map]
   (vase-service service-map vase/load-edn-resource))
  ([service-map vase-load-fn]
   (merge {:env :prod
           ::server/routes (::routes (activate-vase
                                       (::service/route-set service-map)
                                       (::vase/api-root service-map)
                                       (::vase/spec-resources service-map)
                                       vase-load-fn))}
          service-map)))

;; This is an adapted service map, that can be started and stopped
;; From the REPL you can call server/start and server/stop on this service
(defonce runnable-service (server/create-server (vase-service)))

(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (println "\nCreating your [DEV] server...")
  (-> service/service ;; start with production configuration
      (merge {:env :dev
              ;; do not block thread that starts web server
              ::server/join? false
              ;; Routes can be a function that resolve routes,
              ;;  we can use this to set the routes to be reloadable
              ::server/routes #(route/expand-routes
                                 (::routes (activate-vase (deref #'service/routes)
                                                          (::vase/api-root service/service)
                                                          (mapv (fn [res-str]
                                                                  (str "resources/" res-str))
                                                                (::vase/spec-resources service/service))
                                                          vase/load-edn-file)))
              ;; all origins are allowed in dev mode
              ::server/allowed-origins {:creds true :allowed-origins (constantly true)}})
      ;; Wire up interceptor chains
      server/default-interceptors
      server/dev-interceptors
      server/create-server
      server/start))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (println "\nCreating your server...")
  (server/start runnable-service))

;; If you package the service up as a WAR,
;; some form of the following function sections is required (for io.pedestal.servlet.ClojureVarServlet).

;;(defonce servlet  (atom nil))
;;
;;(defn servlet-init
;;  [_ config]
;;  ;; Initialize your app here.
;;  (reset! servlet  (server/servlet-init service/service nil)))
;;
;;(defn servlet-service
;;  [_ request response]
;;  (server/servlet-service @servlet request response))
;;
;;(defn servlet-destroy
;;  [_]
;;  (server/servlet-destroy @servlet)
;;  (reset! servlet nil))

