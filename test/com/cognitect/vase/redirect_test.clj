(ns com.cognitect.vase.redirect-test
  (:require [clojure.test :refer :all]
            [io.pedestal.interceptor :as interceptor]
            [com.cognitect.vase.actions :as actions]
            [com.cognitect.vase.test-helper :as helper]
            [com.cognitect.vase.test-db-helper :as db-helper]
            [com.cognitect.vase.actions-test :refer [expect-response with-query-params execute-and-expect]]
            [clojure.spec.alpha :as s]
            [datomic.api :as d]))

(defn make-redirect
  ([params status body headers url]
   (interceptor/-interceptor
    (actions/->RedirectAction :redirector params body status headers url))))

(deftest redirect-action
  (testing "static redirect"
    (are [status body headers action] (execute-and-expect action status body headers)
       302 "" {"Location" "http://www.example.com"} (make-redirect [] 302 "" {} "http://www.example.com"))

    (are [headers action ctx] (execute-and-expect ctx action 302 "" headers)
      {"Location" "https://donotreply.com"} (make-redirect '[p1] 302 "" {} 'p1) (with-query-params {:p1 "https://donotreply.com"}))))
