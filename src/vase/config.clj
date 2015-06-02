(ns vase.config
  (:require [vase.util :as util]
            [io.pedestal.log :as log]))


(defn default-config
  []
  (if-let [path (System/getProperty "vaseconfig" nil)]
    (util/edn-file path)
    (util/edn-resource "system.edn")))

(defn get-key
  ([config k]
   (get-key config k (str "Config does not contain key: " k)))
  ([config k msg]
   (let [sentinel (ex-info msg {:config config})
         result (get config k sentinel)]
     (if (= result sentinel)
       (throw sentinel)
       result))))

