(ns com.cognitect.vase.actions-test
  (:require [clojure.test :refer :all]
            [com.cognitect.vase.test-helper :as helper]
            [io.pedestal.interceptor :as interceptor]
            [com.cognitect.vase.actions :as actions]))

(defn expect-response
  [actual status body headers]
  (is (= status   (:status actual)))
  (is (= body     (:body actual)))
  (is (= headers (select-keys (:headers actual) (keys headers)))))

(defn with-query-params
  ([p]
   (with-query-params {} p))
  ([ctx p]
      (-> ctx
       (update-in [:request :query-params] merge p)
       (update-in [:request :params] merge p))))

(defn execute-and-expect
  ([action status body headers]
   (expect-response (:response (helper/run-interceptor action)) status body headers))
  ([ctx action status body headers]
   (expect-response (:response (helper/run-interceptor ctx action)) status body headers)))

(deftest dynamic-interceptor-creation
  (testing "at least one function is required"
    (is (thrown? AssertionError (actions/dynamic-interceptor :without-functions [] {})))

    (are [x] (interceptor/interceptor? x)
      (actions/dynamic-interceptor :enter-only [] {:enter (fn [ctx] ctx)})
      (actions/dynamic-interceptor :enter-only [] {:enter identity})
      (actions/dynamic-interceptor :leave-only [] {:leave identity})
      (actions/dynamic-interceptor :error-only [] {:error identity}))))

(deftest builtin-actions-are-interceptors
  (are [x] (interceptor/interceptor? (interceptor/-interceptor x))
    (actions/map->RespondAction {})
    (actions/map->RedirectAction {})
    (actions/map->ValidateAction {})
    (actions/map->QueryAction {})
    (actions/map->TransactAction {})))
