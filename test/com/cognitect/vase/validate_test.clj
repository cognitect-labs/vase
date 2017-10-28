(ns com.cognitect.vase.validate-test
  (:require [clojure.test :refer :all]
            [io.pedestal.interceptor :as interceptor]
            [com.cognitect.vase.actions :as actions]
            [com.cognitect.vase.test-helper :as helper]
            [com.cognitect.vase.test-db-helper :as db-helper]
            [com.cognitect.vase.actions-test :refer [expect-response with-query-params execute-and-expect]]
            [clojure.spec.alpha :as s]))

(defn make-validate
  [spec]
  (interceptor/-interceptor
   (actions/->ValidateAction :validator [] {} spec nil "")))

(defn- with-body [body]
  (assoc-in (helper/new-ctx) [:request :edn-params] body))

(s/def ::a string?)
(s/def ::b boolean?)
(s/def ::request-body (s/keys :req-un #{::a} :opt-un #{::b}))

(deftest validate-action
  (testing "Passing validation"
    (are [body-out body-in action] (execute-and-expect (with-body body-in) action 200 body-out {})
      '() {:a "one"}          (make-validate `(s/keys :req-un #{::a} :opt-un #{::b}))
      '() {:a "one" :b false} (make-validate `(s/keys :req-un #{::a} :opt-un #{::b}))
      '() {:a "one" :b false} (make-validate `::request-body)))

  (testing "Failing validation"
    (are [body-out body-in action] (execute-and-expect (with-body body-in) action 200 body-out {})
      '({:path []   :val {}           :via []                   :in []})   {}           (make-validate `(s/keys :req-un #{::a} :opt-un #{::b}))
      '({:path []   :val {:b 12345}   :via []                   :in []}
        {:path [:b] :val 12345        :via [::b]                :in [:b]}) {:b 12345}   (make-validate `(s/keys :req-un #{::a} :opt-un #{::b}))
      '({:path []   :val {:b "false"} :via [::request-body]     :in []}
        {:path [:b] :val "false"      :via [::request-body ::b] :in [:b]}) {:b "false"} (make-validate `::request-body))))
