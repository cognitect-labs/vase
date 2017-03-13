(ns vase-component.system
  "Component system map definition"
  (:require
   [com.stuartsierra.component :as component]
   [vase-component.api :as api]
   [vase-component.endpoint :as endpoint]))

(defn system
  []
  (component/system-map
   :api             (api/from-resource "/api" "petstore-simple.edn")
   :endpoint        (component/using (endpoint/http-endpoint) [:api])))
