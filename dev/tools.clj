(ns tools
  (:require [com.cognitect.vase.fern :as fern]
            [com.cognitect.vase :as vase]
            [fern.easy :as fe]
            [fern.printer :as fp]
            [datomic.api :as d]
            [edn-fern.convert :as c]))

(defn edn-spec->fern-spec
  [m]
  (assert (map? m) "edn-spec->fern-spec works on a map value")
  (reduce-kv c/convert {} m))

(defn edn-spec->fern-spec-str
  [m & {:as opts}]
  (fp/pprint-str
   (edn-spec->fern-spec m)
   (merge
    {:print-handlers fe/underef-handlers
     :map-delimiter  nil
     :width          100}
    opts)))
