(ns com.cognitect.vase.query-test
  (:require [clojure.test :refer :all]
            [io.pedestal.interceptor :as interceptor]
            [com.cognitect.vase.actions :as actions]
            [com.cognitect.vase.test-helper :as helper]
            [com.cognitect.vase.test-db-helper :as db-helper]
            [clojure.spec.alpha :as s]
            [datomic.api :as d]))

(defn empty-db-entity-count
  []
  (let [conn (:connection (db-helper/new-database []))
        db   (d/db conn)]
    (count
     (d/q '[:find ?e ?v :where [?e :db/ident ?v]] db))))

(defn make-query
  [query variables coercions constants to]
  (interceptor/-interceptor
   (actions/->QueryAction :query variables query coercions constants {} to "")))

(defn- context-with-db []
  (let [conn (db-helper/connection)]
    (-> (helper/new-ctx)
        (assoc-in [:request :db] (d/db conn))
        (assoc-in [:request :conn] conn))))

(defn- with-path-params
  ([p]
   (with-path-params {} p))
  ([ctx p]
      (-> ctx
       (update-in [:request :path-params] merge p)
       (update-in [:request :params] merge p))))

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
        (is (< 0 (count query-results)))))

    (testing "with scalar result"
      (let [action        (make-query '[:find ?e . :in $ ?id :where [?e :user/userId ?id]] '[id] '[id] [] nil)
            response      (:response (helper/run-interceptor
                                      (with-path-params
                                        (context-with-db)
                                        {:id "100"})
                                      action))
            query-result (:body response)]
        (is (number? query-result))))

    (testing "with nil params"
      (let [action        (make-query '[:find ?e :in $ ?id :where [?e :user/userId ?id]] '[id] '[id] [] nil)
            response      (:response (helper/run-interceptor
                                      (with-path-params
                                        (context-with-db)
                                        {})
                                      action))
            query-results (:body response)]
        (is (string? query-results))
        (is (re-matches #"Missing required query parameters.*" query-results))))

    (testing "scalar query with no results"
      (let [action        (make-query '[:find ?e . :in $ ?id :where [?e :user/userId ?id]] '[id] '[id] [] nil)
            response      (:response (helper/run-interceptor
                                      (with-path-params
                                        (context-with-db)
                                        {:id 999})
                                      action))
            query-results (:body response)]
        (is (= nil (read-string {:eof nil} query-results)))))))
