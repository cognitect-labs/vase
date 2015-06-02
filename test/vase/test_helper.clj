(ns vase.test-helper
  (:import [java.util UUID])
  (:require [io.pedestal.test :refer [response-for]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.log :as log]
            [vase.interceptor :as interceptor]
            [vase.service-no-globals :as vserv]
            [vase]
            [vase.util :as util]
            [vase.config :as conf]
            [datomic.api :as d]))

(defn new-service
  "This generates a new testable service for use with io.pedestal.test/response-for."
  ([] (new-service (vserv/service-map)))
  ([service-map] (::bootstrap/service-fn (bootstrap/create-servlet service-map))))

(def ^:dynamic *current-service* nil)

(defmacro with-service
  "Executes all requests in the body with the same service (using a thread-local binding)"
  [& body]
  `(binding [*current-service* (new-service)]
     ~@body))

(defn service
  [& args]
  (or *current-service* (apply new-service args)))

(defn GET
  "Make a GET request on our service using response-for."
  [& args]
  (apply response-for (service) :get args))

(defn POST
  "Make a POST request on our service using response-for."
  [& args]
  (apply response-for (service) :post args))

(defn post-json
  "Makes a POST request to URL-path expecting a payload to submit as JSON.

  Options:
  * :headers: Additional headers to send with the request."
  ([URL-path payload]
   (post-json URL-path payload {}))
  ([URL-path payload opts]
   (response-for (service)
                 :post URL-path
                 :headers (merge {"Content-Type" "application/json"}
                                 (:headers opts))
                 :body (util/write-json payload))))

(defn post-edn
  "Makes a POST request to URL-path expecting a payload to submit as edn.

  Options:
  * :headers: Additional headers to send with the request."
  ([URL-path payload]
   (post-edn URL-path payload {}))
  ([URL-path payload opts]
   (response-for (service)
                 :post URL-path
                 :headers (merge {"Content-Type" "application/edn"}
                                 (:headers opts))
                 :body (util/write-edn payload))))

(defn response-data
  "Return the parsed payload data from a vase api http response."
  ([response] (response-data response util/read-json))
  ([response reader]
     (-> response
         :body
         reader
         :response)))

