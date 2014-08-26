(ns cr-ocs.test-helper
  (:require [io.pedestal.test :refer [response-for]]
            [io.pedestal.http :as bootstrap]
            [cr-ocs.service :as cserv]
            [cr-ocs.util :as util]))

(def service-map cserv/service)

(defn service
  "This generates a testable service for use with io.pedestal.test/response-for."
  ([]
   (service service-map))
  ([serv-map]
   (::bootstrap/service-fn (bootstrap/create-servlet serv-map))))

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

