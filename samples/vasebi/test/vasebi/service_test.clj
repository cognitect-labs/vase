(ns vasebi.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as http]
            [vasebi.test-helper :as helper]
            [vasebi.service :as service]))

;; To test your service, call `(helper/service` to get a new service instance.
;; If you need a constant service over multiple calls, use `(helper/with-service ...)
;; All generated services will have randomized, consistent in-memory Datomic DBs
;; if required by the service
;;
;; `helper` also contains shorthands for common `response-for` patterns,
;; like GET, POST, post-json, post-edn, and others

(deftest about-page-test
  (helper/with-service service/service
    (is (.contains (:body (response-for (helper/service) :get "/about"))
                   "Clojure 1.9"))
    (is (= (:headers (helper/GET "/about"))
           {"Content-Type" "text/html;charset=UTF-8"
            "Strict-Transport-Security" "max-age=31536000; includeSubdomains"
            "X-Frame-Options" "DENY"
            "X-Content-Type-Options" "nosniff"
            "X-XSS-Protection" "1; mode=block"
            "X-Download-Options" "noopen"
            "X-Permitted-Cross-Domain-Policies" "none"
            "Content-Security-Policy" "object-src none"}))))

