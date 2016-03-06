(ns vase.db
  (:require [datomic.api :as d]
            [io.pedestal.interceptor :as i]))

(defn insert-datomic
  "Provide a Datomic conn and db in all incoming requests"
  [conn]
  (i/-interceptor
    {:name ::insert-datomic
     :enter (fn [context]
              (-> context
                  (assoc-in [:request :conn] conn)
                  (assoc-in [:request :db]   (d/db conn))))}))
