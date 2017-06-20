(ns com.cognitect.vase.fern
  (:require [com.cognitect.vase.api :as a]
            [com.cognitect.vase.interceptor :as vinterceptor]
            [fern :as f]
            [fern.easy :as fe]
            [io.pedestal.http :as http]
            [io.pedestal.http.cors :as cors]
            [io.pedestal.http.csrf :as csrf]
            [io.pedestal.http.params :as params]
            [io.pedestal.http.ring-middlewares :as ring-middlewares]
            [com.cognitect.vase.actions :as actions]
            [io.pedestal.interceptor :as i]
            [datomic.api :as d]
            [com.cognitect.vase.literals :as literals]
            [io.pedestal.log :as log])
  (:import [clojure.lang IObj]))

(defn- synthetic-interceptor-name
  [description]
  {:pre [(map? description)]}
  (if (and (meta description) (not (empty? (meta description))))
    (let [md (meta description)]
      (keyword (str "interceptor-from-line-" (:line md))))
    (keyword (str (gensym "unnamed-interceptor")))))

(defn with-name
  [description]
  (if (contains? description :name)
    description
    (assoc description :name (synthetic-interceptor-name description))))

(defrecord Service [apis])
(defrecord Api [on-startup on-request routes])

(defmethod f/literal 'vase/service [_ desc]
  (assert (contains? desc :apis)
          "vase/service must have at least an :apis key with one or more vase/api definitions")
  (map->Service (with-name desc)))

(defmethod f/literal 'vase/api [_ desc]
  (assert (contains? desc :routes)
          "vase/api must have a :routes key with some routes as its value")
  (map->Api (with-name desc)))

(defmethod f/literal 'vase/respond  [_ d] (actions/map->RespondAction (with-name d)))
(defmethod f/literal 'vase/redirect [_ d] (actions/map->RedirectAction (with-name d)))
(defmethod f/literal 'vase/conform  [_ d] (actions/map->ConformAction (with-name d)))
(defmethod f/literal 'vase/validate [_ d] (actions/map->ValidateAction (with-name d)))
(defmethod f/literal 'vase/attach   [_ key val] (actions/map->AttachAction (with-name {:key key :val val})))

(defrecord Tx         [assertions]
  i/IntoInterceptor
  (-interceptor [_]
    (i/map->Interceptor
     {:enter
      (fn [ctx]
        (if-let [conn (-> ctx :request :conn)]
          (let [tx-result @(d/transact conn assertions)]
            (log/info :tx-result tx-result))
          (log/warn :msg "Cannot execute Tx" :reason :no-connection))
        ctx)})))

(defmethod f/literal 'vase.datomic/tx         [_ & assertions] (->Tx assertions))
(defmethod f/literal 'vase.datomic/attributes [_ & attributes] (->Tx (literals/schema-tx attributes)))

(defmethod f/literal 'vase.datomic/query    [_ d] (actions/map->QueryAction    (with-name d)))
(defmethod f/literal 'vase.datomic/transact [_ d] (actions/map->TransactAction (with-name d)))

(defrecord Connection [uri allow-create?]
  i/IntoInterceptor
  (-interceptor [_]
    (let [created? (if allow-create? (d/create-database uri) false)
          cxn      (d/connect uri)]
      (i/map->Interceptor
       {:enter
        (fn [ctx]
          (update ctx :request assoc
                  :conn cxn
                  :db (d/db cxn)))}))))

(defmethod f/literal 'vase.datomic/connection
  [_ uri-or-map]
  (if (map? uri-or-map)
    (map->Connection uri-or-map)
    (map->Connection {:uri uri-or-map :allow-create? true})))

;; Preload inteceptors available to all
(def stock-interceptor-syms
  '[io.pedestal.http/log-request
    io.pedestal.http/not-found
    io.pedestal.http/html-body
    io.pedestal.http/json-body
    io.pedestal.http/transit-json-body
    io.pedestal.http/transit-msgpack-body
    io.pedestal.http/transit-body
    io.pedestal.http.cors/dev-allow-origin
    io.pedestal.http.csrf/anti-forgery
    io.pedestal.http.params/keyword-params
    io.pedestal.http.params/keyword-body-params
    io.pedestal.http.ring-middlewares/cookies
    com.cognitect.vase.interceptor/attach-received-time
    com.cognitect.vase.interceptor/attach-request-id])

;; TODO - attach metadata to the val for nice errors
;; Should indicate it is a builtin
(defn expose-sym
  [s]
  {:pre [(symbol? s) (resolve s)]}
  (let [var (resolve s)
        val (var-get var)]
    (if (instance? IObj val)
      {s (with-meta val (meta var))}
      {s val})))

(defn- expose-as-env
  [syms]
  (reduce merge {} (map expose-sym syms)))

(defn- constructed-stock-interceptors
  []
  'io.pedestal.http.ring-middlewares/session (ring-middlewares/session))

(defn stock-interceptors
  []
  (merge
   (expose-as-env stock-interceptor-syms)
   (constructed-stock-interceptors)))

(defn load-from-file
  [filename]
  (-> filename
      (fe/load-environment 'vase/plugins)
      (merge (stock-interceptors))))

(defn prepare-service
  ([environment]
   (prepare-service environment 'vase/service))
  ([environment service-key]
   (-> environment
       (f/evaluate service-key)
       (a/service-map))))

(def vase-fern-url "https://github.com/cognitect-labs/vase/blob/master/docs/vase_and_fern.md")

(defn -main [& args]
  (let [[filename & stuff] args]
    (if (or (not filename) (not (empty? stuff)))
      (println "Usage: vase _filename_\n\nVase takes exactly one filename, which must be in Fern format.\nSee " vase-fern-url "  for details.")
      (when-let [prepared-service-map (try
                                        (-> filename
                                            (load-from-file)
                                            (prepare-service))
                                        (catch Throwable t
                                          (fe/print-evaluation-exception t)
                                          nil))]
        (try
          (a/start-service prepared-service-map)
          (catch Throwable t
            (fe/print-other-exception t filename)))))))

(comment


  (try-> filename
         load-from-file
         (:? IOException ioe ,,,)
         (:? Throwable t ,,,)
         prepare-service
         start-service
         (:? IOException ioe ,,,)
         (:? EvaluationException ee ,,,)
         (:? Throwable t ,,,))

  (def filename "test/resources/test_descriptor.fern")

  (def srv
    (try
      (let [s (-> filename load-from-file prepare-service)]
        (try
          (a/start-service s)
          (catch Throwable t
            (def ox t)
            (fe/print-other-exception t filename))))
      (catch Throwable t
        (def ex t)
        (fe/print-evaluation-exception t))))

  srv

  (http/stop srv)
  )
