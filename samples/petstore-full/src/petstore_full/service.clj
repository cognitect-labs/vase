(ns petstore-full.service
  (:require [clojure.instant :as instant]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition.table :as table]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor :as i]
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

(def date-conversion
  (i/interceptor
   {:name ::my-interceptor
    :enter (fn [context]
             (let [payloads (get-in context [:request :json-params :payload])
                   payloads (map (fn [m] (if (:petstore.order/shipDate m)
                                          (update m :petstore.order/shipDate instant/read-instant-date)
                                          m))
                                 payloads)]
               (assoc-in context [:request :json-params :payload] payloads)))}))

(defn master-routes
  []
  (table/table-routes
   {}
   [["/" :get [http/log-request http/html-body home-page] :route-name ::home-page]
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
