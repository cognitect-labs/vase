(ns vase.datoms-test
  (:require  [clojure.test :refer :all]
             [vase.service-route-table :as srt]
             vase
             [clojure.set :as set]
             [vase.util :as util]))

(def known-attributes
  #{:app-name :scheme :host :port :path :method :interceptors :route-name :path-params :path-re :path-parts :doc :path-constraints :constraints :query-constraints :name :enter :leave :error})

(deftest datoms
  (testing "transforms routes in a spec to datoms"
    (let [datoms (vase/routes-datoms "/api" (srt/test-spec) {})]
      (is (every? vector? datoms))
      (is (every? #(= 3 (count %)) datoms))
      (is (every? (comp (complement util/eseq?) last) datoms))
      (let [attr-names (into #{} (map second datoms))]
        (is (empty? (set/difference attr-names known-attributes)))))))
