(ns vase.util
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as cstr]
            [cheshire.core :as json]
            [cognitect.transit :as transit])
  (:import (java.io ByteArrayInputStream
                    FileInputStream
                    File)
           (javax.xml.bind DatatypeConverter)))

(defn map-vals
  [f m]
  (reduce-kv (fn [m k v] (assoc m k (f v))) m m))

(defn fully-qualify-symbol
  ([sym] (fully-qualify-symbol *ns* sym))
  ([ns sym]
     (if-let [ns-alias? (namespace sym)]
       (let [ns-aliases (ns-aliases ns)]
         (if-let [fqns (ns-aliases (symbol ns-alias?))]
           (symbol (name (ns-name fqns)) (name sym))
           sym))
       sym)))

(defn short-hash []
  (subs
    (DatatypeConverter/printBase64Binary
      (byte-array (loop [i 0
                         ret (transient [])]
                    (if (< i 8)
                      (recur (inc i) (conj! ret (.byteValue ^Long (long (rand 100)))))
                      (persistent! ret)))))
    0 11))

(defn str->inputstream
  ([^String text]
   (str->inputstream text "UTF-8"))
  ([^String text ^String encoding]
   (ByteArrayInputStream. (.getBytes text encoding))))

(defn re-findall
  ([matcher]
   (loop [res (transient [])]
     (if-let [found (re-find matcher)]
       (recur (conj res found))
       (persistent! res))))
  ([regex s]
   (re-findall (re-matcher regex s))))

(def write-edn pr-str)

(defn read-edn
  "Converts an edn string into Clojure data. `args` are clojure.edn `opts`
  `:readers` defaults to `*data-readers*`"
  [string & args]
  (let [e (edn/read-string (merge {:readers *data-readers*}
                                  (apply hash-map args))
                           string)]
    (if (instance? clojure.lang.IObj e)
      (with-meta e {:vase/src string})
      e)))

(defn edn-resource
  "Load an EDN resource file and read its contents. The only required argument
  is `file-path`, which is the path of a file relative the projects resources
  directory (`resources/` or, for tests, `test/resources/`).

  Optional arguments:

  * `fallback-path` - A \"default\" path to check if file-path is actually an
    empty string. Useful in places you load a `file-path` from a config and its
    value might be absent.
  * `process-path-fn` - The function to use for getting the URL of the file. By
    default uses `clojure.java.io/resource`."
  ([file-path]
   (edn-resource file-path "" io/resource))
  ([file-path fallback-path]
   (edn-resource file-path fallback-path io/resource))
  ([file-path fallback-path process-path-fn]
   (let [trimmed-path (or (not-empty (cstr/trim file-path))
                          (not-empty (cstr/trim fallback-path)))
         contents (some->>
                    trimmed-path
                    process-path-fn
                    slurp)]
     (if contents
       (read-edn contents)
       (throw (ex-info
                (str "Failed to read an EDN file: " file-path " :: trimmed to: " trimmed-path)
                {:file-path file-path
                 :trimmed-path trimmed-path}))))))

(defn edn-file
  [file-path]
  (edn-resource file-path "" (fn [^String x] (io/as-url (File. x)))))

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

;; Response Generation
;; -------------------
(defn- complete-with-errors?
  [response errors]
  (and (seq response) (seq errors)))

(defn- bad-request?
  [response errors]
  (and (nil? (seq response)) (seq errors)))

(defn- exception?
  [response]
  (instance? Throwable response))

(defn status-code
  [response errors]
  (cond
    (complete-with-errors? response errors) 205
    (bad-request?          response errors) 400
    (exception?            response)        500
    :else                                   200))
