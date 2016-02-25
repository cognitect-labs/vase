(ns vase.literals
  (:require [clojure.walk :as walk]
            [themis.core  :as themis]
            ;; Even though preds and validators are not referenced in
            ;; this file, code from validators is eval'ed in this
            ;; context.
            [themis.predicates :as preds]
            [themis.validators :as validators]
            [vase.util  :as util]
            [io.pedestal.http.route.definition :as definition]
            [io.pedestal.interceptor :as i :refer [interceptor]]
            [io.pedestal.log :as log]
            [datomic.api :as d])
  (:import (java.net URLDecoder)))

;; TODO: All these literals should be Types/Records that support print-method
;;       to enable full serialization (right now only reading works)

(defn decode-map
  "URL Decode the values of a Map
  This opens up the potential for non-sanitized input to be rendered."
  [m]
  (walk/postwalk (fn [x] (if (string? x) (URLDecoder/decode x) x)) m))

(defn process-lookup-ref [[str-attr val]]
  [(keyword str-attr) val])

(defn process-id [entity-data]
  (let [id (:db/id entity-data)]
    (cond (vector? id) (assoc entity-data :db/id (process-lookup-ref id))
          (nil? id) (assoc entity-data :db/id (d/tempid :db.part/user))
          :default entity-data)))

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

(defn context-fn
  [ctx-sym forms]
  `(fn [~ctx-sym] ~forms))

(defn bind-request
  [ctx-sym req-sym forms]
  `(let [~req-sym (:request ~ctx-sym)]
     ~forms))

(defn bind-params
  [ctx-sym req-sym param-sym params forms]
  `(let [{:keys ~(or params []) :as ~param-sym} (merge
                                                 (decode-map (:path-params ~req-sym))
                                                 (:params ~req-sym)
                                                 (:json-params ~req-sym)
                                                 (:edn-params ~req-sym))]
     ~forms))

(defn bind-args
  [req-sym args-sym forms]
  `(let [~args-sym (merge (:path-params ~req-sym)
                          (:params ~req-sym)
                          (:json-params ~req-sym)
                          (:edn-params ~req-sym))]
     ~forms))

(defn payload-response
  [req-sym doc body]
  `(util/response (util/payload ~req-sym ~(or doc "") ~(or body ""))))

(defn map-response
  [status headers body]
  `{:status ~(or status 200) :headers ~(or headers {}) :body ~(or body "")})

(defn bind-response
  [resp-sym resp-forms forms]
  `(let [~resp-sym ~resp-forms]
     ~forms))

(defn attach-response
  [ctx-sym forms]
  `(assoc ~ctx-sym :response ~forms))

