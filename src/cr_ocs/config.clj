(ns cr-ocs.config
  (:require [cr-ocs.util :as util]))

(def path (System/getProperty "crocsconfig" nil))
(println "Config is:" (or path "DEFAULT"))
(def config (if path
              (util/edn-file path)
              (util/edn-resource "system.edn")))
(println "Descriptor is:" (config :initial-descriptor))

