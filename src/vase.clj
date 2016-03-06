(ns vase
  (:refer-clojure :exclude [update])
  (:require [clojure.string :as cstr]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [io.pedestal.interceptor :as i]
            [io.rkn.conformity :as c]
            [ring.util.response :as ring-resp]
            [vase.config :as conf]
            [vase.descriptor :as descriptor]
            [vase.util :as util]
            [vase.literals]
            [datomic.api :as d]
            [vase.interceptor :as interceptor]
            [clojure.string :as str]
            [vase.db :as db]))

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
  (if-let [new-verbs (when (conf/get-key config :http-upsert)
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

(defn connect-database
  "Given a Datomic URI, attempt to create the database and connect to it,
  returning the connection."
  [uri]
  (let [norms (util/edn-resource "vase-schema.edn")]
    (d/create-database uri)
    (doto (d/connect uri)
      (c/ensure-conforms norms [:vase/base-schema]))))

(defn ensure-schema
  [conn norms]
  (c/ensure-conforms conn norms))

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
 "Return a new, initialized Vase context."
 [ctx]
 (let [config (or (:config ctx) (conf/default-config))
       master-routes (maybe-enable-http-upsert config (or (:master-routes ctx) default-master-routes))]
   (-> ctx
       ;; Conform the database and establish the connection
       (assoc :conn (connect-database (:db-uri config)))
       ;; Ensure master routes are set
       (assoc :master-routes master-routes)
       ;; Reset routes to the initial state
       (assoc :routes nil)
       ;; Ensure all symbols are available for the literals at descriptor-read-time
       (force-into-literals!))))

(defn load-descriptor
  "Given a resource name, loads a descriptor, using the proper readers to get
   support for Vase literals."
  [res]
  (util/edn-resource res))

(defn load-initial-descriptor
  "Loads the :initial-descriptor and :initial-version keys from the
  config into the given context, returning a new context."
  ([ctx]
   (load-initial-descriptor ctx (:config ctx)))
  ([ctx conf]
   (let [descriptor (load-descriptor (conf/get-key conf :initial-descriptor))
         [app-name app-version] (conf/get-key conf :initial-version)]
     (update ctx descriptor app-name [app-version]))))

(defn bootstrap-vase-context! [ctx-atom master-routes]
  (swap! ctx-atom assoc :master-routes master-routes)
  (swap! ctx-atom init)
  (swap! ctx-atom load-initial-descriptor))

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


;;
;; New API starts here
;;

(defn describe-api
  "Return a list of all active routes.
  Optionally filter the list with the query param, `f`, which is a fuzzy match
  string value"
  [routes]
  (i/-interceptor
   {:enter (fn [context]
             (let [{:keys [f sep edn] :or {f "" sep "<br/>" edn false}} (-> context :request :query-params)
                   results                                              (mapv #(take 2 %) routes)]
               (assoc context :response
                      (if edn
                        (bootstrap/edn-response results)
                        (ring-resp/response
                         (str/join sep (map #(str/join " " %) results)))))))}))

(def common-api-interceptors
  [interceptor/attach-received-time
   interceptor/attach-request-id
   bootstrap/json-body
   interceptor/vase-error-ring-response])

;; TODO Instead of assuming datomic here, we should accept anything
;; that we can deref to get a connection
;; TODO We should add an interceptor that executes transactions on :leave
(defn app-interceptors
  [{:keys [descriptor app-name version datomic-uri]}]
  (let [datomic-conn (d/connect datomic-uri)]
    (into common-api-interceptors
          [(interceptor/forward-headers-interceptor (keyword (name app-name) (name version))
                                                    (get-in descriptor [app-name version :forward-headers] []))
           (db/insert-datomic datomic-conn)
           (body-params/body-params (body-params/default-parser-map :edn-options {:readers *data-readers*}))])))

(defn- specified-routes
  [{:keys [descriptor app-name version]}]
  (get-in descriptor [app-name version :routes]))

(defn api-routes
  "Given a descriptor map, an app-name keyword, and a version keyword,
   return route vectors in Pedestal's tabular format. Routes will all be
   subordinated under `base`"
  [base spec make-interceptors-fn]
  (let [common (app-interceptors spec)]
    (for [[path verb-map] (specified-routes spec)
          [verb action]   verb-map]
      [(str base path) verb (make-interceptors-fn (conj common (i/-interceptor action)))])))

(defn- api-base
  [base {:keys [app-name version]}]
  (str base "/" (name app-name) "/" (name version)))

(defn- api-description-route
  [api-root make-interceptors-fn routes route-name]
  [api-root :get (make-interceptors-fn (conj common-api-interceptors (describe-api routes))) :route-name route-name])

(defn- api-description-route-name
  [{:keys [app-name version]}]
  (keyword (str (name app-name) "-" (name version)) "describe"))

(defn- spec-routes
  "Return a seq of route vectors from a single specification"
  [api-root make-interceptors-fn spec]
  (let [app-version-root   (api-base api-root spec)
        app-version-routes (api-routes app-version-root spec make-interceptors-fn)
        app-api-route      (api-description-route app-version-root make-interceptors-fn app-version-routes (api-description-route-name spec))]
    (cons app-api-route app-version-routes)))

(defn routes
  "Return a seq of route vectors for Pedestal's table routing syntax. Routes
   will all begin with `api-root/:app-name/:version`.

   `spec-or-specs` is either a single spec (as a map) or a collection of specs.

   The routes will support all the operations defined in the
   spec. Callers should treat the format of these routes as
   opaque. They may change in number, quantity, or layout."
  [api-root spec-or-specs & {:keys [make-interceptors-fn] :or {make-interceptors-fn identity} :as opts}]
  (let [specs     (if (sequential? spec-or-specs) spec-or-specs [spec-or-specs])
        routes    (mapcat (partial spec-routes api-root make-interceptors-fn) specs)
        api-route (api-description-route api-root make-interceptors-fn routes :describe-apis)]
    (cons api-route routes)))
