(ns vase-component.main
  "Main entry-point for the service when deployed."
  (:gen-class)
  (:require
   [com.stuartsierra.component :as component]
   [vase-component.system :as system]
   [vase-component.endpoint :as endpoint]
   [io.pedestal.log :as log]))

(defn- env-port
  "Gets port from environment variable, or throws."
  []
  (if-let [s (System/getenv "PORT")]
    (Long. s)
    (throw (ex-info "Must set enviroment variable PORT" {}))))

(defn -main
  "Command-line main entry point."
  [& args]
  (let [port   (env-port)
        system (-> (system/system)
                   (update :endpoint endpoint/with-port port)
                   component/start)]
    (log/info :msg "System started" :port port)
    (println "Service started at port" port)
    (endpoint/join (:endpoint system))))
