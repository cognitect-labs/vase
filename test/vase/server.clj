(ns vase.server
  (:gen-class) ; for -main method in uberjar
  (:require [io.pedestal.http :as server]
            [vase.service-no-globals :as ngservice]
            [vase.service-globals :as service]))

(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (println "\nCreating your [DEV] server...")
  ;; The globals way
  (server/start (-> service/service-map
                    (server/create-server)
                    server/dev-interceptors))
  ;; The no-globals way
  #_(server/start (-> (ngservice/service-map)
                    server/create-server
                    ;; Wire up interceptor chains
                    server/dev-interceptors)))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  ;; Bash the Datomic transactor into place before we move on
  (println "\nCreating your [PROD] server...")
  ;;globals way
  (server/start (server/create-server service/service-map))
  ;; no globals way
  #_(server/start (server/create-server (ngservice/service-map))))

