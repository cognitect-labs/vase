(ns vase.service-route-table
  (:import [java.util UUID])
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.table :as table]
            [io.pedestal.log :as log]
            [ring.util.response :as ring-resp]
            [vase.interceptor :as interceptor]
            [vase]
            ;[vase.util :as util]
            [vase.config :as conf]))

(defn unique-config
     "Returns a unique Vase config map"
     []
     {:db-uri (str "datomic:mem://" (UUID/randomUUID))
      :service-port 8080
      :http-app-root "/"
      :enable-upsert true
      :http-upsert true
      :transact-upsert true
      :initial-descriptor "test_descriptor.edn"
      :initial-version [:example :v1]
      :test-trials 36})

(defn clj-ver
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::clj-ver))))
(defn health-check
  [request]
  (ring-resp/response "alive"))

(def common-interceptors
  [io.pedestal.http.impl.servlet-interceptor/exception-debug
   interceptor/attach-received-time
   interceptor/attach-request-id
   http/html-body])

(defn make-master-routes
  [spec]
  (table/route-table
   {}
   (concat
    [["/"      :get (conj common-interceptors health-check) :route-name ::health-check]
     ["/about" :get (conj common-interceptors clj-ver)      :route-name ::clj-ver]]
    (vase/routes "/api" spec :make-interceptors-fn #(into common-interceptors %)))))

(defn service-map
  "Return a new, fully initialized service map"
  []
  (let [config       (unique-config)
        vase-context (atom (vase/map->Context {:config config}))
        descriptor   (vase/load-descriptor (:initial-descriptor config))
        spec         {:descriptor descriptor :app-name :example :version :v1}]
    #_(vase/bootstrap-vase-context! vase-context (make-master-routes vase-context))
    {:env          :prod
     :vase/context vase-context ;; For testing, shouldn't need this otherwise
     ::http/routes (make-master-routes spec)
     ::http/resource-path "/public"
     ::http/type :jetty
     ::http/port (conf/get-key config :service-port)}))
