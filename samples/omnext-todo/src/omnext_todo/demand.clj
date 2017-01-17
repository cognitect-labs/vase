(ns omnext-todo.demand
  "Demand-driven query-handling"
  (:require [clojure.walk :as walk]
            [datomic.api :as d]
            [io.pedestal.log :as log])
  (:import [java.util Date]))

;; Demand-driven data query handling
;; ---------------------------------

;; ### Auxiliary functions
;; ------------------------
(defn demand-query*
  [eid-slice* opts]
  (let [{:keys [eid-lvar bindings where find-cardinality]
         :or {eid-lvar (first eid-slice*)
              find-cardinality '...}} opts
        eid-slice (assoc eid-slice* 0 eid-lvar)]
    (into `[:find [(~'pull ~eid-lvar ~'selector) ~find-cardinality]
            :in ~'$ ~'selector ~@(keys bindings)
            :where
            ~eid-slice]
          where)))

(defn demand-query
  "Given a db value, a pull-based selector, a `where` vector for finding
  a specific entity type, and a map of additional options,
  perform a DB demand-driven query and return the results.
  Options include:
   :as-of - to pass to Datomic's `as-of` function
   :eid-lvar - an lvar symbol to use for the eid, e.g., to reference in `where` constraints
   :bindings - a map of query arg lvars to values
   :where - a vector of `where` clauses to use when making the query
   :find-cardinality - Either '. or '... -- defaults to '...

  Example:
   (demand-query my-db '[*] '[?todo :todo/title] {})"
  ([db selector eid-slice]
   (demand-query db selector eid-slice nil))
  ([db selector eid-slice opts]
   (let [{:keys [as-of bindings]} opts
         db (if as-of (d/as-of db as-of) db)
         query (demand-query* eid-slice opts)]
     (apply d/q query db (or selector '[*]) (vals bindings)))))

(defn todos
  ([db]
   (todos db nil))
  ([db selector]
   (todos db selector nil))
  ([db selector opts]
   (demand-query db selector '[?todo :todo/title] opts)))

;; ### The `read` function
;; This handles query calls from clients for data
;; The read keys align with the logical resource entities

(defmulti demand-read (fn [env k params] k))

(defmethod demand-read :default
  [env k params]
  {:value {:error (str "No handler for read key/resource " k)}})

(defmethod demand-read :by-id
  [env k params]
  (let [{:keys [conn db db-ref query]} env
        {:keys [id]} params
        db (or (some-> db-ref deref) db (d/db conn))]
    {:value (d/pull db (or query '[*]) id)}))

(defmethod demand-read :todos/list
  [env k params]
  (let [{:keys [conn db db-ref query]} env
        db (or (some-> db-ref deref) db (d/db conn))]
    {:value (todos db query params)}))

;; ### The `write`/mutate function
;; This handles mutate/update calls from clients with novel data
;; The write ops/keys are narrow and strictly controlled

(defmulti demand-write
  (fn [env k params]
    (if (:readonly env)
      :feature/readonly
      k)))

(defmethod demand-write :default
  [env k params]
  {:value {:error (str "No handler for write/mutation key " k)}})

(defmethod demand-write :feature/readonly
  [env k params]
  {:value {:error (str "Mutations disabled, readonly mode enabled.")}})

(defmethod demand-write 'todos/create
  [env k params]
  (let [{:keys [conn db db-ref]} env
        db (or (some-> db-ref deref) db (d/db conn))
        todo-txes [(merge
                     {:db/id (d/tempid :db.part/user)
                      :todo/title (:title params)
                      :todo/completed false
                      :todo/created (java.util.Date.)}
                     (when-let [category (:category params)]
                       {:todo/category category}))]]
    {:value {:keys [:todos/list]}
     :action (fn []
               (let [tx-result (d/transact conn todo-txes)]
                 (some-> db-ref (reset! (:db-after tx-result)))
                 {:mutation/success true}))}))

;; ### The `post-process` function
;; This shapes all outbound results from the demand driven api
;; It works on the attribute level.  If the result is a list of
;; entites, it's mapped across the entities.

(defn resolve-enum-ref
  "Resolve an enum ref to its keyword name/ident"
  [env v]
  (if-let [db (:db env)]
    (:db/ident (d/entity db (:db/id v)))
    v))

;; A dispatch-map of response keys and how to post-process their results
;;  -- All functions take two args: the env and the value to shape
(def demand-post-processors
  {:todo/category resolve-enum-ref
   :result (fn [env v]
             (if (map? v)
               (dissoc v :db-before :db-after :telem-data)
               v))})

(defn post-process
  ([env result]
   (post-process env result demand-post-processors))
  ([env result processors-map]
   (walk/postwalk
     (fn [obj]
       (if-let [v-fn (or (and (instance? clojure.lang.MapEntry obj)
                              (get processors-map (key obj)))
                         (and (vector? obj)
                              (= 2 (count obj))
                              (get processors-map (first obj))))]
         (let [[k v] obj]
           [k (if (or (sequential? v)
                      (set? v))
                (into (empty v) (map #(v-fn env %) v))
                (v-fn env v))])
         obj))
     result)))

