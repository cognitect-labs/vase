(ns vase.interceptor
  (:require [io.pedestal.interceptor.helpers :as helpers :refer [defon-request]]
            [io.pedestal.interceptor :as i]
            [clojure.stacktrace :as ctrace]
            [clj-time.core :as clj-time]
            [vase.util :as util]))

(def request-id-header"vaserequest-id")

(defn attach-received-time
  [interceptor-ns]
  (let [kind :attach-receive-time]
    (with-meta (i/-interceptor
                {:name (keyword interceptor-ns (name kind))
                 :doc   "Attaches a timestamp to every request."
                 :enter (fn [ctx] (assoc-in ctx [:request :received-time] (clj-time/now)))})
      {:kind kind})))

(defn attach-request-id
  [interceptor-ns]
  (let [kind :attach-request-id]
    (with-meta
      (i/-interceptor
       {:name (keyword interceptor-ns (name kind))
        :doc   "Attaches a request ID to every request;
            If there's a 'request_id' header, it will use that value, otherwise it will generate a short hash"
        :enter (fn [{:keys [request] :as ctx}]
                 (let [req-id (get-in request [:headers request-id-header] (util/short-hash))]
                   (-> ctx
                       (assoc-in [:request :request-id] req-id)
                       (assoc-in [:request :headers request-id-header] req-id))))})
      {:kind kind})))

(defn forward-headers
  [headers interceptor-ns]
  (let [kind :forward-headers]
    (with-meta
      (i/-interceptor
       {:name  (keyword interceptor-ns (name kind))
        :doc   "Given an interceptor name and list of headers to forward,
            return an interceptor that attaches those headers to reponses IFF
            they are in the request"
        :leave (fn [context]
                 (update-in context [:response :headers]
                            #(merge (select-keys (get-in context [:request :headers]) headers) %)))})
      {:kind kind})))
