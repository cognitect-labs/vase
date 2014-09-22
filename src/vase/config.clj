(ns vase.config
  (:require [vase.util :as util]
            [io.pedestal.log :as log]))

(def path (System/getProperty "crocsconfig" nil))
(log/info :msg (str "Config is: " (or path "DEFAULT")))
(def config (if path
              (util/edn-file path)
              (util/edn-resource "system.edn")))
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

