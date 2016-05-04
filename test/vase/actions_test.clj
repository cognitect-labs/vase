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

(defn- execute-and-expect
  ([action status body headers]
   (expect-response (:response (helper/run-interceptor action)) status body headers))
  ([ctx action status body headers]
   (expect-response (:response (helper/run-interceptor ctx action)) status body headers)))

(defn make-respond
  ([params exprs]
   (make-respond params exprs 200))
  ([params exprs status]
   (make-respond params exprs status {}))
  ([params exprs status headers]
   (actions/respond-action :responder params exprs status headers)))

(deftest respond-action
  (testing "static response"
    (are [ status body headers action] (execute-and-expect action status body headers)
      202 "respond-only" {}          (make-respond [] "respond-only" 202)
      201 "with-header"  {"a" "b"}   (make-respond [] "with-header"  201 {"a" "b"})))

  (testing "with parameters"
    (are [expected-body action ctx] (execute-and-expect ctx action 200 expected-body {})
      "p1: foo"  (make-respond '[p1]      '(str "p1: " p1))           (with-query-params {:p1 "foo"})
      "p1: &"    (make-respond '[p1]      '(str "p1: " p1))           (with-query-params {:p1 "&"})
      "foo.bar." (make-respond '[p1 zort] '(format "%s.%s." p1 zort)) (with-query-params {:p1 "foo" :zort "bar"}))))

(defn make-redirect
  ([params status body headers url]
   (actions/redirect-action :redirector params body status headers url)))

(deftest redirect-action
  (testing "static redirect"
    (are [status body headers action] (execute-and-expect action status body headers)
       302 "" {"Location" "http://www.example.com"} (make-redirect [] 302 "" {} "http://www.example.com"))

    (are [headers action ctx] (execute-and-expect ctx action 302 "" headers)
      {"Location" "https://donotreply.com"} (make-redirect '[p1] 302 "" {} 'p1) (with-query-params {:p1 "https://donotreply.com"}))))
