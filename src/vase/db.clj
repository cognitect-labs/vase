(ns vase.db
  (:require [datomic.api :as d]
            [io.rkn.conformity :as c]
            [vase.config :refer [config]]
            ;; Here to make sure the helpers are available to users/descriptors
            [vase.query-helpers]))

(def norms (clojure.edn/read-string {:readers *data-readers*}
                                    (slurp (clojure.java.io/resource "schema.edn"))))

(defn conn-database
  "Given a Datomic URI, attempt to create the database and connect to it,
  returning the connection"
  [uri]
  (d/create-database uri)
  (doto (d/connect uri)
    (c/ensure-conforms norms [:vase/base-schema])))

(def uri (config :db-uri))
(def conn (atom (conn-database uri)))

(defn db
  ([]
   (d/db @conn))
  ([another-conn]
   (d/db another-conn)))

(defn q
  ([query args]
   (apply d/q query (db) args))
  ([query another-db & args]
   (apply d/q query another-db args)))

(defn temp-id
  ([]
   (d/tempid :db.part/user))
  ([db-ns]
   (d/tempid db-ns)))

(defn transact!
  ([data]
   (d/transact @conn data))
  ([another-conn data]
   (d/transact another-conn data)))

(defn attribute
  ([attr]
   (d/attribute (db) attr))
  ([another-db attr]
   (d/attribute another-db attr)))

