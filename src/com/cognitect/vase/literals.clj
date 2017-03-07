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

(defn parse-schema-vec
  [s-vec]
  (assert (every? keyword? (butlast s-vec)))
  (let [doc-string            (last s-vec)
        [ident card kind & _] (take 3 s-vec)
        opt-toggles           (take-while keyword? (drop 3 s-vec))]
    (assert (string? doc-string) (str "The last thing in the vector must be a docstring. Instead, I got a " (type doc-string)))
    (assert (every? #(contains? accepted-schema-toggles %) opt-toggles)
            (str "Short schema toggles must be taken from " accepted-schema-toggles ". I found " opt-toggles))
    (assert (contains? accepted-kinds kind) (str "The value type must be one of " accepted-kinds ". I found " kind))
    (assert (contains? accepted-cards card) (str "The cardinality must be one of " accepted-cards ". I found " card))
    (merge {:db/id                 (d/tempid :db.part/db)
            :db/ident              ident
            :db/valueType          (keyword "db.type" (name kind))
            :db/cardinality        (keyword "db.cardinality" (name card))
            :db.install/_attribute :db.part/db
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
  {:pre [(vector? form)]}
  (mapv parse-schema-vec form))

;; Routing/Action literals
;; -----------------------

(defrecord RespondAction [name params edn-coerce body status headers doc]
  i/IntoInterceptor
  (-interceptor [_]
    (actions/respond-action name params edn-coerce body status headers)))

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

(defrecord ValidateAction [name params headers spec request-params-path doc]
  i/IntoInterceptor
  (-interceptor [_]
    (let [params (or params [])]
      (actions/validate-action name params headers spec request-params-path))))

(defn validate [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (map->ValidateAction form))

(defmethod print-method ValidateAction [t ^java.io.Writer w]
  (.write w (str "#vase/validate" (into {} t))))

(defrecord ConformAction [name from spec to explain-to doc]
  i/IntoInterceptor
  (-interceptor [_]
    (actions/conform-action name from spec to explain-to)))

(defn conform [form]
  {:pre [(map? form)]}
  (map->ConformAction form))

(defmethod print-method ConformAction [t ^java.io.Writer w]
  (.write w (str "#vase/conform" (into {} t))))

(defrecord QueryAction [name params query edn-coerce constants headers to doc]
  i/IntoInterceptor
  (-interceptor [_]
    (actions/query-action name query params (into #{} edn-coerce) constants headers to)))

(defn query [form]
  {:pre [(map? form)
         (:query form)
         (-> form :query vector?)
         (:name form)
         (-> form :name keyword?)]}
  (map->QueryAction form))

(defmethod print-method QueryAction [t ^java.io.Writer w]
  (.write w (str "#vase/query" (into {} t))))

(defrecord TransactAction [name properties db-op headers to doc]
  i/IntoInterceptor
  (-interceptor [_]
    (actions/transact-action name properties db-op headers to)))

(defn transact [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (map->TransactAction (merge {:db-op :vase/assert-entity} form)))

(defmethod print-method TransactAction [t ^java.io.Writer w]
  (.write w (str "#vase/transact" (into {} t))))

(defn- handle-intercept-option [x]
  (cond
    (list? x) (eval x)
    (symbol? x) (resolve x)
    (var? x) (deref x)
    :else x))

(defn intercept [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
   (let [enter (handle-intercept-option (:enter form))
         leave (handle-intercept-option (:leave form))
         error (handle-intercept-option (:leave form))]
     (i/interceptor {:name (:name form)
                     :enter enter
                     :leave leave
                     :error error})))
