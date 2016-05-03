(ns vase.actions-test
  (:require [clojure.test :refer :all]
            [io.pedestal.interceptor :as interceptor]
            [vase.actions :as actions]
            [vase.test-helper :as helper]))

(deftest dynamic-interceptor-creation
  (testing "at least one function is required"
    (is (thrown? AssertionError (actions/dynamic-interceptor :without-functions [] {})))

    (are [x] (interceptor/interceptor? x)
      (actions/dynamic-interceptor :enter-only [] {:enter (fn [ctx] ctx)})
      (actions/dynamic-interceptor :enter-only [] {:enter identity})
      (actions/dynamic-interceptor :leave-only [] {:leave identity})
      (actions/dynamic-interceptor :error-only [] {:error identity}))))

(deftest builtin-actions-are-interceptors
  (are [x] (interceptor/interceptor? x)
    (actions/respond-action  :responder  [] "Body content" 203 {})
    (actions/redirect-action :redirector [] "Body content" 303 {} "https://www.example.com")
    (actions/validate-action :validator  [] {} [])
    (actions/query-action    :query      [] [] [] [] {})
    (actions/transact-action :transact   [] [])))

(defn- expect-response
  [actual status body headers]
  (is (= status   (:status actual)))
  (is (= body     (:body actual)))
  (is (= headers (select-keys (:headers actual) (keys headers)))))

(defn- with-query-params
  [p]
  (-> {}
      (update-in [:request :query-params] merge p)
      (update-in [:request :params] merge p)))

(defn make-respond
  ([params exprs]
   (make-respond params exprs 200))
  ([params exprs status]
   (make-respond params exprs status {}))
  ([params exprs status headers]
   (actions/respond-action :responder params exprs status headers)))

(deftest respond-action
  (testing "static response"
    (are [action status body headers] (expect-response (:response (helper/run-interceptor action)) status body headers)
      (make-respond [] "respond-only" 202)           202 "respond-only" {}
      (make-respond [] "with-header"  201 {"a" "b"}) 201 "with-header"  {"a" "b"}))

  (testing "with parameters"
    (are [expected action ctx] (= expected  (-> ctx (helper/run-interceptor action) :response :body))
      "p1: foo"  (make-respond '[p1]      '(str "p1: " p1))           (with-query-params {:p1 "foo"})
      "p1: &"    (make-respond '[p1]      '(str "p1: " p1))           (with-query-params {:p1 "&"})
      "foo.bar." (make-respond '[p1 zort] '(format "%s.%s." p1 zort)) (with-query-params {:p1 "foo" :zort "bar"}))))
