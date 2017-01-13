(ns com.cognitect.vase.descriptor-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [com.cognitect.vase.test-helper :as helper]
            [com.cognitect.vase.service-route-table :as srt]
            [com.cognitect.vase.util :as util]
            [com.cognitect.vase :as vase]))

(deftest exercise-descriptored-service
  (helper/with-service (srt/service-map)
    (let [post-response (helper/post-json "/api/example/v1/user" {:payload [{:user/userId 42
                                                                             :user/userEmail "jane@example.com"}]})
          get-response  (helper/GET "/api/example/v1/fogus")
          get-response2  (helper/GET "/api/example/v1/users/42")
          delete-response (helper/json-request
                            :delete "/api/example/v1/user"
                            ;; In Datomic 5544, lookup-ids no longer automatically get prepended with `:`
                            ;;  Use a string instead...VV
                            {:payload [{:db/id [":user/userId" 42]}]})
          get-response3 (helper/GET "/api/example/v1/fogus")]
      (is (= 200 (:status post-response)))
      (is (= 200 (:status get-response)))
      (is (= 200 (:status get-response2)))
      ;(is (= 200 (:status delete-response)))
      (is (= 200 (:status get-response3)))
      (is (string? (get-in get-response [:headers "vaserequest-id"])))
      (is (seq (helper/response-data post-response)))
      (is (= (seq (helper/response-data get-response))
             (seq (helper/response-data get-response2))))
      (is (empty? (helper/response-data get-response3))))))

(deftest exercise-constant-and-parameter-action
  (helper/with-service (srt/service-map)
    (let [post1 (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "jane@example.com"}]})
          post2 (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "jill@example.com"}]})
          special-get (helper/GET "/api/example/v1/fogus-and-someone?someone=jill@example.com")]
      (are [x] (= (:status x) 200)
           post1 post2 special-get)
      (is (seq (helper/response-data special-get)))
      (is (= (count (helper/response-data special-get)) 2)))))

(deftest exercise-version-interceptor-chains
  (helper/with-service (srt/service-map)
    (let [hello-resp (helper/GET "/api/example/v2/hello")
          hello-body (util/read-transit-json (:body hello-resp))]
      (is (= hello-body {:just-a-key "Another Hello World Route"})))))

(deftest exercise-per-route-interceptor-chains
  (helper/with-service (srt/service-map)
    (let [hello-resp (helper/GET "/api/example/v2/intercept")
          hello-body (helper/response-data hello-resp)]
      (is (= hello-body {:one 1})))))

(deftest multiple-api-specs
  (let [one-spec (srt/test-spec)
        datomic-uri (:datomic-uri one-spec)
        specs (conj
                (mapv (fn [[path v]]
                        (assoc-in {:descriptor {}
                                   :datomic-uri datomic-uri}
                                  path v))
                      {[:descriptor :vase/norms] {:example/animal-schema
                                                  {:vase.norm/txes [#vase/schema-tx [[:animal/type :one :string :identity "The type of animal"]
                                                                                           [:animal/pack-name :one :string "The word for a 'pack' of the animal"]]]}}
                       [:descriptor :vase/apis] {:example/vnew
                                                 {:vase.api/routes
                                                  {"/something-new" {:get #vase/respond {:name :example.vnew/something-new
                                                                                         :body "This is new"}}}}}})
                one-spec)]
    (testing "norms merge cleanly"
      (let [merged-norms (vase/ensure-schema specs)]
        (is (= (set (keys (get-in merged-norms [datomic-uri :norms])))
               #{:example/user-schema :example/animal-schema :example/base-schema :example/loan-schema}))))
    (testing "routes merge cleanly; Only activated routes are included"
      (let [routes (vase/routes "/api" specs)
            paths (map (fn [[path verb]]
                           [path verb]) routes)
            more-routes (vase/routes "/api" (assoc-in specs [1 :activated-apis] [:example/vnew]))
            more-paths (map (fn [[path verb]]
                              [path verb]) more-routes)]
        (is (= (count (set paths)) (count paths)))
        (is (= (set paths)
               #{["/api/example/v1/user" :delete]
                 ["/api/example/v2" :get]
                 ["/api" :get]
                 ["/api/example/v1/fogus-and-paul" :get]
                 ["/api/example/v1/redirect-to-google" :get]
                 ["/api/example/v1/users" :get]
                 ["/api/example/v1/validate" :post]
                 ["/api/example/v2/intercept" :get]
                 ["/api/example/v1/db" :get]
                 ["/api/example/v1/users/:id" :get]
                 ["/api/example/v1/fogus-and-someone" :get]
                 ["/api/example/v1/fogus" :get]
                 ["/api/example/v1" :get]
                 ["/api/example/v1/user" :get]
                 ["/api/example/v1/redirect-to-param" :get]
                 ["/api/example/v1/hello" :get]
                 ["/api/example/v1/user" :post]
                 ["/api/example/v1/capture-s/:url-thing" :get]
                 ["/api/example/v2/hello" :get]}))
        (is (= (count (set more-paths)) (count more-paths)))
        (is (> (count more-paths) (count paths)))
        (is (= (set more-paths)
               #{["/api/example/v1/user" :delete]
                 ["/api/example/v2" :get]
                 ["/api" :get]
                 ["/api/example/v1/fogus-and-paul" :get]
                 ["/api/example/v1/redirect-to-google" :get]
                 ["/api/example/v1/users" :get]
                 ["/api/example/v1/validate" :post]
                 ["/api/example/v2/intercept" :get]
                 ["/api/example/v1/db" :get]
                 ["/api/example/v1/users/:id" :get]
                 ["/api/example/v1/fogus-and-someone" :get]
                 ["/api/example/v1/fogus" :get]
                 ["/api/example/v1" :get]
                 ["/api/example/v1/user" :get]
                 ["/api/example/v1/redirect-to-param" :get]
                 ["/api/example/v1/hello" :get]
                 ["/api/example/v1/user" :post]
                 ["/api/example/v1/capture-s/:url-thing" :get]
                 ["/api/example/vnew" :get]
                 ["/api/example/vnew/something-new" :get]
                 ["/api/example/v2/hello" :get]}))))))
