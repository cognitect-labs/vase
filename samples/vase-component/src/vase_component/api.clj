(ns vase-component.api
  (:require [com.stuartsierra.component :as component]
            [com.cognitect.vase :as vase]))

(defrecord VaseAPI [api-root specs routes]
  component/Lifecycle
  (start [this]
    (vase/ensure-schema specs)
    (vase/specs specs)
    (assoc this :routes (vase/routes api-root specs)))

  (stop [this]
    (assoc this :routes nil)))

(defn from-resource [api-root resource-name]
  (->VaseAPI api-root [(vase/load-edn-resource resource-name)] nil))

(defn datomic-uri [api]
  (-> api :specs first :datomic-uri))
