(ns com.cognitect.vase.literals-test
  (:require [clojure.test :refer :all]
            [com.cognitect.vase.literals :as lit]
            [io.pedestal.test :refer :all]
            [io.pedestal.log :as log]
            [com.cognitect.vase.test-helper :as helper]
            [com.cognitect.vase.actions :as actions]
            [clojure.string :as string]
            [io.pedestal.interceptor :as i]))

;; Schema literal tests
(deftest test-schema-tx
  (are [case input expected] (testing case (= expected (map #(dissoc % :db/id) (read-string input))))
    "One attribute"
    "#vase/schema-tx[[:entity/attribute :one :long \"A docstring\"]]"
    [{:db/ident              :entity/attribute
      :db/valueType          :db.type/long
      :db/cardinality        :db.cardinality/one
      :db/doc                "A docstring"}]

    "Two attributes"
    "#vase/schema-tx[[:e/a1 :one :long \"\"] [:e/a2 :many :string \"docstring 2\"]]"
    [{:db/ident :e/a1
      :db/valueType :db.type/long
      :db/cardinality :db.cardinality/one
      :db/doc ""}
     {:db/ident              :e/a2
      :db/valueType          :db.type/string
      :db/cardinality        :db.cardinality/many
      :db/doc                "docstring 2"}]

    "One toggle"
    "#vase/schema-tx[[:e/a :one :long :identity \"Doc\"]]"
    [{:db/ident              :e/a
      :db/valueType          :db.type/long
      :db/cardinality        :db.cardinality/one
      :db/unique             :db.unique/identity
      :db/doc                "Doc"}]

    "Several toggles"
    "#vase/schema-tx[[:e/a :one :string :identity :index :component :no-history :fulltext \"Doc\"]]"
    [{:db/ident              :e/a
      :db/valueType          :db.type/string
      :db/cardinality        :db.cardinality/one
      :db/index              true
      :db/isComponent        true
      :db/noHistory          true
      :db/fulltext           true
      :db/unique             :db.unique/identity
      :db/doc                "Doc"}])

  (are [bad-input] (thrown? Throwable (read-string bad-input))
    "#vase/schema-tx[[:e/a :one-is-the-lonliest-number :long \"doc\"]]"
    "#vase/schema-tx[[\"not a keyword\" :one :ref \"doc\"]]"
    "#vase/schema-tx[[:e/a :one :categorically-imperative \"\"]]"
    "#vase/schema-tx[[:e/a :one :ref]]"
    "#vase/schema-tx[#{:e/a :one :ref \"doc\"}]")

  (testing "helpful error messages"
    (are [bad-input msg-pattern] (thrown-with-msg? Throwable msg-pattern (read-string bad-input))
      "#vase/schema-tx{}"
      #"must be a vector"

      "#vase/schema-tx[()]"
      #"nested elements must be vectors"

      "#vase/schema-tx[:e/a :one :long :identity \"Doc\"]"
      #"must look like this"

      "#vase/schema-tx[[e/a :one :long \"doc\"]]"
      #"must be Clojure keywords"

      "#vase/schema-tx[[:e/a :one :long]]"
      #"last thing in the vector must be a docstring")))

;; Action literals tests
;;
(deftest test-data-massaging
  "This test ensures that data received can be transformed into a form
   amenable to storing into datomic."

  (is (= (actions/process-id {:db/id 1})
         {:db/id 1}))

  (is (= (actions/process-id {:db/id ["user/age" 42]})
         {:db/id [:user/age 42]})))

(deftest respond-literal
  (is (=
       (:body (helper/GET "/api/example/v1/hello"))
       "Hello World")))

(deftest validate-literal
  (let [response (helper/post-json "/api/example/v1/validate"
                                   {:age 31
                                    :name "paul"})]
    (is (= (:status response) 200))
    (is (empty? (helper/response-data response)))))

(defn- reader-literal [a]
  (first (string/split (pr-str a) #"\{")))

(deftest printing-literals
  (testing "actions print as reader literals"
    (are [word action] (= word (reader-literal action))
      "#vase/respond"  (lit/respond  {:name :responder})
      "#vase/redirect" (lit/redirect {:name :redirect :url ""})
      "#vase/validate" (lit/validate {:name :validate})
      "#vase.datomic/query"    (lit/query    {:name :query :query []})
      "#vase.datomic.cloud/query" (lit/query-cloud {:name :query :query []})
      "#vase.datomic/transact" (lit/transact {:name :transact})
      "#vase.datomic.cloud/transact" (lit/transact-cloud {:name :tr})
      "#vase/conform"  (lit/conform  {:name :conform})))

  (testing "actions round-trip through the reader"
    (are [action] (= action (read-string (pr-str action)))
      (lit/respond  {:name :responder :bar :baz :extra "stuff"})
      (lit/redirect {:name :redirect :url ""})
      (lit/validate {:name :validate})
      (lit/query    {:name :query :query []})
      (lit/query-cloud {:name :query-cloud :query []})
      (lit/transact {:name :transact})
      (lit/transact-cloud {:name :transact-cloud})
      (lit/conform  {:name :conform :from :from-key})))

  (testing "literals create IntoInterceptor values"
    (are [s] (satisfies? i/IntoInterceptor (read-string s))
      "#vase/respond{:name :a}"
      "#vase/redirect{:name :b :url \"http://www.example.com\"}"
      "#vase/validate{:name :c}"
      "#vase/conform{}"
      "#vase.datomic/query{:name :d :query []}"
      "#vase.datomic/transact{:name :e}"
      "#vase.datomic.cloud/query{:name :d :query []}"
      "#vase.datomic.cloud/transact{:name :e}")))
