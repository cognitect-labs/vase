(ns vase.service-properties
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [io.pedestal.test :refer :all]
            [vase.test-helper :as helper]
            [vase.config :refer (config)]))

;; Checkers
(defn proper-homepage? [got expect]
  (= got expect))

;; NOTE: Testing a constant as a start
(defspec homepage-response (config :test-trials 100)
  (prop/for-all [expect gen/string]
    (let [msg (:body (helper/GET "/"))]
      (proper-homepage? msg "alive"))))

