(ns cr-ocs.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [cr-ocs.test-helper :as helper]
            [cr-ocs.service :as service]
            [cr-ocs.descriptor :as descriptor]
            [cr-ocs.util :as util]))

(defn selected-headers
  "Return a map with selected-keys out of the headers of a request to url"
  [verb url selected-keys]
  (-> url
      verb
      :headers
      (select-keys selected-keys)))

(deftest home-page-test
  (is (=
       (:body (helper/GET "/"))
       "alive"))
  (is (=
       (selected-headers helper/GET "/" ["Content-Type"])
       {"Content-Type" "text/html;charset=UTF-8"})))

(deftest about-page-test
  (is (.contains
        (:body (helper/GET "/about"))
        "Clojure 1.6"))
  (is (=
       (selected-headers helper/GET "/about" ["Content-Type"])
       {"Content-Type" "text/html;charset=UTF-8"})))

(deftest respond-literal
  (is (=
       (:body (helper/GET "/api/example/v1/hello"))
       "Hello World")))

(deftest request-tracing-test
  (is (=
       (selected-headers #(helper/GET % :headers {"crrequest-id" "yes"})
                         "/api/example/v1/hello"
                         ["Content-Type" "crrequest-id"])
       {"Content-Type" "text/html;charset=UTF-8"
        "crrequest-id" "yes"}))
  (is (string? (get-in (helper/GET "/api/example/v1/hello") [:headers "crrequest-id"]))))

(def known-route-names
  [:cr-ocs.service/health-check :cr-ocs.service/clj-ver
   :cr-ocs.service/append-api :cr-ocs.service/show-routes
   :example-v2/hello
   :example-v1/simple-response :example-v1/r-page :example-v1/ar-page
   :example-v1/url-param-example
   :example-v1/db-page
   :example-v1/users-page :example-v1/user-id-page
   :example-v1/user-create :example-v1/user-page
   :example-v1/fogus-page :example-v1/foguspaul-page])

(deftest uniquely-add-routes-test
  (is
    (let [test-routes (:io.pedestal.http/routes service/service)
          test-routes (if (fn? test-routes) (test-routes) test-routes)]
      (=
       (map :route-name test-routes)
       (map :route-name (service/uniquely-add-routes (descriptor/route-vecs service/desc :example :v1) test-routes)))))
  (is
    (let [test-routes (:io.pedestal.http/routes service/service)
          test-routes (if (fn? test-routes) (test-routes) test-routes)]
       (mapv :route-name (service/uniquely-add-routes (descriptor/route-vecs service/desc :example :v2) test-routes))
      (=
       (mapv :route-name (service/uniquely-add-routes (descriptor/route-vecs service/desc :example :v2) test-routes))
       known-route-names))))

(deftest bash-routes-test
  (let [_ (service/bash-routes! (descriptor/route-vecs service/desc :example :v2))
        observed-routes (mapv :route-name (if (fn? service/routes) (service/routes) service/routes))]
    (is (= observed-routes known-route-names))
    (is (= (get-in (util/read-json (:body (response-for (helper/service (assoc service/service
                                                                          :io.pedestal.http/routes
                                                                          #(deref #'service/routes)))
                                                        :get "/api/example/v2/hello"))) [:response :payload])
           "Another Hello World Route")))
  ;; reset and confirm the op works
  (is (boolean (service/reset-routes!))))

(deftest bash-http-descriptor-test
  (is
   ;; There is no v2 route to start
   (= (:body (helper/GET "/api?f=v2"))
      ""))
  (let [ ;; Add V2
        post-response (helper/POST "/api" :body (slurp "config/sample_payload.edn") :headers {"Content-Type" "application/edn"})
        new-api-resp (response-for (helper/service (assoc service/service
                                                     :io.pedestal.http/routes
                                                     #(deref #'service/routes)))
                                   :get "/api/example/v2/hello")]
    (is (= (:body post-response) "{:added [:v2]}"))
    (is (= (select-keys (:headers new-api-resp) ["Content-Type"])
           {"Content-Type" "application/json;charset=UTF-8"}))
    (is (= (select-keys (util/read-json (:body new-api-resp)) [:response :errors])
           {:response {:payload "Another Hello World Route"} :errors {}})))
  (is (boolean (service/reset-routes!))))

;;TODO Make this work when we re-introduce validators
;(helper/POST "/api/example/v1/validate/person" {:name "paul" :age "55"})

