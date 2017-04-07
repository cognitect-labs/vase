(ns com.cognitect.vase.conform-test
  (:require [clojure.test :refer :all]
            [com.cognitect.vase.actions :as actions]
            [com.cognitect.vase.test-helper :as helper]
            [clojure.spec :as s]))

(s/def ::a string?)
(s/def ::b boolean?)
(s/def ::request-body (s/keys :req-un #{::a} :opt-un #{::b}))

(defn make-conformer
  [from spec to]
  (actions/conform-action :conformer from spec to nil))

(deftest conform-action
  (testing "Happy path"
    (is (not= :clojure.spec/invalid
              (-> {:query-data {:a 1 :b true}}
                  (helper/run-interceptor (make-conformer :query-data ::request-body :shaped))
                  (get :shaped)))))

  (testing "Non-conforming inputs"
    (is (= :clojure.spec/invalid
           (-> {:query-data {:a 1 :b "string-not-allowed"}}
               (helper/run-interceptor (make-conformer :query-data ::request-body :shaped))
               (get :shaped))))
    (is (not
         (nil?
          (-> {:query-data {:a 1 :b "string-not-allowed"}}
              (helper/run-interceptor (make-conformer :query-data ::request-body :shaped))
              (get :com.cognitect.vase.actions/explain-data)))))))
