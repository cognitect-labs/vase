(ns com.cognitect.vase.conform-test
  (:require [clojure.test :refer :all]
            [com.cognitect.vase.actions :as actions]
            [com.cognitect.vase.test-helper :as helper]
            [clojure.spec.alpha :as s]
            [io.pedestal.interceptor :as i]))

(s/def ::a string?)
(s/def ::b boolean?)
(s/def ::request-body (s/keys :req-un #{::a} :opt-un #{::b}))

(defn make-conformer
  [from spec to explain-to]
  (i/-interceptor
   (actions/->ConformAction :conformer from spec to explain-to "")))

(deftest conform-action
  (testing "Happy path"
    (is (not= ::s/invalid
              (-> {:query-data {:a 1 :b true}}
                  (helper/run-interceptor (make-conformer :query-data ::request-body :shaped nil))
                  (get :shaped)))))

  (testing "Non-conforming inputs"
    (is (= ::s/invalid
           (-> {:query-data {:a 1 :b "string-not-allowed"}}
               (helper/run-interceptor (make-conformer :query-data ::request-body :shaped nil))
               (get :shaped)))))

  (testing "Explain goes to :com.cognitect.vase.actions/explain-data by default"
    (is (not
          (nil?
            (-> {:query-data {:a 1 :b "string-not-allowed"}}
              (helper/run-interceptor (make-conformer :query-data ::request-body :shaped nil))
              (get :com.cognitect.vase.actions/explain-data))))))


  (testing "The 'from' part can be a vector to get nested data out of the context"
    (is (= ::s/invalid
          (-> {:context {:request {:query-data {:a 1 :b "string-not-allowed"}}}}
            (helper/run-interceptor (make-conformer [:context :request :query-data] ::request-body :shaped nil))
            (get :shaped))))

    (is (= {:path [:b] :pred `boolean? :val "string-not-allowed" :via [::request-body ::b] :in [:b]}
          (-> {:context {:request {:query-data {:a 1 :b "string-not-allowed"}}}}
            (helper/run-interceptor (make-conformer [:context :request :query-data] ::request-body :shaped nil))
            :com.cognitect.vase.actions/explain-data
            :clojure.spec.alpha/problems
            first)))))
