(ns cr-ocs.descriptor
  (:require [io.rkn.conformity :as conformity]
            [io.pedestal.http.body-params :as body-params]
            [cr-ocs.interceptor :as interceptor]
            [cr-ocs.literals]
            [cr-ocs.db :as cdb]))

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

