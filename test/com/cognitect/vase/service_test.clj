(ns com.cognitect.vase.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [com.cognitect.vase.test-helper :as helper]
            [com.cognitect.vase :as vase]
            [com.cognitect.vase.service-route-table :as srt]))

(defn selected-headers
  "Return a map with selected-keys out of the headers of a request to url"
  [verb url selected-keys]
  (-> url
      verb
      :headers
      (select-keys selected-keys)))

(deftest request-tracing-test
  (is (=
       (selected-headers #(helper/GET % :headers {"vaserequest-id" "yes"})
                         "/api/example/v1/hello"
                         ["Content-Type" "vaserequest-id"])
       {"Content-Type" "text/plain"
        "vaserequest-id" "yes"}))
  (is (string? (get-in (helper/GET "/api/example/v1/hello") [:headers "vaserequest-id"]))))

(deftest self-describing-test
  (helper/with-service (srt/service-map)
    (is (= 200 (:status (helper/GET "/api?edn=true"))))
    (is (= 200 (:status (helper/GET "/api"))))
    (is (= 200 (:status (helper/GET "/api/example/v1?edn=true"))))
    (is (= 200 (:status (helper/GET "/api/example/v1"))))
    (is (= 200 (:status (helper/GET "/api/example/v2?edn=true"))))
    (is (= 200 (:status (helper/GET "/api/example/v2"))))))

(def known-route-names
  #{:describe-apis
    :example.v1/describe
    :example.v1/simple-response
    :example.v1/r-page
    :example.v1/ar-page
    :example.v1/url-param-example
    :example.v1/validate-page
    :example.v1/db-page
    :example.v1/users-page
    :example.v1/user-id-page
    :example.v1/user-create
    :example.v1/user-delete
    :example.v1/user-page
    :example.v1/fogus-page
    :example.v1/foguspaul-page
    :example.v1/fogussomeone-page
    :example.v2/describe
    :example.v2/hello
    :example.v2/intercept})

(deftest all-route-names-present
  (let [service     (srt/service-map)
        routes      (:io.pedestal.http/routes service)
        route-names (set (map :route-name routes))]
    (is (= known-route-names route-names))))
