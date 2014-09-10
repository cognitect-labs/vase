(ns cr-ocs
  (:require [clojure.string :as cstr]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route.definition :refer [expand-routes]]
            [themis.validators :as validators]
            [themis.predicates :as preds]
            [ring.util.response :as ring-resp]
            [cr-ocs.descriptor :as descriptor]
            [cr-ocs.util :as util]
            [cr-ocs.config :refer [config]]
            [cr-ocs.literals]
            [cr-ocs.db :as cdb]
            [cr-ocs.interceptor :as interceptor]))

(defn update-api-roots
  [master-routes f args]
  (let [api-root-indices (keep-indexed #(when (:cr-ocs/api-root (meta %2))
                                          %1)
                                       master-routes)]
    (reduce (fn [acc-routes index] (update-in acc-routes [index] f args))
            master-routes
            api-root-indices)))

;; This seems to be broken
(defn append-routes-to-api-root
  [master-routes route-vecs]
  (update-api-roots master-routes conj route-vecs))

(defn construct-routes [master-routes route-vecs]
  (vec (expand-routes `[[~(append-routes-to-api-root master-routes route-vecs)]])))

(defn uniquely-add-routes
  "Builds a new sequence of route-maps, and adds back the old, unique route-maps"
  [master-routes route-vecs old-route-maps]
  (let [new-routes (construct-routes master-routes route-vecs)
        route-names (set (map :route-name new-routes))]
    (reduce (fn [accum rt-map]
              (if (route-names (:route-name rt-map))
                accum
                (conj accum rt-map))) new-routes old-route-maps)))

(defn bash-routes! [routes route-vecs]
  (swap! routes
         (fn [current-routes]
           (uniquely-add-routes (:master-routes (meta routes) []) route-vecs current-routes))))

(defn transact-upsert [descriptor route-seq]
  ;; TODO: this should attach something about the transaction as meta to route-seq
  (when (config :transact-upsert)
    (cdb/transact!
      [{:db/id (cdb/temp-id)
        :cr-ocs/descriptor (get (meta descriptor) :cr-ocs/src ":not-found")
        :cr-ocs/routes (pr-str route-seq)}]))
  route-seq)

  ;; TODO: this should attach something about the transaction as meta to routes
(defn bash-from-descriptor!
  "Given a route-atom, a descriptor map, an API/app-name keyword, and a version keyword,
  this will first load all necessary schemas and then `bash routes`,
  Returning the route-seq if no errors occured"
  [routes descriptor-map app-name version]
  (descriptor/ensure-conforms descriptor-map app-name version)
  (some->> (descriptor/route-vecs descriptor-map app-name version)
           (bash-routes! routes)
           (transact-upsert descriptor-map)))

(defn force-into-literals!
  ([] (force-into-literals! (config :extension-namespaces)))
  ([ext-nses]
   (let [current-ns (ns-name *ns*)]
     (in-ns 'cr-ocs.literals)
     (doseq [[namesp nsalias] ext-nses]
       (require `[~namesp :as ~nsalias]))
     (in-ns current-ns))))

(defn show-routes
  "Return a list of all active routes.
  Optionally filter the list with the query param, `f`, which is a fuzzy match
  string value"
  [request]
  (let [routes (:routes-atom request)
        paths (map (juxt :method :path) @routes)
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
(defn extract-descriptor-str [body]
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
                     {:cr-ocs/src (extract-descriptor-str body-string)})
        versions (if (vector? version) version [version])
        routes (:routes-atom request)]
    (doseq [v versions]
      (bash-from-descriptor! routes metad-desc app-name v))
    (bootstrap/edn-response {:added versions})))

(defn maybe-enable-http-upsert
  [master-routes routes]
  (if-let [new-verbs (when (config :http-upsert)
                       `{:post [:cr-ocs/append-api append-api]})]
    (update-api-roots master-routes
                      (fn [root _]
                        (let [verbs-index (keep-indexed #(when (map? %2) %1) root)]
                          (update-in root verbs-index merge new-verbs))) nil)
    master-routes))

(defn init-descriptor-routes!
  "Optionally given a io.pedestal.routes.definition/expand-routes
  compatible vector, returns an atom which will be updated to hold the
  current routing table sequence as new descriptors are incorporated.

  Notes:
   - If you're passing in your own master-routes, some interceptors are required
     for response generation (attach-received-time, attach-request-id)"
  [& args]
  (let [{:keys [routes-atom master-routes descriptor initial-version]
         :or {routes-atom (atom nil)
              master-routes `["/" ^:interceptors [interceptor/attach-received-time
                                                  interceptor/attach-request-id
                                                  ;; In the future, html-body should be json-body
                                                  bootstrap/html-body]
                              ^:cr-ocs/api-root ["/api" {:get [:cr-ocs/show-routes show-routes]}
                                                 ^:interceptors [bootstrap/json-body
                                                                 interceptor/json-error-ring-response]]]
              descriptor (util/edn-resource (get config :initial-descriptor "sample_descriptor.edn"))
              initial-version (config :initial-version)}} args
        master-routes (maybe-enable-http-upsert master-routes routes-atom)]
    ;; Reset the atom to the initial state
    (reset! routes-atom nil)
    ;; Setup the meta on the routes
    (alter-meta! routes-atom assoc :master-routes master-routes :descriptor descriptor)
    ;; Ensure all symbols are available for the literals at descriptor-read-time
    (force-into-literals!)
    ;; Transact the initial version of the API
    ;; TODO: Once bash-from-descriptor! includes transaction info in its meta, it should be added routes
    (apply bash-from-descriptor! routes-atom (cons descriptor initial-version))
    routes-atom))

