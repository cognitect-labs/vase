(ns vase.service-route-table
  (:import [java.util UUID])
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.cors :as cors]
            [io.pedestal.http.impl.servlet-interceptor :as servlet-interceptor]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.table :as table]
            [io.pedestal.log :as log]
            [vase]
            [vase.config :as conf]
            [datomic.api :as d]))

(defn- bootstrap! [uri] )

(defn clj-ver
  [request]
  {:status 200 :body (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::clj-ver))})

(defn health-check
  [request]
  {:status 200 :body "alive"})

(def dev-interceptors [cors/dev-allow-origin servlet-interceptor/exception-debug])

(defn make-master-routes
  [spec]
  (table/route-table
   {}
   (concat
    [["/"      :get health-check :route-name ::health-check]
     ["/about" :get clj-ver      :route-name ::clj-ver]]
    (vase/routes "/api" spec :make-interceptors-fn (fn [is] (into dev-interceptors is))))))

(defn service-map
  "Return a new, fully initialized service map"
  []
  (let [test-db    (str "datomic:mem://" (UUID/randomUUID))
        descriptor (vase/load-descriptor "test_descriptor.edn")
        conn       (vase/connect-database test-db)]
    (vase/ensure-schema conn (-> descriptor :example :norms))
    {:env                 :prod
     ::http/routes        (make-master-routes {:descriptor  descriptor
                                               :app-name    :example
                                               :version     :v1
                                               :datomic-uri test-db})
     ::http/resource-path "/public"
     ::http/type          :jetty
     ::http/port          8080}))
