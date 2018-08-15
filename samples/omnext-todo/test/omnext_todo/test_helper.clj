(ns omnext-todo.test-helper
  (:require [io.pedestal.test :refer [response-for]]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.log :as log]
            [com.cognitect.vase :as vase]
            [com.cognitect.vase.util :as util]
            [com.cognitect.vase.datomic :as datomic]
            [omnext-todo.server :as server]
            [omnext-todo.service]))

(def write-edn pr-str)

(defn new-service
  "This generates a new testable service for use with io.pedestal.test/response-for.
  It will also create a new Datomic DB (randomized URI)."
  ([]            (new-service omnext-todo.service/service))
  ([service-map]
   (let [db-table (volatile! {})
         vase-service-map (server/vase-service
                            service-map
                            (fn [spec-path]
                              (let [spec (vase/load-edn-resource spec-path)
                                    prod-db-uri (:datomic-uri spec)
                                    new-db-uri (when-let [db-uri (and prod-db-uri (get @db-table prod-db-uri (datomic/new-db-uri)))]
                                                 (vswap! db-table assoc prod-db-uri db-uri)
                                                 db-uri)]
                                (if prod-db-uri
                                  (assoc spec :datomic-uri new-db-uri)
                                  spec))))]
     (::http/service-fn (http/create-servlet vase-service-map)))))

(def ^:dynamic *current-service* nil)

(defmacro with-service
  "Executes all requests in the body with the same service (using a thread-local binding)"
  [srv-map & body]
  `(binding [*current-service* (new-service ~srv-map)]
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

(defn DELETE
  "Make a DELETE request on our service using response-for."
  [& args]
  (apply response-for (service) :delete args))

(defn json-request
  ([verb url payload]
   (json-request verb url payload {}))
  ([verb url payload opts]
   (response-for (service)
                 verb url
                 :headers (merge {"Content-Type" "application/json"}
                                 (:headers opts))
                 :body (util/write-json payload))))

(defn post-json
  "Makes a POST request to URL-path expecting a payload to submit as JSON.

  Options:
  * :headers: Additional headers to send with the request."
  ([URL-path payload]
   (post-json URL-path payload {}))
  ([URL-path payload opts]
   (json-request :post URL-path payload opts)))

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
                 :body (write-edn payload))))

(defn response-data
  "Return the parsed payload data from a vase api http response."
  ([response] (response-data response util/read-json))
  ([response reader]
   (-> response
       :body
       reader)))

(defn run-interceptor
  ([i]     (run-interceptor {} i))
  ([ctx i] (chain/execute (chain/enqueue* ctx i))))

(defn new-req-ctx
  [& {:as headers}]
  {:request {:headers headers}})
