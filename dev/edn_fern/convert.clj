(ns edn-fern.convert
  (:require [clojure.string :as str]
            [io.pedestal.interceptor :as i])
  (:import [com.cognitect.vase.actions RespondAction RedirectAction
            ValidateAction TransactAction QueryAction ConformAction
            InterceptAction ValidateAction AttachAction]))

(defn- kn [k]
  (keyword (name k)))

(defn- affix [n a]
  (symbol (str n "-" a)))

(defn- symbolize [k]
  (if (namespace k)
    (symbol (str (namespace k)) (name k))
    (symbol (name k))))

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
                                    (convert-schema-part part)}) txes)]
    (apply merge ret named-parts)))

(defmethod convert :vase/norms
  [ret k v]
  (reduce-kv convert-schema ret v))

(defn- remove-nils
  [m]
  (into {} (remove (comp nil? val) m)))

(def ^:private fern-literals-for-action-types
  {RespondAction   ['vase/respond          [:params :edn-coerce :body :status :headers]]
   RedirectAction  ['vase/redirect         [:params :body :status :headers :url]]
   ValidateAction  ['vase/validate         [:params :headers :spec :request-params-path]]
   ConformAction   ['vase/conform          [:from :to :spec :explain-to]]
   InterceptAction ['vase/intercept        [:enter :leave :error]]
   AttachAction    ['vase/attach           [:key :val]]
   TransactAction  ['vase.datomic/transact [:properties :db-op :headers :to]]
   QueryAction     ['vase.datomic/query    [:params :query :edn-coerce :constants :headers :to]]})

(defn- make-action-lit
  [action]
  (if (symbol? action)
    action
    (let [[nm ks] (fern-literals-for-action-types (type action))]
      (lit nm (remove-nils (select-keys action ks))))))

(defn make-action-name
  [action]
  (if (not (symbol? action))
    (symbolize (:name action))
    action))

(defn make-action-ref
  [action]
  (r (make-action-name action)))

(defn- convert-single-route
  [routesname ret path verbs]
  (let [ret (update ret routesname
                    #(into (or % #{})
                           (for [[verb a-or-as] verbs]
                             [path verb (if (sequential? a-or-as)
                                          (mapv make-action-ref a-or-as)
                                          (make-action-ref a-or-as))])))]
    (into ret
          (for [[verb a-or-as] verbs
                a              (if (sequential? a-or-as) a-or-as [a-or-as])
                :when (not (symbol? a))]
            {(make-action-name a) (make-action-lit a)}))))

(defn- convert-routes
  [ret routesname routes]
  (reduce
   (fn [m [path verbs]]
     (convert-single-route routesname m path verbs))
   ret
   routes))

(defn- convert-api
  [ret apiname {:keys [vase.api/routes vase.api/schemas vase.api/forward-headers vase.api/interceptors]}]
  (let [apiname    (symbolize apiname)
        routesname (routize apiname)
        ret        (assoc ret apiname (lit 'vase/api {:path          (pathize apiname)
                                                      :expose-api-at (str (pathize apiname) "/api")
                                                      :on-request    (into [(r 'connection)]
                                                                           (mapv symbolize interceptors))
                                                      :on-startup    (into [(r 'connection)]
                                                                           (mapv (comp r symbolize) schemas))
                                                      :routes        (r routesname)}))]
    (convert-routes ret routesname routes)))

(defmethod convert :vase/apis
  [ret k v]
  (reduce-kv convert-api ret v))
