(ns vase.datoms-test
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [datomic.api :as d]
            vase
            [vase.descriptor :as desc]
            [vase.service-route-table :as srt]
            [vase.util :as util]))

(def known-attributes
  #{:vase/name
    :vase/norms :vase.norm/requires :vase.norm/txes
    :vase/apis
    :vase/specs :vase/spec
    :vase.api/routes :vase.api/forward-headers :vase.api/interceptors :vase.api/schemas
    :app-name :scheme :host :port :path :method :interceptors :action-literal
    :route-name :path-params :path-re :path-parts :doc :path-constraints :constraints :query-constraints :name :enter :leave :error})

(deftest datoms
  (testing "transforms routes in a spec to datoms"
    (let [datoms (vase/descriptor-facts "/api" (srt/test-spec) {})]
      (is (every? vector? datoms))
      (is (every? #(= 3 (count %)) datoms))
      (is (every? (comp (complement util/eseq?) last) datoms))
      (let [attr-names (into #{} (map second datoms))]
        (is (empty? (set/difference attr-names known-attributes)))))))

(deftest schema-query
  (let [ddb (vase/descriptor-facts "/api" (srt/test-spec) {})
        res (d/q '[:find ?schema-name
                   :in   $
                   :where
                   [_       :vase/norms ?schema]
                   [?schema :vase/name ?schema-name]] ddb)]
    (is (= #{[:example/user-schema] [:example/loan-schema] [:example/base-schema]} res))
    (is (seq res))))

(def expected-query-paths
  #{["/api/example/v1/fogus-and-someone"]
    ["/api/example/v1/fogus"]
    ["/api/example/v1/users/:id"]
    ["/api/example/v1/user"]
    ["/api/example/v1/fogus-and-paul"]
    ["/api/example/v1/db"]
    ["/api/example/v1/users"]})

(deftest route-query
  (let [ddb     (vase/descriptor-facts "/api" (srt/test-spec) {})
        get-res (d/q '[:find ?p
                       :in $
                       :where
                       [_ :vase.api/routes ?route]
                       [?route :path   ?p]
                       [?route :method :get]
                       [?route :interceptors ?i]
                       [?i     :action-literal :vase/query]] ddb)]
    (is (= expected-query-paths get-res))))

(def schema-dependency-rules
  '[[(schema-dep? ?api ?schema-name)
     [?api :vase.api/schemas ?schema-name]]
    [(schema-dep? ?api ?schema-name)
     [?api :vase.api/schemas ?i]
     [schema-requires? ?i ?schema-name]]
    [(schema-requires? ?user ?provider)
     [?id :vase/name ?user]
     [?id :vase.norm/requires ?provider]]
    [(schema-requires? ?user ?provider)
     [?id :vase/name ?user]
     [?id :vase.norm/requires ?intermediate]
     [schema-requires? ?intermediate ?provider]]])

(deftest apis-using-schema
  (testing "direct usage"
    (let [ddb (vase/descriptor-facts "/api" (srt/test-spec) {})
          results (d/q '[:find ?api-name
                         :in $ ?schema-name
                         :where
                         [_    :vase/apis ?api]
                         [?api :vase/name ?api-name]
                         [?api :vase.api/schemas ?schema-name]]
                       ddb :example/user-schema)]
      (is (= #{[:example/v1]} results))))

  (testing "recursive rules"
    (let [ddb (vase/descriptor-facts "/api" (srt/test-spec) {})
          results (d/q '[:find ?schema-name
                         :in $ % ?api-name
                         :where
                         [_    :vase/apis ?api]
                         [?api :vase/name ?api-name]
                         [schema-dep? ?api ?schema-name]]
                       ddb
                       schema-dependency-rules
                       :example/v1)]
      (is (= #{[:example/user-schema] [:example/loan-schema] [:example/base-schema]} results)))))
