(ns com.cognitect.vase.service-route-table
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition.table :as table]
            [com.cognitect.vase :as vase])
  (:import [java.util UUID]))

(defn make-master-routes
  [spec]
  (table/table-routes
   {}
   (vase/routes "/api" spec)))

(defn test-spec
  []
  {:activated-apis [:example/v1 :example/v2]
   :descriptor (vase/load-edn-resource "test_descriptor.edn")
   :datomic-uri (str "datomic:mem://" (UUID/randomUUID))})

(defn service-map
  "Return a new, fully initialized service map"
  []
  (let [{:keys [activated-apis
                descriptor
                datomic-uri] :as app-spec} (test-spec)
        conns (vase/ensure-schema app-spec)]
    (vase/specs app-spec)
    {:env                 :prod
     ::http/routes        (make-master-routes app-spec)
     ::http/resource-path "/public"
     ::http/type          :jetty
     ::http/port          8080}))

(comment

  (service-map)
  (let [s (test-spec)]
    (vase.datomic/normalize-norm-keys (get-in s [:descriptor :vase/norms])))

  (vase/routes "/api" (test-spec)))
