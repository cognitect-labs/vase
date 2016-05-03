(ns vase.interceptor-test
  (:require [clojure.test :refer :all]
            [vase.test-helper :as helper]
            [vase.interceptor :refer :all]
            [io.pedestal.interceptor.chain :as chain]))

(defn- run-interceptor
  ([i]     (run-interceptor {} i))
  ([ctx i] (chain/execute (chain/enqueue* ctx i))))

(defn- new-ctx
  [& {:as headers}]
  {:request {:headers headers}})

(deftest stock-interceptors
  (testing "attach-received-time"
    (is (some-> (run-interceptor attach-received-time) :request :received-time)))

  (testing "attach-request-id"
    (testing "default request id is supplied"
      (is (some-> (run-interceptor attach-request-id) :request :request-id)))

    (testing "an explicit request id is returned"
      (let [original-id  "1234554321"
            ctx          (new-ctx "vaserequest-id" "1234554321")
            resulting-id (some-> (run-interceptor ctx attach-request-id) :request :request-id)]
        (is (= original-id resulting-id)))))

  (testing "forward-headers"
    (let [fh              (forward-headers ["vaserequest-id" "custom-header"])
          ctx             (new-ctx "vaserequest-id" "1234554321" "custom-header" "Any string value")
          result          (run-interceptor ctx fh)]
      (is (= "1234554321"       (some-> result :response :headers (get "vaserequest-id"))))
      (is (= "Any string value" (some-> result :response :headers (get "custom-header")))))))
