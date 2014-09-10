(ns cr-ocs.interceptor
  (:require [io.pedestal.interceptor :as interceptor :refer [defon-request definterceptor definterceptorfn]]
            [io.pedestal.log :as log]
            [clojure.stacktrace :as ctrace]
            [clj-time.core :as clj-time]
            [cr-ocs.util :as util]))
;(:remote-addr :scheme :servlet-path :path-params :io.pedestal.http.impl.servlet-interceptor/protocol :servlet :request-method
; :query-string :content-type :edn-params :path-info :uri :url-for :context-path :server-name :headers :servlet-request :content-length :received-time :server-port :character-encoding
; :servlet-response :io.pedestal.http.impl.servlet-interceptor/async-supported? :body :servlet-context :request-id)

(defon-request attach-received-time
  "Attaches a timestamp to every request."
  [req]
  (assoc req :received-time (clj-time/now)))

(defon-request attach-request-id
  "Attaches a request ID to every request;
  If there's a 'request_id' header, it will use that value, otherwise it will
  generate a short hash"
  [req]
  (let [req-id (get-in req [:headers "crrequest-id"] (util/short-hash))]
    (-> req
        (assoc :request-id req-id)
        ;; just in case someone goes looking in the headers...
        ;; This is also a special case for "forward-headers"
        (assoc-in [:headers "crrequest-id"] req-id))))

(defn forward-headers-interceptor
  "Given an interceptor name and list of headers to forward,
  return an interceptor that attaches those headers to reponses
  IFF they are in the request"
  [kw-name headers]
  (interceptor/interceptor
    :name kw-name
    :leave (fn [context]
             (assoc-in context [:response :headers]
                       (merge (select-keys (get-in context [:request :headers]) headers)
                              (get-in context [:response :headers]))))))

;; Correctly handle 500s and wrap them as JSON responses
(defn- five-hundred-response [exception]
  (log/error :msg "A fatal error occurred - most likely an error from Datomic or malformed upsert. Failing hard with a 500 response: "
             :body (:body (ex-data exception)))
  (util/error-response 500 (:body (ex-data exception))))

(defn- response-for-exception [exception]
  (if (util/five-hundred-exception? exception)
    (five-hundred-response exception)
    (if (and (instance? java.util.concurrent.ExecutionException exception)
             (util/five-hundred-exception? (ctrace/root-cause exception)))
      (do
        (log/info :msg (str "Unwrapping original exception from this concurrent exception - " exception))
        (five-hundred-response (ctrace/root-cause exception)))
      (do
        (log/error
          :msg "Unexpected error within a pedestal route/handling."
          :exception exception
          :exception-data (ex-data exception))
        (util/error-response 500 "Internal server error: exception")))))

(definterceptor json-error-ring-response
  "Returns a 500 JSON response for unexpected exceptions in endpoints."
  (interceptor/interceptor
    :name ::json-error-ring-response
    :error (fn [{:keys [servlet-response] :as context} exception]
             (assoc context
                    :response (response-for-exception exception)))))

(definterceptorfn bind-routes
  ""
  [routes-atom]
  (interceptor/interceptor
    :name ::bind-routes
    :enter (fn [context]
             (assoc-in context [:request :routes-atom] routes-atom))))

(defn conditional-handlers
  "Gvien a keyword name and any variable predicate and handler function pairs,
  return an interceptor that will apply the handler paired to the first truthy
  predicate.  Predicates are given the full context.  Handlers are given the
  request and expect to return the response.
  If all predicates fall, the original context is returned."
  [name-kw & pred-hands]
  {:pre [(even? (count pred-hands))]}
  (let [pred-hand-pairs (partition 2 pred-hands)]
    (interceptor/interceptor
      :name name-kw
      :enter (fn [context]
               (or
                 (first
                   (keep (fn [[pred handler]] (when (pred context)
                                             (assoc context :response
                                                       (handler (:request context)))))
                         pred-hand-pairs))
                 context)))))

(defn conditional-context
  "Given a keyword name and any variable predicate and terminator function pairs,
  return an interceptor that will apply the terminator function paired to the first
  truthy predicate.  Predicates and terminators are both given the context as
  the only argument.
  If all predicates fail, the original context is returned."
  [name-kw & pred-terms]
  {:pre [(even? (count pred-terms))]}
  (let [pred-term-pairs (partition 2 pred-terms)]
    (interceptor/interceptor
      :name name-kw
      :enter (fn [context]
               (or
                 (first
                   (keep (fn [[pred term]] (when (pred context)
                                             (term context)))
                         pred-term-pairs))
                 context)))))

