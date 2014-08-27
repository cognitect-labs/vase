(ns cr-ocs.descriptor-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [cr-ocs.test-helper :as helper]
            [cr-ocs.util :as util]
            [cr-ocs.db :as cdb]))

(defn response-data
  ([response] (response-data response util/read-json))
  ([response reader]
     (-> response
         :body
         reader
         :response)))

(use-fixtures :each helper/test-with-fresh-db)

(deftest exercise-descriptored-service
  (let [post-response (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "mefogus@gmail.com"}]})
        get-response (helper/GET "/api/example/v1/fogus")]
    (is (= 200 (:status post-response)))
    (is (= 200 (:status get-response)))
    (is (seq (response-data post-response)))
    (is (seq (response-data get-response)))))

(deftest exercise-constant-and-parameter-action
  (let [post1 (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "mefogus@gmail.com"}]})
        post2 (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "paul.degrandis@gmail.com"}]})
        special-get (helper/GET "/api/example/v1/fogus-and-someone?someone=paul.degrandis@gmail.com")]
    (are [x] (= (:status x) 200)
         post1 post2 special-get)
    (is (seq (response-data special-get)))
    (is (= (count (response-data special-get)) 2))))