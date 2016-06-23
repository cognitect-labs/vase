(ns vase.actions
  (:require [clojure.walk :as walk]
            [clojure.spec :as s]
            [datomic.api :as d]
            [io.pedestal.interceptor :as interceptor]
            [vase.util :as util])
  (:import java.net.URLDecoder))

;; Code generation tools
;;

(defn tap [where xs forms]
  `(do
     ~@(for [x xs]
        `(println ~where  ~x))
    ~forms))

(defn context-fn
  [ctx-sym forms]
  `(fn [~ctx-sym] ~forms))

(defn bind-request
  [ctx-sym req-sym forms]
  `(let [~req-sym (:request ~ctx-sym)]
     ~forms))

(defn decode-map
  "URL Decode the values of a Map
  This opens up the potential for non-sanitized input to be rendered."
  [m]
  (walk/postwalk (fn [x] (if (string? x) (URLDecoder/decode x) x)) m))

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

(defn response-body
  [body]
  `{:body ~(or body "")})

(defn query-response-body
  [query-result-sym]
  (response-body `(into [] ~query-result-sym)))

(defn tx-response-body
  [tx-result-sym args-sym]
  (response-body `{:transaction ~tx-result-sym :whitelist ~args-sym}))

(defn bind-response
  [resp-sym resp-forms forms]
  `(let [~resp-sym ~resp-forms]
     ~forms))

(defn derive-status-code
  [ctx-sym resp-sym forms]
  `(let [~resp-sym (assoc ~resp-sym :status (util/status-code (:body ~resp-sym) (:errors ~ctx-sym)))]
     ~forms))

(defn assign-headers
  [resp-sym headers forms]
  `(let [~resp-sym (update ~resp-sym :headers merge ~headers)]
     ~forms))

(defn assign-status-code
  [resp-sym status forms]
  `(let [~resp-sym (assoc ~resp-sym :status ~status)]
     ~forms))

(defn attach-response
  [ctx-sym forms]
  `(assoc ~ctx-sym :response ~forms))

(defn bind-db
  [req-sym db-sym forms]
  `(let [~db-sym (:db ~req-sym)]
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

(defn validation-result
  [param-sym spec]
  (response-body `(reduce (fn [a# [k# v#]]
                            (assoc a# k# (dissoc v# :pred)))
                        {}
                        (:clojure.spec/problems (clojure.spec/explain-data ~spec ~param-sym)))))


(defn bind-allowed-arguments
  [req-sym args-sym properties forms]
  `(let [~args-sym (map #(select-keys % ~(vec properties)) (get-in ~req-sym [:json-params :payload]))]
     ~forms))

(defn process-lookup-ref [[str-attr val]]
  [(if (keyword? str-attr) str-attr (keyword str-attr)) val])

(defn process-id [entity-data]
  (let [id (:db/id entity-data)]
    (cond (vector? id) (assoc entity-data :db/id (process-lookup-ref id))
          (nil? id) (assoc entity-data :db/id (d/tempid :db.part/user))
          :default entity-data)))

(defmulti process-transaction
  (fn [db-op args] db-op))

(defmethod process-transaction :default
  [db-op args]
  ;; the default is to assume the maps are for entity assertions
  (mapv process-id args))

(defmethod process-transaction :vase/retract-entity
  [db-op args]
  ;; We assume the maps are for entity-lookups only
  (mapv (fn [entity-data]
          [:db.fn/retractEntity (:db/id entity-data)]) args))

(defmethod process-transaction :vase/assert-entity
  [db-op args]
  ;; We assume the maps are for entity assertions
  (mapv process-id args))

(defn perform-transaction
  [req-sym args-sym tx-result-sym db-op forms]
  `(let [conn#          (-> ~req-sym :conn)
         tx-result#     (d/transact conn# (process-transaction ~db-op ~args-sym))
         ~tx-result-sym (map (juxt :e :a :v) (:tx-data @tx-result#))]
     ~forms))

(defmacro nesting
  [& forms]
  `(->> ~@(reverse forms)))

(defmacro gensyms
  [[:as syms] & forms]
  (let [bindings (vec (interleave syms (map (fn [s] `(gensym ~(name s))) syms)))]
    `(let ~bindings
       ~@forms)))

;; Building Interceptors
;;

(defn dynamic-interceptor
  "Build an interceptor/interceptor from a map of keys to expressions. The
  expressions will be evaluated and must evaluate to a function of 1
  argument (the interceptor/interceptor context.)"
  [name literal exprs]
  (with-meta
    (interceptor/interceptor (merge {:name name} (util/map-vals eval exprs)))
    {:action-literal literal}))

;; Interceptors for common cases
;;
(defn respond-action-exprs
  [params body status headers]
  (gensyms [ctx-sym resp-sym req-sym param-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-params ctx-sym req-sym param-sym params)
            (bind-response resp-sym (response-body body))
            (assign-headers resp-sym headers)
            (assign-status-code resp-sym (or status 200))
            (attach-response ctx-sym resp-sym))))

(defn respond-action
  [name params body status headers]
  (dynamic-interceptor name
                       :respond
                       {:enter (respond-action-exprs params body status headers)}))

(defn redirect-action-exprs
  [params body status headers url]
  (gensyms [ctx-sym resp-sym req-sym param-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-params ctx-sym req-sym param-sym params)
            (bind-response resp-sym (response-body body))
            (assign-headers resp-sym (merge headers {"Location" url}))
            (assign-status-code resp-sym (or status 302))
            (attach-response ctx-sym resp-sym))))

(defn redirect-action
  [name params body status headers url]
  (dynamic-interceptor name :redirect
                       {:enter (redirect-action-exprs (or params []) body status headers (or url ""))}))

(defn validate-action-exprs
  [params headers spec]
  (gensyms [ctx-sym resp-sym req-sym param-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-params ctx-sym req-sym param-sym params)
            (bind-response resp-sym (validation-result param-sym spec))
            (assign-headers resp-sym headers)
            (derive-status-code ctx-sym resp-sym)
            (attach-response ctx-sym resp-sym))))

(defn validate-action
  [name params headers spec]
  (dynamic-interceptor name :validate
                       {:enter (validate-action-exprs params headers spec)}))

(defn query-action-exprs
  [query variables coercions constants headers]
  (gensyms [ctx-sym resp-sym req-sym args-sym db-sym vals-sym query-result-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-args req-sym args-sym)
            (bind-vals args-sym vals-sym variables coercions)
            (bind-db req-sym db-sym)
            (bind-query-results query-result-sym query db-sym vals-sym constants)
            (bind-response resp-sym (query-response-body query-result-sym))
            (assign-headers resp-sym headers)
            (derive-status-code ctx-sym resp-sym)
            (attach-response ctx-sym resp-sym))))

(defn query-action
  [name query variables coercions constants headers]
  (dynamic-interceptor name :query
                       {:enter (query-action-exprs query variables coercions constants headers)}))

(defn transact-action-exprs
  [properties db-op headers]
  (gensyms [ctx-sym resp-sym req-sym args-sym tx-result-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-allowed-arguments req-sym args-sym properties)
            (perform-transaction req-sym args-sym tx-result-sym db-op)
            (bind-response resp-sym (tx-response-body tx-result-sym args-sym))
            (assign-headers resp-sym headers)
            (derive-status-code ctx-sym resp-sym)
            (attach-response ctx-sym resp-sym))))

(defn transact-action
  [name properties db-op headers]
  (dynamic-interceptor name :transact
                       {:enter (transact-action-exprs properties db-op headers)}))
