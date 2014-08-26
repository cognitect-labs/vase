(ns immutant.init
  (:require [immutant.web :as web]
            [io.pedestal.log :as log]
            [io.pedestal.http :as http]
            [cr-ocs.service :as service]
            [cr-ocs.config :refer [config]]))


(defn web-init []
  (web/start-servlet (config :http-app-root)
                     (::http/servlet (http/create-servlet service/service))))


(defn start []
  ;; Start the web server
  (try
    (web-init)
    (catch Exception e
      (log/error :exeption e
                 :msg "Web failed to start; Check Pedestal service setup")))
  ;; Start Datomic
  ;(try
  ;  (db/init)
  ;  (catch Exception e
  ;    (log/info :msg (ex-data e))
  ;    (when-let [db-error (-> e ex-data :db/error)]
  ;      (log/info :msg "Waiting 15 seconds for the transactor to come up")
  ;      (Thread/sleep 15000)
  ;      (db/init))
  ;    (log/error :exeption e
  ;               :msg "Check transactor failings or version conflicts")))
  )

