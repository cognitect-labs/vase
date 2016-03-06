(ns vase.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [vase.test-helper :as helper]
            [vase.descriptor :as descriptor]
            [vase.util :as util]
            [vase.config :as cfg]
            [vase.service-no-globals :as service]
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

(deftest self-describing-test
  (helper/with-service (vase.service-route-table/service-map)
    (is (= 200 (:status (helper/GET "/api?edn=true"))))
    (is (= 200 (:status (helper/GET "/api"))))
    (is (= 200 (:status (helper/GET "/api/example/v1?edn=true"))))
    (is (= 200 (:status (helper/GET "/api/example/v1"))))))

(def known-route-names
  #{:vase.service-no-globals/health-check :vase.service-no-globals/clj-ver
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

(def known-route-names-2
  #{:vase.service-route-table/health-check :vase.service-route-table/clj-ver
    :describe-apis :example-v1/describe
    :example-v1/simple-response :example-v1/r-page :example-v1/ar-page
    :example-v1/url-param-example
    :example-v1/validate-page
    :example-v1/db-page
    :example-v1/users-page :example-v1/user-id-page
    :example-v1/user-create :example-v1/user-page
    :example-v1/fogus-page :example-v1/foguspaul-page
    :example-v1/fogussomeone-page})

(deftest all-route-names-present
  (let [service     (vase.service-route-table/service-map)
        routes      (:io.pedestal.http/routes service)
        route-names (set (map :route-name routes))]
    (is (= known-route-names-2 route-names))))

(deftest uniquely-add-routes-test
  ;; TODO: This test needs to be patched
  (let [service (service/service-map)
        test-routes (:io.pedestal.http/routes service)
        test-routes (if (fn? test-routes) (test-routes) test-routes)
        vase-context @(:vase/context service)
        descriptor (:descriptor vase-context)
        master-routes (:master-routes vase-context)
        route-vecs-1 (descriptor/route-vecs descriptor :example :v1)
        route-vecs-2 (descriptor/route-vecs descriptor :example :v2)]

    (is (=
         (map :route-name test-routes)
         (map :route-name (@#'vase/uniquely-add-routes master-routes
                                                       route-vecs-1
                                                       test-routes))))
    (is (=
         (set (map :route-name (@#'vase/uniquely-add-routes master-routes
                                                            route-vecs-2
                                                            test-routes)))
         known-route-names))))


(deftest update-routes-test
  (let [service-map (service/service-map)
        vase-context (service-map :vase/context)
        descriptor (:descriptor @vase-context)
        serv-fn (helper/service service-map)
        _ (swap! vase-context vase/update descriptor :example [:v2])
        observed-routes (set (map :route-name (:routes @vase-context)))]
    (is (= observed-routes known-route-names))
    (is (= (get-in (util/read-json (:body
                                     (response-for serv-fn :get "/api/example/v2/hello")))
                   [:response :payload])
           "Another Hello World Route"))))

(deftest update-descriptor-test
  (let [serv-map (service/service-map)
        serv (helper/service serv-map)
        vase-context (:vase/context serv-map)
        descriptor (:descriptor @vase-context)
        pre-condition (response-for serv :get "/api/example/v2/hello")
        _ (swap! vase-context vase/update descriptor :example [:v2])
        post-condition (response-for serv :get "/api/example/v2/hello")]
    (is (= (:status pre-condition) 404))
    (is (= (:status post-condition) 200))))

;; TODO This needs to use a stateful service
(deftest update-http-descriptor-test
  (let [serv-map (service/service-map)
        serv (helper/service serv-map)]
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
