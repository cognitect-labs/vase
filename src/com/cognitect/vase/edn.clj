(ns com.cognitect.vase.edn
  (:refer-clojure :exclude [read])
  (:require   [clojure.edn :as edn]
              [clojure.java.io :as io]
              [clojure.string :as cstr])
  (:import (java.io File)))

(defn read
  "Converts an edn string into Clojure data. `args` are clojure.edn `opts`
  `:readers` defaults to `*data-readers*`"
  [string & args]
  (let [e (edn/read-string (merge {:readers *data-readers*}
                                  (apply hash-map args))
                           string)]
    (if (instance? clojure.lang.IObj e)
      (with-meta e {:vase/src string})
      e)))

(defn from-resource
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
   (from-resource file-path "" io/resource))
  ([file-path fallback-path]
   (from-resource file-path fallback-path io/resource))
  ([file-path fallback-path process-path-fn]
   (let [trimmed-path (or (not-empty (cstr/trim file-path))
                          (not-empty (cstr/trim fallback-path)))
         contents (some->>
                    trimmed-path
                    process-path-fn
                    slurp)]
     (if contents
       (read contents)
       (throw (ex-info
                (str "Failed to read an EDN file: " file-path " :: trimmed to: " trimmed-path)
                {:file-path file-path
                 :trimmed-path trimmed-path}))))))

(defn from-file
  [file-path]
  (from-resource file-path "" (fn [^String x] (io/as-url (File. x)))))
