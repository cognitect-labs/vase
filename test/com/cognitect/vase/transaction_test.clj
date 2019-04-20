(ns com.cognitect.vase.transaction-test
  (:require [clojure.test :refer :all]
            [io.pedestal.interceptor :as interceptor]
            [com.cognitect.vase.actions :as actions]
            [com.cognitect.vase.test-helper :as helper]
            [com.cognitect.vase.test-db-helper :as db-helper]
            [clojure.spec.alpha :as s]
            [datomic.api :as d]
            [io.pedestal.interceptor.chain :as chain]))

(defn empty-db-entity-count
  []
  (let [conn (:connection (db-helper/new-database []))
        db   (d/db conn)]
    (count
     (d/q '[:find ?e ?v :where [?e :db/ident ?v]] db))))

(defn make-transaction
  ([properties]
   (interceptor/-interceptor
    (actions/->TransactAction :transact properties nil nil nil "")))
  ([properties db-op headers to]
   (interceptor/-interceptor
    (actions/->TransactAction :transact properties db-op headers to ""))))

(defn- context-with-db []
  (let [conn (db-helper/connection)]
    (-> (helper/new-ctx)
        (assoc-in [:request :db] (d/db conn))
        (assoc-in [:request :conn] conn))))

(defn- with-json-payload
  ([p]
   (with-json-payload {} p))
  ([ctx p]
   (-> ctx
       (update-in [:request :json-params] merge {:payload p}))))

(defn- found?
  [e coll]
  (not (empty? (filter #(= e %) coll))))

(deftest test-single-entity
  (db-helper/with-database db-helper/query-test-txes
    (let [action         (make-transaction [:company/name])
          response       (:response (helper/run-interceptor
                                     (with-json-payload
                                       (context-with-db)
                                       [{:company/name "Acme, Inc."}])
                                     action))
          txdata-results (:body response)]
      (is (map? txdata-results))
      (is (found? {:company/name "Acme, Inc."} (:whitelist txdata-results))))))

(deftest test-multiple-entities
  (db-helper/with-database db-helper/query-test-txes
    (let [action         (make-transaction [:company/name])
          response       (:response (helper/run-interceptor
                                     (with-json-payload
                                       (context-with-db)
                                       [{:company/name "Acme, Inc."}
                                        {:company/name "Tyrell Corp"}
                                        {:company/name "Jupiter Mining Company"}])
                                     action))
          txdata-results (:body response)]
      (is (map? txdata-results))
      (is (found? {:company/name "Acme, Inc."} (:whitelist txdata-results)))
      (is (found? {:company/name "Jupiter Mining Company"} (:whitelist txdata-results))))))

(deftest test-explicit-db-op
  (db-helper/with-database db-helper/query-test-txes
    (let [action         (make-transaction [:company/name] :vase/assert-entity nil nil)
          response       (:response (helper/run-interceptor
                                     (with-json-payload
                                       (context-with-db)
                                       [{:company/name "Weyland-Yutani"}])
                                     action))
          txdata-results (:body response)]
      (is (map? txdata-results))
      (is (found? {:company/name "Weyland-Yutani"} (:whitelist txdata-results))))))

(deftest test-multiple-properties
  (db-helper/with-database db-helper/query-test-txes
    (let [action         (make-transaction [:user/userEmail :user/userBio])
          response       (:response (helper/run-interceptor
                                     (with-json-payload
                                       (context-with-db)
                                       [{:user/userEmail "peter.weyland@wy.com"
                                         :user/userBio   "Founder, Visionary, Madman"}
                                        {:user/userEmail "david8@wy.com"
                                         :user/userBio   "Potentially psychotic synthetic"}])
                                     action))
          txdata-results (:body response)]
      (is (map? txdata-results))
      (is (found? {:user/userEmail "peter.weyland@wy.com"
                   :user/userBio   "Founder, Visionary, Madman"}
                  (:whitelist txdata-results)))
      (is (found? {:user/userEmail "david8@wy.com"
                   :user/userBio   "Potentially psychotic synthetic"}
                  (:whitelist txdata-results))))))

(deftest test-ignore-payload-not-in-whitelist
  (db-helper/with-database db-helper/query-test-txes
    (let [action         (make-transaction [:user/userEmail])
          response       (:response (helper/run-interceptor
                                     (with-json-payload
                                       (context-with-db)
                                       [{:user/userEmail "peter.weyland@wy.com"
                                         :user/userBio   "Founder, Visionary, Madman"
                                         :user/userId    1}
                                        {:user/userEmail "david8@wy.com"
                                         :user/userBio   "Potentially psychotic synthetic"
                                         :user/userId    8}])
                                     action))
          txdata-results (:body response)]
      (is (map? txdata-results))
      (is (found? {:user/userEmail "peter.weyland@wy.com"}
                  (:whitelist txdata-results)))
      (is (found? {:user/userEmail "david8@wy.com"}
                  (:whitelist txdata-results))))))

(deftest test-payload-with-nils
  (db-helper/with-database db-helper/query-test-txes
    (let [action         (make-transaction [:user/userEmail :user/userBio])
          response       (:response (helper/run-interceptor
                                     (with-json-payload
                                       (context-with-db)
                                       [{:user/userEmail "peter.weyland@wy.com"
                                         :user/userBio   nil}
                                        {:user/userEmail nil}])
                                     action))
          txdata-results (:body response)]
      (is (map? txdata-results))
      (is (= 2 (count (:whitelist txdata-results))))
      (is (found? {:user/userEmail "peter.weyland@wy.com"}
                  (:whitelist txdata-results)))
      (is (found? {}
                  (:whitelist txdata-results))))))

(deftest test-payload-with-booleans
  (db-helper/with-database db-helper/query-test-txes
    (let [action         (make-transaction [:user/userEmail :user/userActive?])
          response       (:response (helper/run-interceptor
                                     (with-json-payload
                                       (context-with-db)
                                       [{:user/userEmail "peter.weyland@wy.com"
                                         :user/userActive? false}
                                        {:user/userEmail   "david8@wy.com"
                                         :user/userActive? true}])
                                     action))
          txdata-results (:body response)]
      (is (map? txdata-results))
      (is (= 2 (count (:whitelist txdata-results))))
      (is (found? {:user/userEmail   "peter.weyland@wy.com"
                   :user/userActive? false}
                  (:whitelist txdata-results)))
      (is (found? {:user/userEmail   "david8@wy.com"
                   :user/userActive? true}
                  (:whitelist txdata-results))))))

(deftest test-headers-pass-through
  (db-helper/with-database db-helper/query-test-txes
    (let [action         (make-transaction [:user/userEmail] nil {"Extra-Header" "passed"} nil)
          response       (:response (helper/run-interceptor
                                     (with-json-payload
                                       (context-with-db)
                                       [{:user/userEmail "peter.weyland@wy.com"
                                         :user/userBio   "Founder, Visionary, Madman"
                                         :user/userId    1}
                                        {:user/userEmail "david8@wy.com"
                                         :user/userBio   "Potentially psychotic synthetic"
                                         :user/userId    8}])
                                     action))]
      (is (= {"Extra-Header" "passed"} (:headers response))))))

(deftest test-txdata-on-context
  (testing "defaults to :com.cognitect.vase.actions/transact-data"
    (db-helper/with-database db-helper/query-test-txes
      (let [transaction (make-transaction [:user/userEmail])
            phony       (interceptor/interceptor {:name :phony :enter identity})
            context     (-> (context-with-db)
                            (with-json-payload
                              [{:user/userEmail "peter.weyland@wy.com"
                                :user/userBio   "Founder, Visionary, Madman"
                                :user/userId    1}
                               {:user/userEmail "david8@wy.com"
                                :user/userBio   "Potentially psychotic synthetic"
                                :user/userId    8}])
                            (chain/enqueue* transaction)
                            (chain/enqueue* phony))
            context     (chain/execute context)
            txdata      (:com.cognitect.vase.actions/transact-data context)]
        (is (not (nil? txdata)))
        (is (contains? txdata :whitelist))
        (is (contains? txdata :transaction)))))

  (testing "overrides with custom key"
    (db-helper/with-database db-helper/query-test-txes
      (let [transaction (make-transaction [:user/userEmail] nil nil :special-context-key)
            phony       (interceptor/interceptor {:name :phony :enter identity})
            context     (-> (context-with-db)
                            (with-json-payload
                              [{:user/userEmail "peter.weyland@wy.com"
                                :user/userBio   "Founder, Visionary, Madman"
                                :user/userId    1}
                               {:user/userEmail "david8@wy.com"
                                :user/userBio   "Potentially psychotic synthetic"
                                :user/userId    8}])
                            (chain/enqueue* transaction)
                            (chain/enqueue* phony))
            context     (chain/execute context)
            txdata      (:special-context-key context)]
        (is (not (nil? txdata)))
        (is (contains? txdata :whitelist))
        (is (contains? txdata :transaction))))))
