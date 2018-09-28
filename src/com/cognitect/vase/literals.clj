(ns com.cognitect.vase.literals
  (:require [clojure.walk :as walk]
            [datomic.api :as d]
            [io.pedestal.http.route.definition :as definition]
            [io.pedestal.interceptor :as i]
            [com.cognitect.vase.actions :as actions]
            [com.cognitect.vase.util :as util]))

;; Data literals
;; -------------

;; Schema literals
;; ---------------
(def accepted-schema-toggles #{:unique :identity :index :fulltext :component :no-history})
(def accepted-kinds          #{:keyword :string :boolean :long :bigint :float :double :bigdec :ref :instant :uuid :uri :bytes})
(def accepted-cards          #{:one :many})

(def schema-tx-usage
  "[ _attribute-name_ _cardinality_ _type_ _toggles_* _docstring_ ]")

(defmacro schema-problem
  [flavor actual]
  `(str "A schema attribute must look like this:\n\n"
       schema-tx-usage
       "\n\n"
       ~flavor
       "\n\n"
       "I got:\n\n"
       (pr-str ~actual)))

(defmacro schema-assert
  [f flavor emit]
  `(when-not ~f
     (throw (AssertionError. (schema-problem ~flavor ~emit)))))

(defn parse-schema-vec
  [s-vec]
  (schema-assert (every? keyword? (butlast s-vec))
                 "All of _attribute-name_, _cardinality_, _type_, and _toggles_ must be Clojure keywords."
                 s-vec)
  (let [doc-string            (last s-vec)
        [ident card kind & _] (take 3 s-vec)
        opt-toggles           (take-while keyword? (drop 3 s-vec))]
    (schema-assert (string? doc-string) "The last thing in the vector must be a docstring." s-vec)
    (schema-assert (every? #(contains? accepted-schema-toggles %) opt-toggles)
            (str "Short schema toggles must be taken from " accepted-schema-toggles) opt-toggles)
    (schema-assert (contains? accepted-kinds kind) (str "The value type must be one of " accepted-kinds) kind)
    (schema-assert (contains? accepted-cards card) (str "The cardinality must be one of " accepted-cards) card)
    (merge {:db/ident              ident
            :db/valueType          (keyword "db.type" (name kind))
            :db/cardinality        (keyword "db.cardinality" (name card))
            :db/doc                doc-string}
           (reduce (fn [m opt]
                     (merge m (case opt
                                :unique     {:db/unique :db.unique/value}
                                :identity   {:db/unique :db.unique/identity}
                                :index      {:db/index true}
                                :fulltext   {:db/fulltext true
                                             :db/index    true}
                                :component  {:db/isComponent true}
                                :no-history {:db/noHistory true}
                                nil)))
                   {}
                   opt-toggles))))

(defn schema-tx [form]
  (schema-assert (sequential? form) "The top level must be a vector or list." form)
  (schema-assert (every? vector? form) "The nested elements must be vectors" form)
  (mapv parse-schema-vec form))

;; Routing/Action literals
;; -----------------------

(defn respond [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (actions/map->RespondAction form))

(defn redirect [form]
  {:pre [(map? form)
         (:url form)
         (:name form)
         (-> form :name keyword?)]}
  (actions/map->RedirectAction form))

(defn validate [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (actions/map->ValidateAction form))

(defn conform [form]
  {:pre [(map? form)]}
  (actions/map->ConformAction form))

(defn- for-cloud [a]
  (assoc a :cloud? true))

(defn query [form]
  {:pre [(map? form)
         (:query form)
         (-> form :query vector?)
         (:name form)
         (-> form :name keyword?)]}
  (actions/map->QueryAction form))

(defn query-cloud [form]
  {:pre [(map? form)
         (:query form)
         (-> form :query vector?)
         (:name form)
         (-> form :name keyword?)]}
  (actions/map->CloudQueryAction form))

(defn transact [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (actions/map->TransactAction (merge {:db-op :vase/assert-entity} form)))

(defn transact-cloud [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (actions/map->CloudTransactAction (merge {:db-op :vase/assert-entity} form)))

(defn intercept [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (actions/map->InterceptAction form))

(defn attach [form]
  {:pre [(map? form)]}
  (actions/map->AttachAction form))
