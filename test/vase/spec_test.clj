(ns vase.spec-test
  (:require [vase.descriptor]
            [vase]
            [vase.service-route-table :as srt]
            [clojure.spec :as s]
            [clojure.test :refer :all]
            [io.pedestal.interceptor :as interceptor]
            [clojure.string :as str]))

(deftest route-table-spec-tests
  (let [handler-fn (fn [req] {:status 200 :body "foo"})]
    (is (s/valid? :vase.descriptor/route-table
                  [["/a" :get handler-fn]
                   ["/b" :get [(interceptor/interceptor {:enter handler-fn})]]
                   ["/c" :get handler-fn :route-name :c]
                   ["/d/:id" :get handler-fn :route-name :d :constraints {:id #"[0-9]+"}]]))
    (testing "invalid routes"
      (are [v] (not (s/valid? :vase.descriptor/route-table-route v))
        []
        ["/a"]
        ["/a" :get]
        ["/a" :get handler-fn :route-name]
        ["/a" :get handler-fn :not-route-name-k :bar]
        ["/a" :get handler-fn :route-name :bar :constraints]
        ["/a" :get handler-fn :route-name :bar :constraints 1]))))

(def test-spec (srt/test-spec))

(deftest vase-spec-tests
  (testing "full vase spec"
    (is (s/valid? :vase.descriptor/description test-spec))))

(use-fixtures :once (fn [f]
                      (s/instrument #'vase/routes)
                      (f)
                      (s/unstrument #'vase/routes)))

(deftest vase-routes-fn-tests
  (is (vase/routes "/api" []))
  (is (vase/routes "/api" [test-spec]))
  (is (vase/routes "/api" [test-spec test-spec]))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"did not conform" (vase/routes "" test-spec)))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"did not conform" (vase/routes :not-a-path test-spec)))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"did not conform" (vase/routes "/api" test-spec))))
