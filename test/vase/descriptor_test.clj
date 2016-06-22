(ns vase.descriptor-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [vase.test-helper :as helper]
            [vase.service-route-table :as srt]
            [vase.util :as util]))

(deftest exercise-descriptored-service
  (helper/with-service (srt/service-map)
    (let [post-response (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "mefogus@gmail.com"}]})
          get-response  (helper/GET "/api/example/v1/fogus")]
      (is (= 200 (:status post-response)))
      (is (= 200 (:status get-response)))
      (is (seq (helper/response-data post-response)))
      (is (seq (helper/response-data get-response))))))

(deftest exercise-constant-and-parameter-action
  (helper/with-service (srt/service-map)
    (let [post1 (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "mefogus@gmail.com"}]})
          post2 (helper/post-json "/api/example/v1/user" {:payload [{:user/userEmail "paul.degrandis@gmail.com"}]})
          special-get (helper/GET "/api/example/v1/fogus-and-someone?someone=paul.degrandis@gmail.com")]
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

