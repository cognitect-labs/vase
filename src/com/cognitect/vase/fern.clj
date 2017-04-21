(ns com.cognitect.vase.fern
  (:require [com.cognitect.vase.interceptor :as vinterceptor]
            [fern :as f]
            [fern.easy :as easy]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.cors :as cors]
            [io.pedestal.http.csrf :as csrf]
            [io.pedestal.http.params :as params]
            [io.pedestal.http.ring-middlewares :as ring-middlewares]
            [com.cognitect.vase.literals :as literals]
            [com.cognitect.vase.datomic :as datomic]
            [com.cognitect.vase :as vase]))

(defn- synthetic-interceptor-name
  [description]
  {:pre [(map? description)]}
  (if (and (meta description) (not (empty? (meta description))))
    (let [md (meta description)]
      (keyword (str "interceptor-from-line-" (:line md))))
    (keyword (str (gensym "unnamed-interceptor")))))

(defn- with-name
  [description]
  (if (contains? description :name)
    description
    (assoc description :name (synthetic-interceptor-name description))))

(defrecord Service [apis])

(defmethod f/literal 'vase/service
  [_ desc]
  (assert (contains? desc :apis)
          "vase/service must have at least an :apis key with one or more vase/api definitions")
  (map->Service desc))

(defrecord Api [on-startup on-request routes])

(defmethod f/literal 'vase/api
  [_ desc]
  (assert (contains? desc :routes)
          "vase/api must have a :routes key with some routes as its value")
  (map->Api desc))

(defmacro function-for-literal
  [s f]
  `(defmethod f/literal ~s
     [_# desc#]
     (~f (with-name desc#))))

(defmacro fn-lits
  [& sf]
  `(do
     ~@(for [[s f] (partition 2 sf)]
         `(function-for-literal ~s ~f))))

(fn-lits

 'vase/respond                    literals/respond
 'vase/redirect                   literals/redirect
 'vase/conform                    literals/conform
 'vase/validate                   literals/validate
; 'vase.datomic/db-from-connection literals/datomic-db-from-connection
; 'vase.datomic/schema-tx          literals/datomic-schema-tx
 'vase.datomic/query              literals/query
 'vase.datomic/transact           literals/transact)

(defmethod f/literal 'vase/attach [_ key val]
  (literals/attach {:key key :val val}))

(defmethod f/literal 'vase.datomic/connection [_ uri]
  (datomic/connect uri))

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
  {s (var-get (resolve s))})

(defn- expose-as-env
  [syms]
  (reduce merge {} (map expose-sym syms)))

(defn- constructed-stock-interceptors
  []
  {'io.pedestal.http.body-params/body-params (body-params/body-params)
   'io.pedestal.http.ring-middlewares/session (ring-middlewares/session)})

(defn stock-interceptors
  []
  (merge
   (expose-as-env stock-interceptor-syms)
   (constructed-stock-interceptors)))

(defn load-from-file
  [filename]
  (-> filename
      (easy/load-environment 'vase/plugins)
      (merge (stock-interceptors))))
