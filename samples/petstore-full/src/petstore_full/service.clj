(ns petstore-full.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition.table :as table]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor :as i]
            [ring.util.response :as ring-resp]
            [vase]
            [vase.datomic]
            [petstore-full.interceptors]))  ;; for descriptors

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

;; A swagger top page will show up by "/"
(defn home-page
  [request]
  (ring-resp/redirect "/index.html"))

;; A route definition of normal page
(defn master-routes
  []
  (table/table-routes
   {}
   [["/" :get [http/log-request http/html-body home-page] :route-name ::home-page]
    ["/about" :get [http/html-body about-page] :route-name ::about-page]]))

;; Vase app specific routes setting
(defn app-routes
  [api-root specs]
  (table/table-routes
   {}
   (vase/routes api-root specs)))

;; Merging ordinary adn Vase routes
(defn routes
  [specs]
  (concat (master-routes)
          (app-routes "/api" specs)))

;; Loading multipl spec files from resources directory
(defn load-specs
  [descriptors]
  (reduce #(conj %1 (vase/load-edn-resource %2)) []  descriptors))

(defn service
  []
  (let [descriptors (:descriptors (vase/load-edn-resource "vase-descriptors.edn"))
        specs (load-specs descriptors)]
    (vase/ensure-schema specs)
    {:env :prod
     ::http/routes (routes specs)
     ::http/resource-path "/public"
     ::http/type :jetty
     ::http/port 8888}))
