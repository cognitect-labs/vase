(ns vase.actions
  (:require [clojure.walk :as walk]
            [datomic.api :as d]
            [io.pedestal.interceptor :as interceptor]
            [themis.core :as themis]
            [themis.validators :as validators]
            [themis.predicates :as preds]
            [vase.util :as util])
  (:import java.net.URLDecoder))

;; Code generation tools
;;

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
  [param-sym rule-vec]
  `(themis/unfold-result (themis/validation ~param-sym ~rule-vec)))

(defn query-result-payload
  [req-sym query-result-sym doc]
  (payload-response req-sym doc `{:response ~query-result-sym}))

(defn bind-allowed-arguments
  [req-sym args-sym properties forms]
  `(let [~args-sym (map #(select-keys % ~(vec properties)) (get-in ~req-sym [:json-params :payload]))]
     ~forms))

(defn process-lookup-ref [[str-attr val]]
  [(keyword str-attr) val])

(defn process-id [entity-data]
  (let [id (:db/id entity-data)]
    (cond (vector? id) (assoc entity-data :db/id (process-lookup-ref id))
          (nil? id) (assoc entity-data :db/id (d/tempid :db.part/user))
          :default entity-data)))

(defn perform-transaction
  [req-sym args-sym tx-result-sym forms]
  `(let [conn#          (-> ~req-sym :conn)
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
  [params body status headers enforce-format doc]
  (gensyms [ctx-sym resp-sym req-sym param-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-params ctx-sym req-sym param-sym params)
            (bind-response resp-sym (if enforce-format (payload-response req-sym doc body) (map-response status headers body)))
            (attach-response ctx-sym resp-sym))))

(defn respond-action
  [name params body status headers enforce-format doc]
  (dynamic-interceptor name
                       :respond
                       {:enter (respond-action-exprs params body status headers enforce-format doc)}))

(defn redirect-action-exprs
  [params body status headers url]
  (gensyms [ctx-sym resp-sym req-sym param-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-params ctx-sym req-sym param-sym params)
            (bind-response resp-sym (map-response (or status 302) (merge headers {"Location" url}) body))
            (attach-response ctx-sym resp-sym))))

(defn redirect-action
  [name params body status headers url]
  (dynamic-interceptor name :redirect
                       {:enter (redirect-action-exprs (or params []) body status headers (or url ""))}))

(defn validate-action-exprs
  [params rule-vec doc]
  (gensyms [ctx-sym resp-sym req-sym param-sym]
           (nesting
            (context-fn ctx-sym)
            (bind-request ctx-sym req-sym)
            (bind-params ctx-sym req-sym param-sym params)
            (bind-response resp-sym (payload-response req-sym doc (validation-result param-sym rule-vec)))
            (attach-response ctx-sym resp-sym))))

(defn validate-action
  [name params rule-vec doc]
  (dynamic-interceptor name :validate
                       {:enter (validate-action-exprs params rule-vec doc)}))

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

(defn query-action
  [name query variables coercions constants doc]
  (dynamic-interceptor name :query
                       {:enter (query-action-exprs query variables coercions constants doc)}))

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

(defn transact-action
  [name properties doc]
  (dynamic-interceptor name :transact
                       {:enter (transact-action-exprs properties doc)}))
