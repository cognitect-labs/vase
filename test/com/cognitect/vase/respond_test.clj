(ns com.cognitect.vase.respond-test
  (:require [clojure.test :refer :all]
            [io.pedestal.interceptor :as interceptor]
            [com.cognitect.vase.actions :as actions]
            [com.cognitect.vase.test-helper :as helper]
            [com.cognitect.vase.actions-test :refer [expect-response with-query-params execute-and-expect]]))

(defn make-respond
  ([params coercions exprs]
   (make-respond params coercions exprs 200))
  ([params coercions exprs status]
   (make-respond params coercions exprs status {}))
  ([params coercions exprs status headers]
   (interceptor/-interceptor
    (actions/->RespondAction :responder params coercions exprs status headers ""))))

(deftest respond-action
  (testing "static response"
    (are [ status body headers action] (execute-and-expect action status body headers)
      202 "respond-only" {}                (make-respond [] [] "respond-only" 202)
      201 "with-header"  {"a" "b"}         (make-respond [] [] "with-header"  201 {"a" "b"})
      200 "two-headers"  {"a" "b" "c" "d"} (make-respond [] [] "two-headers"  200 {"a" "b" "c" "d"})))

  (testing "with parameters"
    (are [expected-body action ctx] (execute-and-expect ctx action 200 expected-body {})
      "p1: foo"  (make-respond '[p1]      [] '(str "p1: " p1))           (with-query-params {:p1 "foo"})
      "p1: &"    (make-respond '[p1]      [] '(str "p1: " p1))           (with-query-params {:p1 "&"})
      "foo.bar." (make-respond '[p1 zort] [] '(format "%s.%s." p1 zort)) (with-query-params {:p1 "foo" :zort "bar"})))

  (testing "with parameters and defaults"
    (are [expected-body action ctx] (execute-and-expect ctx action 200 expected-body {})
      "p1: foobar"  (make-respond '[[p1 "foobar"]]      [] '(str "p1: " p1)) (with-query-params {})
      "p1: & p2: +" (make-respond '[p1 [p2 "+"]]      [] '(str "p1: " p1 " p2: " p2)) (with-query-params {:p1 "&"})
      "foo.bar."    (make-respond '[[p1 "baz"] [zort "bar"]] [] '(format "%s.%s." p1 zort)) (with-query-params {:p1 "foo"})))

  (testing "with parameters and coercions"
    (are [expected-body action ctx] (execute-and-expect ctx action 200 expected-body {})
      "p1's foo: 1" (make-respond '[p1] '[p1] '(str "p1's foo: " (:foo p1))) (with-query-params {:p1 "{:foo 1}"})
      "p1: A"       (make-respond '[p1] '[p1] '(str "p1: " p1))  (with-query-params {:p1 "\"A\""})
      "Sum: 3"      (make-respond '[p1 zort] '[p1 zort] '(str "Sum: " (+ p1 zort))) (with-query-params {:p1 "1" :zort "2"}))))
