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
  (assoc (vase/load "test_descriptor.edn")
         :vase.descriptor/datomic-uri (str "datomic:mem://" (UUID/randomUUID))))

(defn service-map
  "Return a new, fully initialized service map"
  []
  (let [spec (test-spec)]
    (vase.datomic/ensure-schema spec)
    (-> {:env                 :dev
         ::http/routes        (make-master-routes spec)
         ::http/resource-path "/public"
         ::http/type          :jetty
         ::http/port          8080}
        http/default-interceptors
        http/dev-interceptors)))
