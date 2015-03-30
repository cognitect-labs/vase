(ns vase
  (:require [clojure.string :as cstr]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [io.rkn.conformity :as c]
            [ring.util.response :as ring-resp]
            [vase.config :as cfg]
            [vase.descriptor :as descriptor]
            [vase.util :as util]
            [vase.literals]
            [datomic.api :as d]
            [vase.interceptor :as interceptor]))

(def default-master-routes
  `["/" ^:interceptors [interceptor/attach-received-time
                        interceptor/attach-request-id
                        ;; In the future, html-body should be json-body
                        bootstrap/html-body]
    ^:vase/api-root ["/api" {:get [:vase/show-routes show-routes]}
                     ^:interceptors [bootstrap/json-body
                                     interceptor/json-error-ring-response]]])

(defn- update-api-roots
  [master-routes f args]
  (let [api-root-indices (keep-indexed #(when (:vase/api-root (meta %2))
                                          %1)
                                       master-routes)]
    (reduce (fn [acc-routes index] (update-in acc-routes [index] f args))
            master-routes
            api-root-indices)))

(declare append-api)

(defn- maybe-enable-http-upsert
  [config master-routes]
  (if-let [new-verbs (when (cfg/get-key config :http-upsert)
                       `{:post [:vase/append-api append-api]})]
    (update-api-roots master-routes
                      (fn [root _]
                        (let [verbs-index (keep-indexed #(when (map? %2) %1) root)]
                          (update-in root verbs-index merge new-verbs))) nil)
    master-routes))

(defn- force-into-literals!
  [ctx]
  (let [ext-nses (get (:config ctx) :extension-namespaces)
        current-ns (ns-name *ns*)]
    (in-ns 'vase.literals)
    (doseq [[namesp nsalias] ext-nses]
      (require `[~namesp :as ~nsalias]))
    (in-ns current-ns))
  ctx)

;; This seems to be broken (?)
(defn append-routes-to-api-root
  [master-routes route-vecs]
  (update-api-roots master-routes conj route-vecs))

(defn- construct-routes [master-routes route-vecs]
  (vec (expand-routes `[[~(append-routes-to-api-root master-routes route-vecs)]])))

(defn- uniquely-add-routes
  "Builds a new sequence of route-maps, and adds back the old, unique route-maps"
  [master-routes route-vecs old-route-maps]
  (let [new-routes (construct-routes master-routes route-vecs)
        route-names (set (map :route-name new-routes))]
    (reduce (fn [accum rt-map]
              (if (route-names (:route-name rt-map))
                accum
                (conj accum rt-map))) new-routes old-route-maps)))

(defn transact-upsert [{:keys [conn config]} descriptor route-seq]
  ;; TODO: this should attach something about the transaction as meta to route-seq
  (when (get config :transact-upsert)
    (d/transact conn
     [{:db/id (d/tempid :db.part/user)
       :vase/descriptor (get (meta descriptor) :vase/src ":not-found")
       :vase/routes (pr-str route-seq)}]))
  route-seq)

(defn conn-database
  "Given a Datomic URI, attempt to create the database and connect to it,
  returning the connection."
  [config]
  (let [uri (:db-uri config)
        norms (edn/read-string {:readers *data-readers*}
                               (slurp (io/resource "vase-schema.edn")))]
    (d/create-database uri)
    (doto (d/connect uri)
      (c/ensure-conforms norms [:vase/base-schema]))))

(defrecord Context [config conn routes master-routes descriptor])

(defn update
 "Return a new context with the given descriptor added. Will first
  load all necessary schemas, then update the routes and return an updated context"
 [ctx descriptor app-name versions]
 ;; TODO: this should attach something about the transaction as meta to routes
 (reduce (fn [ctx version]
           (descriptor/ensure-conforms descriptor app-name version (:conn ctx))
           (let [route-vecs (descriptor/route-vecs descriptor app-name version)
                 new-routes (uniquely-add-routes (:master-routes ctx) route-vecs (:routes ctx))]
             (transact-upsert ctx new-routes descriptor)
             (assoc ctx
               :routes new-routes
               :descriptor descriptor)))
         ctx versions))

(defn init
 "Return a new, initialized context. "
 [ctx]
 (let [config (or (:config ctx) (cfg/default-config))
       master-routes (maybe-enable-http-upsert config (or (:master-routes ctx) default-master-routes))]
   (-> ctx
       ;; Conform the database and establish the connection
       (assoc :conn (conn-database config))
       ;; Ensure master routes are set
       (assoc :master-routes master-routes)
       ;; Reset routes to the initial state
       (assoc :routes nil)
       ;; Ensure all symbols are available for the literals at descriptor-read-time
       (force-into-literals!))))

(defn load-initial-descriptor
  "Loads the :initial-descriptor and :initial-version keys from the
  config into the given context, returning a new context."
  [ctx cfg]
  (let [descriptor (util/edn-resource (cfg/get-key cfg :initial-descriptor))
        [app-name app-version] (cfg/get-key cfg :initial-version)]
    (update ctx descriptor app-name [app-version])))

(defn show-routes
  "Return a list of all active routes.
  Optionally filter the list with the query param, `f`, which is a fuzzy match
  string value"
  [request]
  (let [vase-context @(:vase-context-atom request)
        routes (:routes vase-context)
        paths (map (juxt :method :path) routes)
        {:keys [f sep edn] :or {f "" sep "<br/>" edn false}} (:query-params request)
        sep (str sep " ")
        results (filter #(.contains ^String (second %) f) paths)]
    (if edn
      (bootstrap/edn-response (vec results))
      (ring-resp/response
        (cstr/join sep (map #(cstr/join " " %) results))))))

;; TODO: This should replace with " " and then replace #"[ \t]+" - preserving newlines
;; Remove the following words when extracting just the descriptor map string
(def remove-words [#":version.*\[.+\]\W" #":app-name.+:.+\W" #":descriptor\W"])
(defn- extract-descriptor-str [body]
  (let [trimmed-body (cstr/trim
                    (reduce (fn [acc-s rword] (cstr/replace acc-s rword ""))
                            body
                            remove-words))]
  (cstr/trim (subs trimmed-body 1 (dec (count trimmed-body))))))

(defn append-api
  "Append an API given an EDN descriptor payload.
  This will also transact schema updates to the DB."
  [request]
  (let [body-string (slurp (:body request))
        {:keys [descriptor app-name version] :as payload} (util/read-edn body-string)
        metad-desc (with-meta descriptor
                     {:vase/src (extract-descriptor-str body-string)})
        versions (if (vector? version) version [version])
        vase-context-atom (:vase-context-atom request)]
    (swap! vase-context-atom update metad-desc app-name versions)
    (bootstrap/edn-response {:added versions})))
