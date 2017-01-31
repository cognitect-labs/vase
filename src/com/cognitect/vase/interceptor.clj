(ns com.cognitect.vase.interceptor
  (:require [io.pedestal.interceptor.helpers :as helpers :refer [defon-request]]
            [io.pedestal.interceptor :as i]
            [clojure.stacktrace :as ctrace]
            [clj-time.core :as clj-time]
            [com.cognitect.vase.util :as util]))

(def request-id-header "vaserequest-id")

(def attach-received-time
  (i/-interceptor
   {:name  ::attach-received-time
    :doc   "Attaches a timestamp to every request."
    :enter (fn [ctx] (assoc-in ctx [:request :received-time] (clj-time/now)))}))

(def attach-request-id
  (i/-interceptor
   {:name  ::attach-request-id
    :doc   "Attaches a request ID to every request;
            If there's a 'request_id' header, it will use that value, otherwise it will generate a short hash"
    :enter (fn [{:keys [request] :as ctx}]
             (let [req-id (get-in request [:headers request-id-header] (util/short-hash))]
               (-> ctx
                   (assoc-in [:request :request-id] req-id)
                   (assoc-in [:request :headers request-id-header] req-id))))}))

(defn forward-headers
  [headers]
  (i/-interceptor
   {:name  ::forward-headers
    :doc   "Given an interceptor name and list of headers to forward,
            return an interceptor that attaches those headers to reponses IFF
            they are in the request"
    :leave (fn [context]
             (update-in context [:response :headers]
                        #(merge (select-keys (get-in context [:request :headers]) headers) %)))}))
