(ns vase.actions-test
  (:require [vase.actions :as actions]
            [clojure.test :refer :all]
            [io.pedestal.interceptor :as interceptor]))

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
