(ns cr-ocs.descriptor
  (:require [io.rkn.conformity :as conformity]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :as route]
            [cr-ocs.interceptor :as interceptor]
            [cr-ocs.literals]
            [cr-ocs.db :as cdb]
            [datomic.api :as d]))

(defn route-vecs
  "Given a descriptor map, an app-name keyword, and a version keyword,
  return the route vecs that can be placed within the container,
  ideally under some /api/ route"
  [descriptor app-name version]
  (into [(str "/" (name app-name) "/" (name version))
         ^:interceptors [(interceptor/forward-headers-interceptor
                          (keyword (name app-name) (name version))
                          (get-in descriptor [app-name version :forward-headers] []))
                         (body-params/body-params
                           (body-params/default-parser-map :edn-options {:readers *data-readers*}))]]
   (get-in descriptor [app-name version :routes]) ))

(defn versions
  "Given a descriptor map and an app-name keyword,
  return a sequence of all the registered version keys.
  Returns `nil` if no versions are found"
  [descriptor app-name]
  (keys (get descriptor app-name)))

(defn norms
  "Given a descriptor map and an app-name,
  return the datomic schema datoms/norms"
  [descriptor app-name]
  (get-in descriptor [app-name :norms]))

(defn ensure-conforms
  "Given a descriptor map, app-name, version, and optionally a DB connection,
  Idempotentally transact the APIs active schema norms.
  If no DB/Datomic connection is passed, it will use the service's root
  connection."
  ([descriptor app-name version]
   (ensure-conforms descriptor app-name version @cdb/conn))
  ([descriptor app-name version db-conn]
   (let [api-schema (get-in descriptor [app-name version :schemas])]
     (conformity/ensure-conforms db-conn (norms descriptor app-name) api-schema))))

(def ^:dynamic *descriptor-entity* nil)

(def ^:dynamic *schema-entity* nil)

(def ^:dynamic *api-entity* nil)

(def ^:dynamic *route-entity* nil)

;; Dynamic var used in post-processing that holds only realized, inert
;; facts.

(def ^:dynamic *realized-facts* nil)

(defn- resolve-schema-entity
  [schema-name]
  (ffirst (d/q '[:find ?schema-entity :in $ ?schema-name
                 :where
                 [_ ::schema ?schema-entity]
                 [?schema-entity ::name ?schema-name]]
               *realized-facts*
               schema-name)))

(defn- schema-facts
  [schema-entries]
  (doall
   (mapcat
    (fn [[schema-name schema-data]]
      (binding [*schema-entity* (cdb/temp-id)]
        (let [current-schema-entity *schema-entity*]
          (concat [[*descriptor-entity* ::schema *schema-entity*]
                   [*schema-entity* ::name schema-name]]
                  (for [dependency (:requires schema-data)]
                    (delay [current-schema-entity ::requires (resolve-schema-entity dependency)]))))))
    schema-entries)))

(defn- route-facts
  [route-spec]
  (let [route-table (route/expand-routes [route-spec])]
    (doall (mapcat
            (fn [entry]
              (binding [*route-entity* (cdb/temp-id)]
                (let [{name :route-name
                       :keys [path method]} entry
                       action-literal (-> entry
                                          :interceptors
                                          last
                                          meta
                                          :action-literal)]
                  [[*api-entity* ::route *route-entity*]
                   [*route-entity* ::name name]
                   [*route-entity* ::path path]
                   [*route-entity* ::method method]
                   [*route-entity* ::action-literal action-literal]])))
            route-table))))

(defn- api-schema-facts
  [schema-vec]
  (let [current-api-entity *api-entity*]
    (doall (for [schema-name schema-vec]
             (delay [current-api-entity ::schema (resolve-schema-entity schema-name)])))))

(defn- api-facts
  "Return a pseudo-datomic DB of [e a v] tuples describing qualities of api entries from a descriptor."
  [api-entries]
  (doall (mapcat (fn [[k v]]
                   (binding [*api-entity* (cdb/temp-id)]
                     (concat [[*descriptor-entity* ::api *api-entity*]
                              [*api-entity* ::name k]]
                             (route-facts (:routes v))
                             (api-schema-facts (:schemas v))))) api-entries)))

(defn- norm-facts
  "Return a pseudo-datomic DB of [e a v] tuples describing qualities of descriptor-val's norms."
  [{:keys [norms]}]
  (let [schema-entries (filter (fn [[k v]] (find v :txes)) norms)]
    (schema-facts schema-entries)))

(defn descriptor-facts
  "Return a pseudo-datomic DB of [e a v] tuples describing qualities of the provided descriptor."
  [descriptor]
  (let [[k v] (first descriptor)]
    (when (and k v)
      (binding [*descriptor-entity* (cdb/temp-id)]
        (let [heterogeneous-facts (concat [[*descriptor-entity* ::name k]]
                                          (norm-facts v)
                                          (api-facts (remove (fn [[k v]] (= k :norms)) v)))
              {realized false
               delayed true} (group-by delay? heterogeneous-facts)]
          (binding [*realized-facts* realized]
            (concat realized (doall (map deref delayed)))))))))

;; Datomic support for above schema

(def schema-dependency-rules
  "Schema dependency rules:

  (schema-dep? ?a ?b) holds when ?a has a direct or transitive schema
  dependency on ?b" 
  '[[(schema-dep? ?ent1 ?ent2)
     [?ent1 ::schema ?ent2]]
    [(schema-dep? ?ent1 ?ent2)
     [?ent1 ::requires ?ent2]]
    [(schema-dep? ?ent1 ?ent2)
     [schema-dep? ?ent1 ?ent3]
     [schema-dep? ?ent3 ?ent2]]])