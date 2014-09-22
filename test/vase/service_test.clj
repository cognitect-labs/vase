(ns vase.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [vase.test-helper :as helper]
            [vase.service :as service]
            [vase.descriptor :as descriptor]
            [vase.util :as util]
            [vase.config :refer [config]]
            [vase]))

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
  (is (=
       (selected-headers helper/GET "/about" ["Content-Type"])
       {"Content-Type" "text/html;charset=UTF-8"})))

(deftest request-tracing-test
  (is (=
       (selected-headers #(helper/GET % :headers {"vaserequest-id" "yes"})
                         "/api/example/v1/hello"
                         ["Content-Type" "vaserequest-id"])
       {"Content-Type" "text/html;charset=UTF-8"
        "vaserequest-id" "yes"}))
  (is (string? (get-in (helper/GET "/api/example/v1/hello") [:headers "vaserequest-id"]))))

(def known-route-names
  #{:vase.service/health-check :vase.service/clj-ver
    :vase/append-api :vase/show-routes
    :example-v2/hello
    :example-v1/simple-response :example-v1/r-page :example-v1/ar-page
    :example-v1/url-param-example
    :example-v1/validate-page
    :example-v1/db-page
    :example-v1/users-page :example-v1/user-id-page
    :example-v1/user-create :example-v1/user-page
    :example-v1/fogus-page :example-v1/foguspaul-page
    :example-v1/fogussomeone-page})

(deftest uniquely-add-routes-test
  ;; TODO: This test needs to be patched
  (is
    (let [test-routes (:io.pedestal.http/routes service/service)
          test-routes (if (fn? test-routes) (test-routes) test-routes)
          route-vecs (descriptor/route-vecs (:descriptor (meta service/routes)) :example :v1)]
      (=
       (map :route-name test-routes)
       (map :route-name (vase/uniquely-add-routes (:master-routes (meta service/routes))
                                                    route-vecs
                                                    @service/routes)))))
  (let [route-vecs (descriptor/route-vecs (:descriptor (meta service/routes)) :example :v2)]
    (is
      (=
       (set (map :route-name (vase/uniquely-add-routes (:master-routes (meta service/routes))
                                                         route-vecs
                                                         @service/routes)))
       known-route-names))))

(deftest bash-routes-test
  (let [route-vecs (descriptor/route-vecs (:descriptor (meta service/routes)) :example :v2)
        serv-map (helper/refresh-service-map)
        serv-fn (helper/service-fn serv-map)
        _ (vase/bash-routes! (:routes-atom serv-map) route-vecs)
        observed-routes (set (map :route-name (deref (:routes-atom serv-map))))]
    (is (= observed-routes known-route-names))
    (is (= (get-in (util/read-json (:body
                                     (response-for serv-fn :get "/api/example/v2/hello")))
                   [:response :payload])
           "Another Hello World Route"))))

(deftest bash-descriptor-test
  (let [serv-map (helper/refresh-service-map)
        serv (helper/service-fn serv-map)
        routes-atom (:routes-atom serv-map)
        descriptor (:descriptor (meta service/routes))
        pre-condition (response-for serv :get "/api/example/v2/hello")
        _ (vase/bash-from-descriptor! routes-atom descriptor :example :v2)
        post-condition (response-for serv :get "/api/example/v2/hello")]
    (is (= (:status pre-condition) 404))
    (is (= (:status post-condition) 200))))

;; TODO This needs to use a stateful service
(deftest bash-http-descriptor-test
  (let [serv-map (helper/refresh-service-map)
        serv (helper/service-fn serv-map)]
    (is
     ;; There is no v2 route to start
     (= (:body (response-for serv :get "/api?f=v2"))
        ""))
    (let [ ;; Add V2
          pre-modified-routes (:body (response-for serv :get "/api"))
          post-response (response-for serv :post "/api" :body (slurp "config/sample_payload.edn") :headers {"Content-Type" "application/edn"})
          post-modified-routes (:body (response-for serv :get "/api"))
          new-api-resp (response-for serv :get "/api/example/v2/hello")]
      (is (= (:body post-response) "{:added [:v2]}"))
      (is (not= pre-modified-routes post-modified-routes))
      (is (= (select-keys (:headers new-api-resp) ["Content-Type"])
             {"Content-Type" "application/json;charset=UTF-8"}))
      (is (= (select-keys (util/read-json (:body new-api-resp)) [:response :errors])
             {:response {:payload "Another Hello World Route"} :errors {}})))))

;;TODO Make this work when we re-introduce validators
;(helper/POST "/api/example/v1/validate/person" {:name "paul" :age "55"})

