(ns vase.literals-test
  (:require [clojure.test :refer :all]
            [vase.literals :as lit]
            [io.pedestal.test :refer :all]
            [vase.test-helper :as helper]
            [vase.actions :as actions]))

(deftest test-data-massging
  "This test ensures that data received can be transformed into a form
   amenable to storing into datomic."

  (is (= (actions/process-id {:db/id 1})
         {:db/id 1}))

  (is (= (:part (:db/id (actions/process-id {})))
         :db.part/user))

  (is (= (actions/process-id {:db/id ["user/age" 42]})
         {:db/id [:user/age 42]})))

(deftest respond-literal
  (is (=
       (:body (helper/GET "/api/example/v1/hello"))
       "Hello World")))

(deftest validate-literal
  (let [response (helper/post-json "/api/example/v1/validate"
                              {:age 22
                               :name "paul"})]
    (is (= (:status response) 200))
    (is (empty? (helper/response-data response)))))
