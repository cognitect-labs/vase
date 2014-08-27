(ns cr-ocs.service
  (:require [clojure.string :as cstr]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition :refer [defroutes expand-routes]]
            [io.pedestal.log :as log]
            [ring.util.response :as ring-resp]
            [themis.validators :as validators]
            [themis.predicates :as preds]
            [cr-ocs.descriptor :as descriptor]
            [cr-ocs.interceptor :as interceptor]
            [cr-ocs.literals]
            [cr-ocs.util :as util]
            [cr-ocs.config :refer [config]]
            [cr-ocs.db :as cdb]))

(declare bash-from-descriptor!)
(declare routes)

(defn clj-ver
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::clj-ver))))

(defn health-check
  [request]
  (ring-resp/response "alive"))

(defn show-routes
  "Return a list of all active routes.
  Optionally filter the list with the query param, `f`, which is a fuzzy match
  string value"
  [request]
  (let [paths (map (juxt :method :path) (deref #'routes))
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
        versions (if (vector? version) version [version])]
    (doseq [v versions]
      (bash-from-descriptor! metad-desc app-name v))
    (bootstrap/edn-response {:added versions})))

(def desc (util/edn-resource (get config :initial-descriptor "sample_descriptor.edn")))

(def master-routes `["/" {:get health-check} ^:interceptors [interceptor/attach-received-time
                                                             interceptor/attach-request-id
                                                             ;; In the future, html-body should be json-body
                                                             bootstrap/html-body]
                     ["/about" {:get clj-ver}]
                     ^::api-root ["/api" ~(merge {:get `show-routes}
                                                 (if (config :http-upsert) {:post `append-api} nil))
                                  ^:interceptors [bootstrap/json-body
                                                  interceptor/json-error-ring-response]]])

(defn append-routes-to-api-root
  [master-routes route-vecs]
  (let [api-root-indices (keep-indexed #(when (-> (meta %2)
                                                  ::api-root)
                                          %1) master-routes)]
    (reduce (fn [acc-routes index] (update-in acc-routes [index] conj route-vecs))
            master-routes
            api-root-indices)))

(defn construct-routes [route-vecs]
  (vec (expand-routes `[[~(append-routes-to-api-root master-routes route-vecs)]])))

;; TODO: Consider making routes an atom and avoiding alter-var-root
(def routes (construct-routes (apply descriptor/route-vecs (cons desc (config :initial-version)))))

(defn uniquely-add-routes
  "Builds a new sequence of route-maps, and adds back the old, unique route-maps"
  [route-vecs old-route-maps]
  (let [new-routes (construct-routes route-vecs)
        route-names (set (map :route-name new-routes))]
    (reduce (fn [accum rt-map]
              (if (route-names (:route-name rt-map))
                accum
                (conj accum rt-map))) new-routes old-route-maps)))

(defn bash-routes! [route-vecs]
  (alter-var-root #'routes
                  (fn [r]
                    (uniquely-add-routes route-vecs r))))

(defn reset-routes! []
  (alter-var-root #'routes
                  (fn [r]
                    (construct-routes (apply descriptor/route-vecs desc (config :initial-version))))))

(defn transact-upsert [descriptor route-seq]
  (when (config :transact-upsert)
    (cdb/transact!
      [{:db/id (cdb/temp-id)
        :cr-ocs/descriptor (get (meta descriptor) :cr-ocs/src ":not-found")
        :cr-ocs/routes (pr-str route-seq)}]))
  route-seq)

(defn bash-from-descriptor!
  "Given a descriptor map, an API/app-name keyword, and a version keyword,
  this will first load all necessary schemas and then `bash routes`,
  Returning the route-seq if no errors occured"
  [descriptor-map app-name version]
  (descriptor/ensure-conforms descriptor-map app-name version)
  (some->> (descriptor/route-vecs descriptor-map app-name version)
           bash-routes!
           (transact-upsert descriptor-map)))

;; Transact the initial version of the API
(apply bash-from-descriptor! (cons desc (config :initial-version)))

;; Consumed by cr-ocs.server/create-server
;; See bootstrap/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; :bootstrap/interceptors []
              ::bootstrap/routes (if (config :enable-upsert) #(deref #'routes) routes)

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::bootstrap/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty or :tomcat (see comments in project.clj
              ;; to enable Tomcat)
              ;;::bootstrap/host "localhost"
              ::bootstrap/type :jetty
              ::bootstrap/port (config :service-port)})

