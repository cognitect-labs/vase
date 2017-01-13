(ns com.cognitect.vase.datomic
  (:require [datomic.api :as d]
            [io.rkn.conformity :as c]
            [io.pedestal.interceptor :as i]))

(defn new-db-uri []
  (str "datomic:mem://" (d/squuid)))

(defn connect
  "Given a Datomic URI, attempt to create the database and connect to it,
  returning the connection."
  [uri]
  (d/create-database uri)
  (d/connect uri))

(defn normalize-norm-keys
  [norms]
  (reduce
    (fn [acc [k-title v-map]]
      (assoc acc
             k-title (reduce
                       (fn [norm-acc [nk nv]]
                         (assoc norm-acc
                                (keyword (name nk)) nv))
                       {} v-map)))
    {}
    norms))

(defn ensure-schema
  [conn norms]
  (c/ensure-conforms conn (normalize-norm-keys norms)))

(defn insert-datomic
  "Provide a Datomic conn and db in all incoming requests"
  [conn]
  (i/interceptor
    {:name ::insert-datomic
     :enter (fn [context]
              (-> context
                  (assoc-in [:request :conn] conn)
                  (assoc-in [:request :db]   (d/db conn))))}))

