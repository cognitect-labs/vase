(ns vase.literals
  (:require [clojure.walk :as walk]
            [datomic.api :as d]
            [io.pedestal.http.route.definition :as definition]
            [io.pedestal.interceptor :as i]
            [vase.actions :as actions]
            [vase.util :as util]))

;; TODO: All these literals should be Types/Records that support print-method
;;       to enable full serialization (right now only reading works)

;; Data literals
;; -------------
(defn regex
  "A non-auto-escaping regex literal.
  This is like Regex strings in Clojure pre-1.0.
  If we want the short form, we'll need to come up with a different convention"
  [s]
  {:pre (string? s)}
  (re-pattern s))

;; Schema literals
;; ---------------
(def accepted-schema-toggles #{:unique :index :fulltext :identity nil})

(defn parse-short-schema-vec [s-vec]
  (let [doc-string (last s-vec)
        [ident card kind opt-toggle] (butlast s-vec)]
    (if (contains? accepted-schema-toggles opt-toggle)
      (merge {:db/id (d/tempid :db.part/db)
              :db/ident ident
              :db/valueType (keyword "db.type" (name kind))
              :db/cardinality (keyword "db.cardinality" (name card))
              :db/doc (str doc-string)
              :db.install/_attribute :db.part/db}
             (condp = opt-toggle
               :unique   {:db/unique :db.unique/value}
               :identity {:db/unique :db.unique/identity}
               :index    {:db/index true}
               :fulltext {:db/fulltext true
                          :db/index true}
               nil))
      (throw (ex-info (str "Short schema toggles must be one of: " accepted-schema-toggles)
                      {:found-toggle opt-toggle})))))

(defn short-schema-tx [form]
  {:pre [(vector? form)]}
  (mapv parse-short-schema-vec form))

;; Routing/Action literals
;; -----------------------

(defrecord RespondAction [name params edn-coerce body status headers enforce-format doc]
  i/IntoInterceptor
  (-interceptor [_]
    (actions/respond-action name params body status headers)))

(defn respond [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (map->RespondAction form))

(defmethod print-method RespondAction [t ^java.io.Writer w]
  (.write w (str "#vase/respond" (into {} t))))


(defrecord RedirectAction [name params body status headers url]
  i/IntoInterceptor
  (-interceptor [_]
    (actions/redirect-action name params body status headers url)))

(defn redirect [form]
  {:pre [(map? form)
         (:url form)
         (:name form)
         (-> form :name keyword?)]}
  (map->RedirectAction form))

(defmethod print-method RedirectAction [t ^java.io.Writer w]
  (.write w (str "#vase/redirect" (into {} t))))

(defrecord ValidateAction [name params headers spec doc]
  i/IntoInterceptor
  (-interceptor [_]
    (let [params (or params [])]
      (actions/validate-action name params headers spec))))

(defn validate [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (map->ValidateAction form))

(defmethod print-method ValidateAction [t ^java.io.Writer w]
  (.write w (str "#vase/validate" (into {} t))))

(defrecord QueryAction [name params query edn-coerce constants headers doc]
  i/IntoInterceptor
  (-interceptor [_]
    (let [variables (vec (map #(-> % clojure.core/name keyword) (or params [])))
          coercions (set (map #(-> % clojure.core/name keyword) (or edn-coerce [])))]
      (actions/query-action name query variables coercions constants headers))))

(defn query [form]
  {:pre [(map? form)
         (:query form)
         (-> form :query vector?)
         (:name form)
         (-> form :name keyword?)]}
  (map->QueryAction form))

(defmethod print-method QueryAction [t ^java.io.Writer w]
  (.write w (str "#vase/query" (into {} t))))

(defrecord TransactAction [name properties db-op headers doc]
  i/IntoInterceptor
  (-interceptor [_]
    (actions/transact-action name properties db-op headers)))

(defn transact [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (map->TransactAction (merge {:db-op :vase/assert-entity} form)))

(defmethod print-method TransactAction [t ^java.io.Writer w]
  (.write w (str "#vase/transact" (into {} t))))
