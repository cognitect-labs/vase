(ns com.cognitect.vase.actions-test
  (:require [clojure.test :refer :all]
            [io.pedestal.interceptor :as interceptor]
            [com.cognitect.vase.actions :as actions]
            [com.cognitect.vase.test-helper :as helper]
            [com.cognitect.vase.test-db-helper :as db-helper]
            [clojure.spec :as s]
            [datomic.api :as d]))

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
    (actions/respond-action  :responder  [] [] "Body content" 203 {})
    (actions/redirect-action :redirector [] "Body content" 303 {} "https://www.example.com")
    (actions/validate-action :validator  [] {} [])
    (actions/query-action    :query      [] [] [] [] {} nil)
    (actions/transact-action :transact   [] :vase/assert-entity [] nil)))

(defn- expect-response
  [actual status body headers]
  (is (= status   (:status actual)))
  (is (= body     (:body actual)))
  (is (= headers (select-keys (:headers actual) (keys headers)))))

(defn- with-query-params
  ([p]
   (with-query-params {} p))
  ([ctx p]
      (-> ctx
       (update-in [:request :query-params] merge p)
       (update-in [:request :params] merge p))))

(defn- with-path-params
  ([p]
   (with-path-params {} p))
  ([ctx p]
      (-> ctx
       (update-in [:request :path-params] merge p)
       (update-in [:request :params] merge p))))

(defn- execute-and-expect
  ([action status body headers]
   (expect-response (:response (helper/run-interceptor action)) status body headers))
  ([ctx action status body headers]
   (expect-response (:response (helper/run-interceptor ctx action)) status body headers)))

(defn make-respond
  ([params coercions exprs]
   (make-respond params coercions exprs 200))
  ([params coercions exprs status]
   (make-respond params coercions exprs status {}))
  ([params coercions exprs status headers]
   (actions/respond-action :responder params coercions exprs status headers)))

(deftest respond-action
  (testing "static response"
    (are [ status body headers action] (execute-and-expect action status body headers)
      202 "respond-only" {}          (make-respond [] [] "respond-only" 202)
      201 "with-header"  {"a" "b"}   (make-respond [] [] "with-header"  201 {"a" "b"})))

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

(defn make-redirect
  ([params status body headers url]
   (actions/redirect-action :redirector params body status headers url)))

(deftest redirect-action
  (testing "static redirect"
    (are [status body headers action] (execute-and-expect action status body headers)
       302 "" {"Location" "http://www.example.com"} (make-redirect [] 302 "" {} "http://www.example.com"))

    (are [headers action ctx] (execute-and-expect ctx action 302 "" headers)
      {"Location" "https://donotreply.com"} (make-redirect '[p1] 302 "" {} 'p1) (with-query-params {:p1 "https://donotreply.com"}))))


(defn make-validate
  [spec]
  (actions/validate-action :validator [] {} spec))

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

(defn make-conformer
  [from spec to]
  (actions/conform-action :conformer from spec to nil))

(deftest conform-action
  (testing "Happy path"
    (is (not= :clojure.spec/invalid
              (-> {:query-data {:a 1 :b true}}
                  (helper/run-interceptor (make-conformer :query-data ::request-body :shaped))
                  (get :shaped)))))

  (testing "Non-conforming inputs"
    (is (= :clojure.spec/invalid
           (-> {:query-data {:a 1 :b "string-not-allowed"}}
               (helper/run-interceptor (make-conformer :query-data ::request-body :shaped))
               (get :shaped))))
    (is (not
         (nil?
          (-> {:query-data {:a 1 :b "string-not-allowed"}}
              (helper/run-interceptor (make-conformer :query-data ::request-body :shaped))
              (get :com.cognitect.vase.actions/explain-data)))))))

(defn empty-db-entity-count
  []
  (let [conn (:connection (db-helper/new-database []))
        db   (d/db conn)]
    (count
     (d/q '[:find ?e ?v :where [?e :db/ident ?v]] db))))

(defn make-query
  [query variables coercions constants to]
  (actions/query-action :query query variables coercions constants {} to))

(defn- context-with-db []
  (let [conn (db-helper/connection)]
    (-> (helper/new-ctx)
        (assoc-in [:request :db] (d/db conn))
        (assoc-in [:request :conn] conn))))

(deftest query-action
  (db-helper/with-database db-helper/query-test-txes
    (testing "Simple query, no parameters"
      (let [action        (make-query '[:find ?e ?v :where [?e :db/ident ?v]] [] [] [] nil)
            response      (:response (helper/run-interceptor (context-with-db) action))
            query-results (:body response)]
        (is (vector? query-results))
        (is (< (empty-db-entity-count) (count query-results)))
        (is (= '(2) (distinct (map count query-results))))))


    (testing "with one coerced query parameter"
      (let [action        (make-query '[:find ?id ?email :in $ ?id :where [?e :user/userId ?id] [?e :user/userEmail ?email]] '[id] '[id] [] nil)
            response      (:response (helper/run-interceptor
                                      (with-path-params
                                        (context-with-db)
                                        {:id "100"})
                                      action))
            query-results (:body response)]
        (is (vector? query-results))
        (is (< 0 (count query-results)))))

    (testing "with two coerced query parameters"
      (let [action        (make-query '[:find ?id ?email :in $ ?id :where [?e :user/userId ?id] [?e :user/userEmail ?email]] '[id email] '[email id] [] nil)
            response      (:response (helper/run-interceptor
                                      (with-path-params
                                        (context-with-db)
                                        {:id    "100"
                                         :email "paul@cognitect.com"})
                                      action))
            query-results (:body response)]
        (is (vector? query-results))
        (is (< 0 (count query-results)))))))
