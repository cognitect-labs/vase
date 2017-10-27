(ns edn-fern.convert
  (:require [clojure.string :as str])
  (:import [com.cognitect.vase.actions RespondAction RedirectAction ValidateAction TransactAction QueryAction]))

(defn- kn [k]
  (keyword (name k)))

(defn- affix [n a]
  (symbol (str n "-" a)))

(defn- symbolize [k]
  (symbol (str (namespace k)) (name k)))

(defn- pathize [s]
  (str "/" (str/replace (str s) #"\." "/")))

(defn- routize [s]
  (symbol (str/replace (str s) #"/" ".") "routes"))

(defn- lit
  [kind & args]
  (list* 'fern/lit kind args))

(defn- r [s]
  (list `deref s))

(def default-spec
  {'vase/plugin  []
   'http-options {:io.pedestal.http/port      8080
                  :io.pedestal.http/file-path "/public"}})

(defmulti convert (fn [ret k v] k))

(defmethod convert :default
  [ret k v]
  ret)

(defmethod convert :datomic-uri
  [ret k v]
  (-> ret
      (assoc 'connection (lit 'vase.datomic/connection (r 'datomic-uri)))
      (assoc 'datomic-uri v)))

(defmethod convert :activated-apis
  [ret k v]
  (-> ret
      (assoc 'vase/service (lit 'vase/service {:apis (mapv (comp r symbolize) v)
                                               :service-map (r 'http-options)}))
      (assoc 'http-options {:io.pedestal.http/port      8080
                            :io.pedestal.http/file-path "/public"})))

(defmethod convert :descriptor
  [ret k v]
  (reduce-kv convert ret v))

(defmethod convert :vase/specs
  [ret k v]
  (reduce-kv
   (fn [m specname speccode]
     (assoc m
            (symbolize specname)
            speccode))
   ret
   v))

(defn- attribute? [ent] (contains? ent :db.install/_attribute))

(defn- convert-attribute
  [{:keys [db/doc db/cardinality db/ident db/valueType db/isComponent db/noHistory db/unique db/index db/fulltext] :as attr}]
  (-> [ident (kn cardinality) (kn valueType)]
      (into
       (keep identity
             [(when (= :db.unique/value unique)
                :unique)
              (when (= :db.unique/identity unique)
                :identity)
              (when index
                :index)
              (when fulltext
                :fulltext)
              (when isComponent
                :component)
              (when noHistory
                :no-history)]))
      (conj (or doc ""))))

(defn- convert-schema-part
  [ents]
  (if (every? attribute? ents)
    (apply lit 'vase.datomic/attributes (map convert-attribute ents))
    (apply lit 'vase.datomic/tx ents)))

(defn- convert-schema
  [ret schemaname {:keys [vase.norm/requires vase.norm/txes]}]
  (let [schemaname  (symbolize schemaname)
        named-parts (map-indexed (fn [idx part]
                                   {(affix schemaname idx)
                                    (convert-schema-part part)}) txes)
        ret         (apply merge ret named-parts)]
    (update-in ret [:requires schemaname] #(-> (or % [])
                                               (into (mapv (comp r symbolize) requires))
                                               (into (mapv r (mapcat keys named-parts)))))))

(defmethod convert :vase/norms
  [ret k v]
  (reduce-kv convert-schema ret v))

(def ^:private fern-literals-for-action-types
  {RespondAction  'vase/respond
   RedirectAction 'vase/redirect
   ValidateAction 'vase/validate
   TransactAction 'vase.datomic/transact
   QueryAction    'vase.datomic/query})

(defn- make-action-lit
  [action]
  (when-not (fern-literals-for-action-types (type action))
    (println "no mapping for " action ":" (type action)))
  (lit (fern-literals-for-action-types (type action)) ))

(defn- convert-single-route
  [ret [path verbs]]
  (into ret
        (map
         (fn [[verb action-or-actions]]
           (let [rhs (if (sequential? action-or-actions)
                       (mapv make-action-lit action-or-actions)
                       (make-action-lit action-or-actions))]
             [path verb rhs]))
         verbs)))

(defn- convert-routes
  [ret routesname routes]
  (update ret routesname #(reduce convert-single-route (or % #{}) routes)))

(defn- convert-api
  [ret apiname {:keys [vase.api/routes vase.api/schemas vase.api/forward-headers vase.api/interceptors]}]
  (let [apiname    (symbolize apiname)
        routesname (routize apiname)
        ret        (assoc ret apiname (lit 'vase/api {:path          (pathize apiname)
                                                      :expose-api-at (str (pathize apiname) "/api")
                                                      :on-request    (into [(r 'connection)]
                                                                           (mapv (comp r symbolize) interceptors))
                                                      :on-startup    (into [(r 'connection)]
                                                                           (mapv (comp r symbolize) schemas))
                                                      :routes        (r routesname)}))]
    (convert-routes ret routesname routes)))

(defmethod convert :vase/apis
  [ret k v]
  (reduce-kv convert-api ret v))
