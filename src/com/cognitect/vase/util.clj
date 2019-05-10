(ns com.cognitect.vase.util
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as cstr]
            [cheshire.core :as json]
            [cognitect.transit :as transit])
  (:import (java.io ByteArrayInputStream
                    FileInputStream
                    File)
           (java.util Base64)))

(defn map-vals
  [f m]
  (reduce-kv (fn [m k v] (assoc m k (f v))) m m))

(defn str->inputstream
  ([^String text]
   (str->inputstream text "UTF-8"))
  ([^String text ^String encoding]
   (ByteArrayInputStream. (.getBytes text encoding))))

(defn short-hash []
  (subs
   (.encodeToString (Base64/getEncoder)
                    (byte-array (loop [i   0
                                       ret (transient [])]
                                  (if (< i 8)
                                    (recur (inc i) (conj! ret (.byteValue ^Long (long (rand 100)))))
                                    (persistent! ret)))))
   0 11))

;; This function is useful when writing your own action literals,
;; allowing you to expand symbol names within the descriptors.
;; It's not used within the Vase source, but has been used on projects
;; built with Vase.
(defn fully-qualify-symbol
  ([sym] (fully-qualify-symbol *ns* sym))
  ([ns sym]
     (if-let [ns-alias? (namespace sym)]
       (let [ns-aliases (ns-aliases ns)]
         (if-let [fqns (ns-aliases (symbol ns-alias?))]
           (symbol (name (ns-name fqns)) (name sym))
           sym))
       sym)))

(defn ensure-keyword [x]
    (cond
      (keyword? x) x
      (string? x) (keyword x)
      (symbol? x) (keyword (namespace x) (name x))
      :else (keyword (str x))))









(defn read-json
  "Converts json string to Clojure data. By default, keys are keywordized."
  [string & args]
  (apply json/parse-string string keyword args))

(defn write-json
  "Writes json string given Clojure data. By default, unicode is not escaped."
  [data & args]
  (json/generate-string data (apply hash-map args)))

(defn read-transit-json
  [transit-json-str]
  (-> transit-json-str
      str->inputstream
      (transit/reader :json)
      transit/read))
