(ns cr-ocs.util
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as cstr]
            [cheshire.core :as json])
  (:import (java.io ByteArrayInputStream
                    FileInputStream
                    File)
           (javax.xml.bind DatatypeConverter)))

(def ^:dynamic *deep-merge-fn* last)

(defn deep-merge
  "Merge any number of values. When `vals` are maps, this performs a recursive
  merge. When `vals` are not maps, `*deep-merge-fn*` is used to choose a
  winner.

  By default, `*deep-merge-fn*` is bound to `last`, which means the last value
  of `vals` will be chosen.

  You can specify your own merge strategy by binding `*deep-merge-fn*` to
  another function."
  [& vals]
  (if (every? map? (keep identity vals))
    (apply merge-with deep-merge vals)
    (*deep-merge-fn* vals)))

(defn fully-qualify-symbol
  [ns sym]
  (if-let [ns-alias? (namespace sym)]
    (let [ns-aliases (ns-aliases ns)]
      (if-let [fqns (ns-aliases (symbol ns-alias?))]
        (symbol (name (ns-name fqns)) (name sym))
        sym))
    sym))

(defn short-hash []
  (subs (DatatypeConverter/printBase64Binary (byte-array 8 (.byteValue ^Long (long (rand 100))))) 0 11))

(defn str->inputstream
  ([^String text]
   (str->inputstream text "UTF-8"))
  ([^String text ^String encoding]
   (ByteArrayInputStream. (.getBytes text encoding))))

(def write-edn pr-str)

(defn read-edn
  "Converts an edn string into Clojure data. `args` are clojure.edn `opts`
  `:readers` defaults to `*data-readers*`"
  [string & args]
  (let [e (edn/read-string (merge {:readers *data-readers*}
                                  (apply hash-map args))
                           string)]
    (if (instance? clojure.lang.IObj e)
      (with-meta e {:cr-ocs/src string})
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

;; Response Generation
;; -------------------
(defn merge-payloads
  "Does a deep merge of provided payloads."
  [& partial-payload-maps]
  (apply deep-merge (flatten partial-payload-maps)))

(defn base-body [maybe-map]
  (let [some-map (if (or (map? maybe-map)
                         (nil? maybe-map)) maybe-map {:payload maybe-map})]
    (if (some #{:response :errors :request} (keys some-map))
      (merge-payloads {:request {} :response {} :errors {}} some-map)
      {:request {} :response some-map :errors {}})))

(defn payload
  "Generates the body of a Ring response that's standard for API responses"
  ([req help-url]
   {:request {:body (get req :json-params
                         (get req :edn-params
                              (get req :body-string "")))
              :this_ (:uri req)
              :help help-url
              :server_received_time (str (:received-time req))}
    :response {}
    :errors {}})
  ([req help-url & partial-payload-maps]
   (merge-payloads (payload req help-url) (map base-body partial-payload-maps))))

(defn response
  "Converts a body into a valid Ring response e.g. sets status"
  [payload-map]
  (let [resp {:status 200 :headers {} :body payload-map}]
    (cond
      (and
        (seq (:response payload-map))
        (seq (:errors payload-map))) (assoc resp :status 205)
      (and
        (empty? (:response payload-map))
        (seq (:errors payload-map))) (assoc resp :status 400)
      :else resp)))

(defn error-response
  "Returns a Ring response with given status and a JSON error body with given error"
  [status error]
  {:pre [(< 399 status 600)]}
  {:status status
   :body (write-json {:errors [error]})
   :headers {"Content-Type" "application/json"}})

(defn throw-500!
  "Throws a clojure.lang.ExceptionInfo with a 500 error body to return to the end user.
  The exception also comes with :reason :five-hundred-response."
  ([] (throw-500! ""))
  ([err-body]
   (throw (ex-info "A fatal error occurred. Failing hard with a 500 response: " {:reason :five-hundred-response :body err-body}))))

(defn five-hundred-exception?
  "Returns true if an exception failed due to throw-500!."
  [exception]
  (= :five-hundred-response (:reason (ex-data exception))))

