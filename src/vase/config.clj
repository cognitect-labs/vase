(ns vase.config
  (:require [vase.util :as util]
            [io.pedestal.log :as log]))

(def path (System/getProperty "crocsconfig" nil))
(log/info :msg (str "Config is: " (or path "DEFAULT")))

;; The purpose of this fn is to hide the implementation details of config reading.
;; This allows changing the config system painlessly (Perhaps to Confil or something new)
(defn read-config
  ([] (read-config nil))
  ([path]
   (if path
     (util/edn-file path)
     (util/edn-resource "system.edn"))))

;; `config` should always be map-like
(def config (read-config path))
(log/info :msg (str "Descriptor is: " (config :initial-descriptor)))

(defn get-key
  ([k]
   (get-key k (str "Config does not contain key: " k)))
  ([k msg]
   (let [sentinel (ex-info msg {:config config})
         result (get config k sentinel)]
     (if (= result sentinel)
       (throw sentinel)
       result))))

