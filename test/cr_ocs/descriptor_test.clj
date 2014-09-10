(ns cr-ocs.descriptor-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [cr-ocs.test-helper :as helper]
            [cr-ocs.db :as cdb]
            [cr-ocs.util :as util]
            [cr-ocs.descriptor :as desc]
            [cr-ocs.config :refer [config]]
            [datomic.api :as d]))

(use-fixtures :each helper/test-with-fresh-db)

;; Tests that require fresh DBs should go in here

(deftest exercise-descriptored-service
  (let [post-response (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "mefogus@gmail.com"}]})
        get-response (helper/GET "/api/example/v1/fogus")]
    (is (= 200 (:status post-response)))
    (is (= 200 (:status get-response)))
    (is (seq (helper/response-data post-response)))
    (is (seq (helper/response-data get-response)))))

(deftest exercise-constant-and-parameter-action
  (let [post1 (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "mefogus@gmail.com"}]})
        post2 (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "paul.degrandis@gmail.com"}]})
        special-get (helper/GET "/api/example/v1/fogus-and-someone?someone=paul.degrandis@gmail.com")]
    (are [x] (= (:status x) 200)
         post1 post2 special-get)
    (is (seq (helper/response-data special-get)))
    (is (= (count (helper/response-data special-get)) 2))))

(deftest schema-query
  (let [descriptor (util/edn-resource (get config :initial-descriptor "sample_descriptor.edn"))
        ddb (desc/descriptor-facts descriptor)
        res (d/q '[:find ?schema-name :in $ :where
                   [_ ::desc/schema ?schema]
                   [?schema ::desc/name ?schema-name]] ddb)
        res-length (count res)]
    (is (seq res))
    (is (= (count (remove #(-> % str (.startsWith "blah")) res)) res-length))))

(deftest route-query
  (let [descriptor (util/edn-resource (get config :initial-descriptor "sample_descriptor.edn"))
        ddb (desc/descriptor-facts descriptor)
        post-res (d/q '[:find ?route :in $ :where
                        [_ ::desc/route ?route]
                        [?route ::desc/method :post]
                        [?route ::desc/action-literal :query]] ddb)
        get-res (d/q '[:find ?route :in $ :where
                       [_ ::desc/route ?route]
                       [?route ::desc/method :get]
                       [?route ::desc/action-literal :query]] ddb)
        action-literals (map first (d/q '[:find ?literal :in $ :where
                                          [_ ::desc/action-literal ?literal]] ddb))]
    (is (= #{:transact :query :respond :redirect :validate} (set action-literals)))
    (is (empty? post-res))
    (is (seq get-res))))

(deftest all-route-queries-that-use-X-schema
  (let [descriptor (util/edn-resource (get config :initial-descriptor "sample_descriptor.edn"))
        ddb (desc/descriptor-facts descriptor)
        results (d/q '[:find ?path :in $ ?schema-name :where
                       [?api ::desc/schema ?schema]
                       [?schema ::desc/name ?schema-name]
                       [?api ::desc/route ?route]
                       [?route ::desc/path ?path]] ddb :example/user-schema)]
    (is (= #{["/fogus-and-paul"] ["/capture-s/:url-thing"] ["/users"] ["/user"]
             ["/fogus-and-someone"] ["/redirect-to-google"]
             ["/redirect-to-param"] ["/fogus"] ["/db"] ["/users/:id"]
             ["/validate"] ["/hello"]} results))))

