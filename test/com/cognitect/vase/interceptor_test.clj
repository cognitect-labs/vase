(ns com.cognitect.vase.interceptor-test
  (:require [clojure.test :refer :all]
            [com.cognitect.vase.test-helper :as helper :refer [run-interceptor new-ctx]]
            [com.cognitect.vase.interceptor :refer :all]))

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
