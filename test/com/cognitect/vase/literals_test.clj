(ns com.cognitect.vase.literals-test
  (:require [clojure.test :refer :all]
            [com.cognitect.vase.literals :as lit]
            [io.pedestal.test :refer :all]
            [com.cognitect.vase.test-helper :as helper]
            [com.cognitect.vase.actions :as actions]
            [clojure.string :as string]))

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
                              {:age 31
                               :name "paul"})]
    (is (= (:status response) 200))
    (is (empty? (helper/response-data response)))))

(defn- reader-literal [a]
  (first (string/split (pr-str a) #"\{")))

(deftest printing-literals
  (testing "actions print as reader literals"
    (are [word action] (= word (reader-literal action))
      "#vase/respond"  (lit/respond  {:name :responder})
      "#vase/redirect" (lit/redirect {:name :redirect :url ""})
      "#vase/validate" (lit/validate {:name :validate})
      "#vase/query"    (lit/query    {:name :query :query []})
      "#vase/transact" (lit/transact {:name :transact})))

  (testing "actions round-trip through the reader"
    (are [action] (= action (read-string (pr-str action)))
      (lit/respond  {:name :responder :bar :baz :extra "stuff"})
      (lit/redirect {:name :redirect :url ""})
      (lit/validate {:name :validate})
      (lit/query    {:name :query :query []})
      (lit/transact {:name :transact}))))
