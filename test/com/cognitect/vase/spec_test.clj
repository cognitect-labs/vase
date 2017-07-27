(ns com.cognitect.vase.spec-test
  (:require [com.cognitect.vase.spec :as vase.spec]
            [com.cognitect.vase :as vase]
            [com.cognitect.vase.service-route-table :as srt]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer :all]
            [io.pedestal.interceptor :as interceptor]))

(deftest route-table-spec-tests
  (let [handler-fn (fn [req] {:status 200 :body "foo"})]
    (is (s/valid? ::vase.spec/route-table
                  [["/a" :get handler-fn]
                   ["/b" :get [(interceptor/interceptor {:enter handler-fn})]]
                   ["/c" :get handler-fn :route-name :c]
                   ["/d/:id" :get handler-fn :route-name :d :constraints {:id #"[0-9]+"}]]))
    (testing "invalid routes"
      (are [v] (not (s/valid? ::vase.spec/route-table-route v))
        []
        ["/a"]
        ["/a" :get]
        ["/a" :get handler-fn :route-name]
        ["/a" :get handler-fn :not-route-name-k :bar]
        ["/a" :get handler-fn :route-name :bar :constraints]
        ["/a" :get handler-fn :route-name :bar :constraints 1]))))

(def test-spec (srt/test-spec))
(def sample-spec (vase/load-edn-resource "sample_payload.edn"))

(deftest vase-spec-tests
  (testing "full vase spec"
    (doseq [vspec [test-spec sample-spec]]
      (is (s/valid? ::vase.spec/spec vspec))))

  (testing "descriptors"
    (doseq [d ["sample_descriptor.edn"
               "small_descriptor.edn"]]
      (is (s/valid? ::vase.spec/descriptor (vase/load-edn-resource d))
          (format "%s is not valid!" d)))))

(use-fixtures :once (fn [f]
                      (stest/instrument `vase/routes)
                      (f)
                      (stest/unstrument `vase/routes)))

(deftest vase-routes-fn-tests
  (is (vase/routes "/api" test-spec))
  (is (vase/routes "/api" []))
  (is (vase/routes "/api" [test-spec]))
  (is (vase/routes "/api" [test-spec sample-spec]))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"did not conform" (vase/routes "" test-spec)))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"did not conform" (vase/routes :not-a-path test-spec)))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"did not conform" (vase/routes "/api" (:descriptor test-spec)))))
