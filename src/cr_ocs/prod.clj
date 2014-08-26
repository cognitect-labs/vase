(ns cr-ocs.prod
  (:require [me.raynes.conch.low-level :as sh]
            [cr-ocs.config :refer [config]]))

;; Deployment
(defn run-datomic-transactor
  "Start a Datomic transactor in another process, returning a process map"
  []
  (let [transactor (sh/proc "./start-transactor.sh")]
    (println "Starting Datomic transactor")
    (-> (Thread. #(sh/stream-to-out transactor :out)) (.start))
    (.addShutdownHook (Runtime/getRuntime) (Thread. #((println "Shutting down Datomic transactor...")
                                                      (sh/destroy transactor))))))

(defn deploy-datomic []
  (run-datomic-transactor)
  (println "Waiting for transactor [15 seconds]...")
  (Thread/sleep 15000))

(defn -main [& args]
  (when (config :launch-transactor-on-start)
    (deploy-datomic))
  (require '[cr-ocs.server :as server])
  (apply (ns-resolve (find-ns (symbol "cr-ocs.server")) (symbol "-main")) args))

