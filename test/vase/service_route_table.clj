(ns vase.service-route-table
  (:import [java.util UUID])
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition.table :as table]
            [vase]))

(defn make-master-routes
  [spec]
  (table/table-routes
   {}
   (vase/routes "/api" spec)))

(defn service-map
  "Return a new, fully initialized service map"
  []
  (let [test-db    (str "datomic:mem://" (UUID/randomUUID))
        descriptor (vase/load-descriptor "test_descriptor.edn")
        conn       (vase.datomic/connect test-db)]
    (vase.datomic/ensure-schema conn (-> descriptor :example :norms))
    {:env                 :prod
     ::http/routes        (make-master-routes {:descriptor  descriptor
                                               :app-name    :example
                                               :version     :v1
                                               :datomic-uri test-db})
     ::http/resource-path "/public"
     ::http/type          :jetty
     ::http/port          8080}))
