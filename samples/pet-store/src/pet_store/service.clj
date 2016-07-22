(ns pet-store.service
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

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])

(defn master-routes
  []
  (table/table-routes
   {}
   [["/" :get [http/html-body home-page] :route-name ::home-page]
    ["/about" :get [http/html-body about-page] :route-name ::about-page]]))

(defn app-routes
  [api-root spec]
  (table/table-routes
   {}
   (vase/routes api-root spec)))

(defn routes
  [spec]
  (concat (master-routes)
          (app-routes "/api" spec)))

;; Consumed by pet-store.server/create-server
;; See http/default-interceptors for additional options you can configure
(defn service
  []
  (let [spec (vase/load-edn-resource "petstore-simple.edn")]
    (vase/ensure-schema spec)
    {:env :prod
     ::http/routes (routes spec)
     ::http/resource-path "/public"
     ::http/type :jetty
     ::http/port 8080}))
