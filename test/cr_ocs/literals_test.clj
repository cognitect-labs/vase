(ns cr-ocs.literals-test
  (:require [clojure.test :refer :all]
            [cr-ocs.literals :as lit]
            [io.pedestal.test :refer :all]
            [cr-ocs.test-helper :as helper]))

(deftest test-data-massging
  "This test ensures that data received can be transformed into a form
   ammenble to storing into datomic."

  (is (= (lit/massage-data '({:db/id 1}))
         [{:db/id 1}]))

  (is (= (:part (:db/id (first (lit/massage-data '({})))))
         :db.part/user))

  (is (= (lit/massage-data '({:db/id ["user/age" 42]} ))
         [{:db/id [:user/age 42]}])))


