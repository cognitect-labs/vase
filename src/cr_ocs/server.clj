(ns cr-ocs.server
  (:gen-class) ; for -main method in uberjar
  (:require [io.pedestal.http :as server]
            [cr-ocs.service :as service]))

(defonce runnable-service (server/create-server service/service))

(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (println "\nCreating your [DEV] server...")
  (server/start (-> runnable-service
                     ;; Wire up interceptor chains
                     (server/dev-interceptors))))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  ;; Bash the Datomic transactor into place before we move on
  (println "\nCreating your [PROD] server...")
  (server/start runnable-service))