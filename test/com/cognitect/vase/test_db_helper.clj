(ns com.cognitect.vase.test-db-helper
  (:require  [clojure.test :as t]
             [datomic.api :as d])
  (:import [java.util UUID]))

(defn new-database
  "This generates a new, empty Datomic database for use within unit tests."
  [txes]
  (let [uri  (str "datomic:mem://test" (UUID/randomUUID))
        _    (d/create-database uri)
        conn (d/connect uri)]
    (doseq [t txes]
      @(d/transact conn t))
    {:uri uri
     :connection conn}))

(def ^:dynamic *current-db-connection* nil)
(def ^:dynamic *current-db-uri* nil)

(defmacro with-database
  "Executes all requests in the body with the same database."
  [txes & body]
  `(let [dbm# (new-database ~txes)]
     (binding [*current-db-uri* (:uri dbm#)
               *current-db-connection* (:connection dbm#)]
       ~@body)))

(defn connection
  ([]
   (or *current-db-connection* (:connection (new-database []))))
  ([txes]
   (or *current-db-connection* (:connection (new-database txes)))))


(def query-test-txes
  [[{:db/id #db/id[:db.part/db]
     :db/ident :company/name
     :db/unique :db.unique/value
     :db/valueType :db.type/string
     :db/cardinality :db.cardinality/one
     :db.install/_attribute :db.part/db}
    {:db/id #db/id[:db.part/db]
     :db/ident :user/userId
     :db/valueType :db.type/long
     :db/cardinality :db.cardinality/one
     :db.install/_attribute :db.part/db
     :db/doc "A Users unique identifier"
     :db/unique :db.unique/identity}
    {:db/id #db/id[:db.part/db]
     :db/ident :user/userEmail
     :db/valueType :db.type/string
     :db/cardinality :db.cardinality/one
     :db.install/_attribute :db.part/db
     :db/doc "The users email"
     :db/unique :db.unique/value}
    {:db/id #db/id[:db.part/db]
     :db/ident :user/userBio
     :db/valueType :db.type/string
     :db/cardinality :db.cardinality/one
     :db.install/_attribute :db.part/db
     :db/doc "A short blurb about the user"
     :db/fulltext true :db/index true}
    {:db/id #db/id[:db.part/db]
     :db/ident :user/userActive?
     :db/valueType :db.type/boolean
     :db/cardinality :db.cardinality/one
     :db.install/_attribute :db.part/db
     :db/doc "User active flag."
     :db/index true}
    {:db/id #db/id[:db.part/db]
     :db/ident :loanOffer/loanId
     :db/valueType :db.type/long
     :db/cardinality :db.cardinality/one
     :db.install/_attribute :db.part/db
     :db/doc "The unique offer ID"
     :db/unique :db.unique/value}
    {:db/id #db/id[:db.part/db]
     :db/ident :loanOffer/fees
     :db/valueType :db.type/long
     :db/cardinality :db.cardinality/one
     :db.install/_attribute :db.part/db
     :db/doc "All of the loan fees"
     :db/index true}
    {:db/id #db/id[:db.part/db]
     :db/ident :loanOffer/notes
     :db/valueType :db.type/string
     :db/cardinality :db.cardinality/many
     :db.install/_attribute :db.part/db
     :db/doc "Notes about the loan"}
    {:db/id #db/id[:db.part/db]
     :db/ident :user/loanOffers
     :db/valueType :db.type/ref
     :db/cardinality :db.cardinality/many
     :db.install/_attribute :db.part/db
     :db/doc "The collection of loan offers"}]
   [{:db/id #db/id[:db.part/user]
     :user/userId 100
     :user/userEmail "paul@cognitect.com"}
    {:db/id #db/id[:db.part/user]
     :user/userId 101
     :user/userEmail "fogus@cognitect.com"}
    {:db/id #db/id[:db.part/user]
     :user/userId 102
     :user/userEmail "mtnygard@cognitect.com"}]])
