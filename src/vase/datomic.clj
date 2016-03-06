(ns vase.datomic
  (:require [datomic.api :as d]
            [io.rkn.conformity :as c]
            [io.pedestal.interceptor :as i]))

(defn connect
  "Given a Datomic URI, attempt to create the database and connect to it,
  returning the connection."
  [uri]
  (d/create-database uri)
  (d/connect uri))

(defn ensure-schema
  [conn norms]
  (c/ensure-conforms conn norms))

(defn insert-datomic
  "Provide a Datomic conn and db in all incoming requests"
  [conn]
  (i/-interceptor
    {:name ::insert-datomic
     :enter (fn [context]
              (-> context
                  (assoc-in [:request :conn] conn)
                  (assoc-in [:request :db]   (d/db conn))))}))
