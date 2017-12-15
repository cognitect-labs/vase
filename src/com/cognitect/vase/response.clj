(ns com.cognitect.vase.response)

(defn- complete-with-errors?
  [response errors]
  (and (not (nil? response)) (seq errors)))

(defn- bad-request?
  [response errors]
  (and (nil? response) (seq errors)))

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

(defn response
  [body headers status]
  {:body    (or body "")
   :headers (or headers {})
   :status  (or status 200)})
