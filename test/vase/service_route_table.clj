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
   (vase/routes "/api" [spec])))

(defn test-spec
  []
  {:app-name :example
   :version :v1
   :descriptor (vase/load "test_descriptor.edn")
   :datomic-uri (str "datomic:mem://" (UUID/randomUUID))})

(defn service-map
  "Return a new, fully initialized service map"
  []
  (let [{:keys [app-name
                version
                descriptor
                datomic-uri] :as spec} (test-spec)
        conn                           (vase.datomic/connect datomic-uri)]
    (vase.datomic/ensure-schema conn (get-in descriptor [app-name :norms]))
    {:env                 :prod
     ::http/routes        (make-master-routes spec)
     ::http/resource-path "/public"
     ::http/type          :jetty
     ::http/port          8080}))
