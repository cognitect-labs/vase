(ns cr-ocs.interceptor-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as service]
            [io.pedestal.http.route :as proute]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [ring.util.response :as ring-resp]
            [cr-ocs.test-helper :as helper]
            [cr-ocs.interceptor :as interceptor]))

;; We're going to build custom routes to test the interceptors in isolation
;;  from the core application

(defn about-page
  [request]
  (ring-resp/response (format "Yeah, this is a self-link to %s"
                              (proute/url-for :about))))

(defn hello-page
  [request]
  (ring-resp/response "HELLO"))

(defn hello-foo-page
  [request]
  (ring-resp/response "HELLO FOO"))

(defn goodbye-page
  [request]
  (ring-resp/response "BYE PAGE"))

(defn goodbye-context-page
  [context]
  ;; This returns a response (it's a terminator), but it could do anything,
  ;;  including another round of routing.
  (assoc context :response (ring-resp/response "BYE FOO")))

(defn foo-header? [context]
  (get-in context [:request :headers "foo"]))

(defroutes app-routes
  [[["/about" {:get [:about about-page]}]
    ;; Conditional interceptors can be applied within a single path...
    ["/hello" {:get [^:interceptors [(interceptor/conditional-handlers ::hello-handlers
                                       foo-header? hello-foo-page)]
                     hello-page]}]

    ["/bye" {:get goodbye-page}]
    ["/subsystem" ;; Let's apply the header dispatch to ALL subsystem routes, regardless of path
    ^:interceptors [(interceptor/conditional-context ::header-dispatch
                      foo-header? goodbye-context-page)]
     ["/bye" {:get [:subbye goodbye-page]}]]
    ["/subsystem2"
     ^:interceptors [(interceptor/conditional-context ::header2-dispatch
                      foo-header? goodbye-context-page)]
     ["/bye" {:get [:sub2bye goodbye-page]}]
     ["/*all" {:get [:sub2all goodbye-page]}]]]])

;; We need a custom service to test this out in isolation

(def app-interceptors
  (service/default-interceptors {::service/routes app-routes}))

(defn make-app [interceptors]
  (-> interceptors
      service/service-fn
      ::service/service-fn))

(def app (make-app app-interceptors))

(deftest sanity-checks
  (testing "The interceptors don't affect general routes"
    (let [response (response-for app :get "/about")]
      (is (= "text/plain" (get-in response [:headers "Content-Type"])))
      (is (.contains (:body response) "Yeah, this is"))))
  (testing "The 'good-bye' handler is equiv outside of header filtering"
    (let [root-goodbye (response-for app :get "/bye")
          sub-goodbye (response-for app :get "/subsystem/bye")]
      (is (= root-goodbye sub-goodbye)))))

(deftest conditional-handlers-test
  (testing "A single endpoint can dispatch other handlers"
    (let [response (response-for app :get "/hello")
          dispatched-response (response-for app :get "/hello" :headers {"foo" "hello"})]
      (is (not= response dispatched-response))
      (is (= "HELLO" (:body response)))
      (is (= "HELLO FOO" (:body dispatched-response))))))

(deftest conditional-context-test
  (testing "Dispatch can be applied to sub routes"
    (let [response (response-for app :get "/bye")
          nodist-response (response-for app :get "/subsystem/bye")
          dispatched-response (response-for app :get "/subsystem/bye" :headers {"foo" "hello"})]
      (is (not= response dispatched-response))
      (is (= response nodist-response))
      (is (= "BYE PAGE" (:body response)))
      (is (= "BYE FOO" (:body dispatched-response)))))
  (testing "Dispatch is still bound by routes"
    (let [nf-response (response-for app :get "/subsystem/fake-route" :headers {"foo" "hello"})]
      (is (= 404 (:status nf-response)))
      (is (= "Not Found" (:body nf-response)))))
  (testing "Dispatch intercepts on splat routes"
    (let [response (response-for app :get "/subsystem2/bye")
          dispatched-response (response-for app :get "/subsystem2/bye" :headers {"foo" "hello"})
          splat-response (response-for app :get "/subsystem2/random-url")
          splat-dispatched (response-for app :get "/subsystem2/random-url" :headers {"foo" "hello"})]
      (is (not= response dispatched-response))
      (is (= "BYE PAGE" (:body response)))
      (is (= "BYE FOO" (:body dispatched-response)))
      (is (= dispatched-response splat-dispatched))
      (is (= response splat-response)))))