(defn bind-db
  [req-sym db-sym forms]
  `(let [vase-ctx# (:vase-context-atom ~req-sym)
         ~db-sym   (d/db (:conn (deref vase-ctx#)))]
     ~forms))

(defn bind-query-results
  [result-sym query db-sym vals-sym constants forms]
  `(let [~result-sym (apply d/q '~query ~db-sym (concat ~vals-sym ~constants))]
     ~forms))

(defn coerce-arg-val
  [args-sym k]
  `(let [inv# (get ~args-sym ~k)]
     (try (util/read-edn inv#)
          (catch Exception e# inv#))))

(defn arg-val
  [args-sym k]
  `(get ~args-sym ~k))

(defn bind-vals
  [args-sym vals-sym variables coercions forms]
  `(let [~vals-sym [~(mapcat
                      (fn [k]
                        (if (contains? coercions k)
                          (coerce-arg-val args-sym k)
                          (arg-val args-sym k)))
                      variables)]]
     ~forms))

(defn query-result-payload
  [req-sym query-result-sym doc]
  (payload-response req-sym doc `{:response ~query-result-sym}))

(defn bind-allowed-arguments
  [req-sym args-sym properties forms]
  `(let [~args-sym (map #(select-keys % ~(vec properties)) (get-in ~req-sym [:json-params :payload]))]
     ~forms))

(defn perform-transaction
  [req-sym args-sym tx-result-sym forms]
  `(let [conn#          (-> ~req-sym :vase-context-atom deref :conn)
         tx-result#     (d/transact conn# (mapv process-id ~args-sym))
         ~tx-result-sym (map (juxt :e :a :v) (:tx-data @tx-result#))]
     ~forms))

(defn tx-result-payload
  [req-sym args-sym tx-result-sym doc]
  (payload-response req-sym doc `{:transaction ~tx-result-sym :whitelist ~args-sym}))

(defmacro nesting
  [& forms]
  `(->> ~@(reverse forms)))

(defmacro gensyms
  [[:as syms] & forms]
  (let [bindings (vec (interleave syms (map (fn [s] `(gensym ~(name s))) syms)))]
    `(let ~bindings
       ~@forms)))

(defn respond-action-exprs
  [params body status headers enforce-format doc]
  (gensyms [ctx-sym resp-sym req-sym param-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-params ctx-sym req-sym param-sym params)
            (bind-response resp-sym (if enforce-format (payload-response req-sym doc body) (map-response status headers body)))
            (attach-response ctx-sym resp-sym))))

(defn dynamic-interceptor
  "Build an interceptor from a map of keys to expressions. The
  expressions will be evaluated and must evaluate to a function of 1
  argument (the interceptor context.)"
  [name literal exprs]
  (with-meta
    (interceptor (merge {:name name} (util/map-vals eval exprs)))
    {:action-literal literal}))

(defn respond-action-intc
  [name params body status headers enforce-format doc]
  (dynamic-interceptor name
                       :respond
                       {:enter (respond-action-exprs params body status headers enforce-format doc)}))

(defrecord RespondAction [name params edn-coerce body status headers enforce-format doc]
  i/IntoInterceptor
  (-interceptor [_]
    (respond-action-intc name params body status headers enforce-format doc))

  definition/ExpandableVerbAction
  (expand-verb-action [_]
    (definition/expand-verb-action
      (respond-action-intc name params body status headers enforce-format doc))))

(defn respond [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (map->RespondAction form))

(defmethod print-method RespondAction [t ^java.io.Writer w]
  (.write w (str "#vase/respond" (into {} t))))

(defn redirect-action-exprs
  [params body status headers url]
  (gensyms [ctx-sym resp-sym req-sym param-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-params ctx-sym req-sym param-sym params)
            (bind-response resp-sym (map-response (or status 302) (merge headers {"Location" url}) body))
            (attach-response ctx-sym resp-sym))))

(defn redirect-action-intc
  [name params body status headers url]
  (dynamic-interceptor name :redirect
                       {:enter (redirect-action-exprs (or params []) body status headers (or url ""))}))

(defrecord RedirectAction [name params body status headers url]
  i/IntoInterceptor
  (-interceptor [_]
    (redirect-action-intc name params body status headers url))

  definition/ExpandableVerbAction
  (expand-verb-action [_]
    (definition/expand-verb-action
      (redirect-action-intc name params body status headers url))))

(defn redirect [form]
  {:pre [(map? form)
         (:url form)
         (:name form)
         (-> form :name keyword?)]}
  (map->RedirectAction form))

(defmethod print-method RedirectAction [t ^java.io.Writer w]
  (.write w (str "#vase/redirect" (into {} t))))

(defn validation-result
  [param-sym rule-vec]
  `(themis/unfold-result (themis/validation ~param-sym ~rule-vec)))

(defn validate-action-exprs
  [params rule-vec doc]
  (gensyms [ctx-sym resp-sym req-sym param-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-params ctx-sym req-sym param-sym params)
            (bind-response resp-sym (payload-response req-sym doc (validation-result param-sym rule-vec)))
            (attach-response ctx-sym resp-sym))))

(defn validate-action-intc
  [name params rule-vec doc]
  (dynamic-interceptor name :validate
                       {:enter (validate-action-exprs params rule-vec doc)}))

(defrecord ValidateAction [name params properties doc]
  i/IntoInterceptor
  (-interceptor [_]
    (let [params   (or params [])
          rule-vec (walk/postwalk
                    (fn [form] (if (symbol? form)
                                 (util/fully-qualify-symbol (the-ns 'vase.literals)
                                                            form)
                                 form))
                    (or properties []))]
      (validate-action-intc name params rule-vec doc)))

  definition/ExpandableVerbAction
  (expand-verb-action [_]
    (let [params   (or params [])
          rule-vec (walk/postwalk
                    (fn [form] (if (symbol? form)
                                 (util/fully-qualify-symbol (the-ns 'vase.literals)
                                                            form)
                                 form))
                    (or properties []))]
      (definition/expand-verb-action
        (validate-action-intc name params rule-vec doc)))))

(defn validate [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (map->ValidateAction form))

(defmethod print-method ValidateAction [t ^java.io.Writer w]
  (.write w (str "#vase/validate" (into {} t))))

(defn query-action-exprs
  [query variables coercions constants doc]
  (gensyms [ctx-sym resp-sym req-sym args-sym db-sym vals-sym query-result-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-args req-sym args-sym)
            (bind-vals args-sym vals-sym variables coercions)
            (bind-db req-sym db-sym)
            (bind-query-results query-result-sym query db-sym vals-sym constants)
            (bind-response resp-sym (query-result-payload req-sym query-result-sym doc))
            (attach-response ctx-sym resp-sym))))

(defn query-action-intc
  [name query variables coercions constants doc]
  (dynamic-interceptor name :query
                       {:enter (query-action-exprs query variables coercions constants doc)}))

(defrecord QueryAction [name params query edn-coerce constants doc]
  i/IntoInterceptor
  (-interceptor [_]
    (let [variables (vec (map #(-> % clojure.core/name keyword) (or params [])))
          coercions (set (map #(-> % clojure.core/name keyword) (or edn-coerce [])))]
      (query-action-intc name query variables coercions constants doc)))

  definition/ExpandableVerbAction
  (expand-verb-action [_]
    (let [variables (vec (map #(-> % clojure.core/name keyword) (or params [])))
          coercions (set (map #(-> % clojure.core/name keyword) (or edn-coerce [])))]
      (definition/expand-verb-action
        (query-action-intc name query variables coercions constants doc)))))

(defn query [form]
  {:pre [(map? form)
         (:query form)
         (-> form :query vector?)
         (:name form)
         (-> form :name keyword?)]}
  (map->QueryAction form))

(defmethod print-method QueryAction [t ^java.io.Writer w]
  (.write w (str "#vase/query" (into {} t))))

(defn transact-action-exprs
  [properties doc]
  (gensyms [ctx-sym resp-sym req-sym args-sym tx-result-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-allowed-arguments req-sym args-sym properties)
            (perform-transaction req-sym args-sym tx-result-sym)
            (bind-response resp-sym (tx-result-payload req-sym args-sym tx-result-sym doc))
            (attach-response ctx-sym resp-sym))))

(defn transact-action-intc
  [name properties doc]
  (dynamic-interceptor name :transact
                       {:enter (transact-action-exprs properties doc)}))

(defrecord TransactAction [name properties doc]
  i/IntoInterceptor
  (-interceptor [_]
    (transact-action-intc name properties doc))

  definition/ExpandableVerbAction
  (expand-verb-action [_]
    (definition/expand-verb-action
      (transact-action-intc name properties doc))))

(defn transact [form]
  {:pre [(map? form)
         (:name form)
         (-> form :name keyword?)]}
  (map->TransactAction form))

(defmethod print-method TransactAction [t ^java.io.Writer w]
  (.write w (str "#vase/transact" (into {} t))))
