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
  [api-root spec]
  (table/table-routes
   {}
   (vase/routes api-root spec)))

(defn routes
  [spec]
  (concat (master-routes)
          (app-routes "/api" spec)))

(defn service
  []
  (let [spec (vase/load-descriptor "petstore-full.edn")
        db-uri (:datomic-uri spec)
        conn (vase.datomic/connect db-uri)]
    (vase.datomic/ensure-schema conn (get-in spec [:descriptor :petstore-full :norms]))
    {:env :prod
     ::http/routes (routes spec)
     ::http/resource-path "/public"
     ::http/type :jetty
     ::http/port 8888}))
