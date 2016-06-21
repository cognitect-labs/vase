(ns vase.datomic
  (:require [datomic.api :as d]
            [io.rkn.conformity :as c]
            [io.pedestal.interceptor :as i]))

(defn connect
  "Given an API descriptor, attempt to create the database and connect to it.
  Returns the connection."
  [{:keys [vase.descriptor/datomic-uri]}]
  (d/create-database datomic-uri)
  (d/connect datomic-uri))

(defn- reshape-norms
  [{:keys [vase.descriptor/norms]}]
  (apply merge
         (map (fn [{:keys [vase.descriptor/ident vase.descriptor/txes vase.descriptor/requires]}]
                {ident {:txes txes :requires requires}})
              norms)))

(defn- required-norms
  [{:keys [vase.descriptor/endpoints]}]
  (keep identity (mapcat :vase.descriptor/schemas endpoints)))

(defn ensure-schema
  [descriptor]
  (let [conn (connect descriptor)]
    (c/ensure-conforms conn (reshape-norms descriptor) (required-norms descriptor))
    conn))

(defn insert-datomic
  "Provide a Datomic conn and db in all incoming requests"
  [descriptor]
  (let [conn (connect descriptor)]
    (i/-interceptor
     {:name ::insert-datomic
      :enter (fn [context]
               (-> context
                   (assoc-in [:request :conn] conn)
                   (assoc-in [:request :db]   (d/db conn))))})))
