(ns com.cognitect.vase.datomic-cloud-test
  "These are integration tests that require a Datomic Cloud system to be up and running."
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [com.cognitect.vase.fern :as vf]
            [datomic.client.api :as d]
            [io.pedestal.interceptor :as i]
            [io.pedestal.interceptor.chain :as chain]))

;; To run these tests, be sure your AWS credentials are available via the environment.
;; Also, set the envvar DATOMIC_SYSTEM_NAME to the correct name of your Cloud system.

(def db-name (or (System/getenv "VASE_TEST_DB_NAME") "vase-test"))
(def aws-region (or (System/getenv "AWS_REGION") "us-east-2"))
(def system-name (System/getenv "DATOMIC_SYSTEM_NAME"))

(defn endpoint [region sys]
  (format "http://entry.%s.%s.datomic.net:8182/" sys region))

(defn client-config [region sys]
  {:server-type :cloud
   :region      region
   :system      sys
   :endpoint    (endpoint region sys)
   :proxy-port  8182})

(defn run-interceptor [i ctx]
  (chain/execute ctx [i]))

(defn run [l ctx]
  (run-interceptor (i/-interceptor l) ctx))

(deftest ^:integration cloud-connection-should
  (testing "function as an interceptor that connects to Datomic"
    (let [initial-context   {}
          resulting-context (-> (fern/literal 'vase.datomic.cloud/client (client-config aws-region system-name) db-name)
                                (run initial-context))]
      (is (some? (-> resulting-context :request :conn)))
      (is (some? (-> resulting-context :request :db)))
      (is (some? (-> resulting-context :request :client))))))

(defn connect [client db-name]
  (let [client (d/client client)
        _      (d/create-database client {:db-name db-name})
        conn   (d/connect client {:db-name db-name})]
    {:request {:client client :conn conn :db (d/db conn)}}))

(defn attr [ident tp card d] {:db/ident ident :db/valueType tp :db/cardinality card :db/doc d})
(defn qattr [a] (vector :find '?e :where ['?e :db/ident a]))
(defn qent [a v] (vector :find '?e :where ['?e a v]))
(defn only [q db] (ffirst (d/q q db)))

(defn assert-txes! [conn & txes]
  (d/transact conn {:tx-data txes}))

(deftest ^:integration cloud-txn-should
  (testing "perform transactions"
    (let [initial-context   (connect (client-config aws-region system-name) db-name)
          conn              (-> initial-context :request :conn)
          db-before         (d/db conn)
          resulting-context (-> (fern/literal 'vase.datomic.cloud/tx (attr :entity/attribute :db.type/long :db.cardinality/one "A docstring"))
                                (run initial-context))
          db-after          (d/db conn)
          attr-entity       (-> (qattr :entity/attribute) (d/q db-after) ffirst)]
      (is (< (:t db-before) (:t db-after)))
      (is (some? attr-entity)))))

(defn simulated-http-request
  [initial-context details]
  (update initial-context :request merge details))

(deftest ^:integration cloud-transact-should
  (testing "perform transactions from request bodies"
    (let [initial-context   (connect (client-config aws-region system-name) db-name)
          conn              (-> initial-context :request :conn)
          _                 (assert-txes! conn (attr :entity/attribute :db.type/long :db.cardinality/one "A docstring"))
          random-value      (rand-int 1000000)
          db-before         (d/db conn)
          resulting-context (-> (fern/literal 'vase.datomic.cloud/transact {:properties [:entity/attribute]})
                                (run (simulated-http-request initial-context {:json-params {:payload [{:entity/attribute random-value}]}})))
          db-after          (d/db (-> resulting-context :request :conn))
          new-entity        (only (qent :entity/attribute random-value) db-after)]
      (is (< (:t db-before) (:t db-after)))
      (is (some? new-entity)))))

(deftest ^:integration cloud-query-should
  (testing "perform queries using parameters from the request"
    (let [initial-context   (connect (client-config aws-region system-name) db-name)
          conn              (-> initial-context :request :conn)
          _                 (assert-txes! conn (attr :user/email :db.type/string :db.cardinality/one "A user's email address."))
          _                 (assert-txes! conn {:user/email "doctor_no@example.com"})
          resulting-context (-> (fern/literal 'vase.datomic.cloud/query {:params ['email]
                                                                         :query  '[:find ?e
                                                                                   :in $ ?email
                                                                                   :where [?e :user/email ?email]]})
                                (run (simulated-http-request initial-context {:params {:email "doctor_no@example.com"}})))]
      (is (some-> resulting-context :response))
      (is (vector? (some-> resulting-context :response :body)))
      (is (= 200 (some-> resulting-context :response :status))))))
