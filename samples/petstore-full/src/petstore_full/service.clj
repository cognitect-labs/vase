(ns petstore-full.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition.table :as table]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [vase]
            [vase.datomic]))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

(defn home-page
  [request]
  (ring-resp/redirect "/index.html"))

(def common-interceptors [(body-params/body-params) http/html-body])

(defn master-routes
  []
  (table/table-routes
   {}
   [["/" :get [http/html-body home-page] :route-name ::home-page]
    ["/about" :get [http/html-body about-page] :route-name ::about-page]]))

(defn app-routes
  [api-root specs]
  (table/table-routes
   {}
   (vase/routes api-root specs)))

(defn routes
  [specs]
  (concat (master-routes)
          (app-routes "/api" specs)))

(defn load-specs
  [descriptors]
  (reduce #(conj %1 (vase/load-edn-resource %2)) []  descriptors))

(defn ensure-schemas
  [specs]
  (doall (map #(vase/ensure-schema %) specs)))

(defn service
  []
  (let [descriptors (:descriptors (vase/load-edn-resource "vase-descriptors.edn"))
        specs (load-specs descriptors)]
    (ensure-schemas specs)
    {:env :prod
     ::http/routes (routes specs)
     ::http/resource-path "/public"
     ::http/type :jetty
     ::http/port 8888}))
