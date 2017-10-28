(ns petstore-full.server
  (:gen-class) ; for -main method in uberjar
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [petstore-full.service :as service]))

(defonce runnable-service (http/create-server (service/service)))

(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (println "\nCreating your [DEV] server...")
  (-> (service/service) ;; start with production configuration
      (merge {:env                     :dev
              ::http/join?           false
              ::http/allowed-origins {:creds true :allowed-origins (constantly true)}})
      ;; Wire up interceptor chains
      http/default-interceptors
      http/dev-interceptors
      http/create-server
      http/start))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (println "\nCreating your server...")
  (http/start runnable-service))
