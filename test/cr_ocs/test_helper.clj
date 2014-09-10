(ns cr-ocs.test-helper
  (:require [io.pedestal.test :refer [response-for]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.log :as log]
            [cr-ocs.service :as cserv]
            [cr-ocs]
            [cr-ocs.util :as util]
            [cr-ocs.db :as cdb]
            [cr-ocs.config :refer [config]]
            [datomic.api :as d]))

(def base-service-map cserv/service)

(defn test-with-fresh-db
  [f]
  (let [uri cdb/uri
        delete-result (d/delete-database uri)
        create-result (d/create-database uri)]
    (assert (and delete-result create-result))
    (reset! cdb/conn (cdb/conn-database uri))
    (f)))

(defn refresh-service-map
  ([]
   (refresh-service-map base-service-map))
  ([serv-map]
   (let [routes-atom (cr-ocs/init-descriptor-routes!
                       :master-routes cserv/master-routes)]
     (assoc serv-map
            :io.pedestal.http/routes
              ;(if (config :enable-upsert) #(deref routes-atom) @routes-atom)
            #(deref routes-atom)
            :routes-atom routes-atom))))

(defn service-fn [serv-map]
  (::bootstrap/service-fn (bootstrap/create-servlet (dissoc serv-map :routes-atom))))

(defn service
  "This generates a testable service for use with io.pedestal.test/response-for."
  ([]
     (service base-service-map))
  ([serv-map]
   (let [new-map (refresh-service-map serv-map)]
     (service-fn new-map))))

;; This is only to make ad-hoc/repl testing easier.  Servlets are not immutable
;; and should be generated per request to prevent any test pollution
(def testable-service (service))

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
  "Return the parsed payload data from a cr-ocs api http response."
  ([response] (response-data response util/read-json))
  ([response reader]
     (-> response
         :body
         reader
         :response)))

