(ns omnext-todo.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as http]
            [omnext-todo.test-helper :as helper]
            [omnext-todo.service :as service]))

;; To test your service, call `(helper/service` to get a new service instance.
;; If you need a constant service over multiple calls, use `(helper/with-service ...)
;; All generated services will have randomized, consistent in-memory Datomic DBs
;; if required by the service
;;
;; `helper` also contains shorthands for common `response-for` patterns,
;; like GET, POST, post-json, post-edn, and others

(deftest home-page-test
  (is (= (:body (response-for (helper/service) :get "/"))
         "Hello World!"))
  (is (= (:headers (helper/GET "/"))
         {"Content-Type" "text/html;charset=UTF-8"
          "Strict-Transport-Security" "max-age=31536000; includeSubdomains"
          "X-Frame-Options" "DENY"
          "X-Content-Type-Options" "nosniff"
          "X-XSS-Protection" "1; mode=block"
          "X-Download-Options" "noopen"
          "X-Permitted-Cross-Domain-Policies" "none"
          "Content-Security-Policy" "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"})))


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
            "Content-Security-Policy" "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;"}))))

;; Let's test the API from our descriptor
(deftest todos-CRUD-test
  (helper/with-service service/service
    (let [initial-list (helper/response-data (helper/GET "/api/omnext-todo/main/todos"))
          expected-todo {:todo/title "Write a test for TODO example"
                         :todo/category {:db/ident "todo.category/personal"}}
          tx-resp (helper/post-json "/api/omnext-todo/main/todos" {:payload [{:todo/title "Write a test for TODO example"}]})
          new-list (helper/response-data (helper/GET "/api/omnext-todo/main/todos"))
          new-todo (first new-list)]
      (is (empty? initial-list))
      (is (= 1 (count new-list)))
      (is (:db/id new-todo))
      (is (= expected-todo
             (dissoc new-todo :db/id :todo/created))))))


