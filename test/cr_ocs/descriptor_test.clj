(ns cr-ocs.descriptor-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [cr-ocs.test-helper :as helper]
            [cr-ocs.db :as cdb]))

(use-fixtures :each helper/test-with-fresh-db)

;; Tests that require fresh DBs should go in here

(deftest exercise-descriptored-service
  (let [post-response (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "mefogus@gmail.com"}]})
        get-response (helper/GET "/api/example/v1/fogus")]
    (is (= 200 (:status post-response)))
    (is (= 200 (:status get-response)))
    (is (seq (helper/response-data post-response)))
    (is (seq (helper/response-data get-response)))))

(deftest exercise-constant-and-parameter-action
  (let [post1 (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "mefogus@gmail.com"}]})
        post2 (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "paul.degrandis@gmail.com"}]})
        special-get (helper/GET "/api/example/v1/fogus-and-someone?someone=paul.degrandis@gmail.com")]
    (are [x] (= (:status x) 200)
         post1 post2 special-get)
    (is (seq (helper/response-data special-get)))
    (is (= (count (helper/response-data special-get)) 2))))