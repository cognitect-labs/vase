(ns petstore-full.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as http]
            [petstore-full.service :as service]))

(def service
  (::http/service-fn (http/create-servlet (service/service))))

(defn- interesting-headers
  [response]
  (select-keys (get response :headers) ["Content-Type"]))

(deftest home-page-test
  (is (= {"Content-Type" "text/html;charset=UTF-8"}
         (interesting-headers (response-for service :get "/")))))

(deftest about-page-test
  (is (.contains
       (:body (response-for service :get "/about"))
       "Clojure 1.9"))
  (is (= {"Content-Type" "text/html;charset=UTF-8"}
       (interesting-headers (response-for service :get "/about")))))
