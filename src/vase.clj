(ns vase
  (:require [vase.routes :as r]
            [vase.util :as util]
            [vase.literals]
            [vase.descriptor]
            [clojure.spec :as s]))

(defn load
  "Given a resource name, loads a descriptor, using the proper readers to get
   support for Vase literals."
  [res]
  (util/edn-resource res))

(defn routes
  "Return a seq of route vectors for Pedestal's table routing syntax. Routes
   will all begin with `api-root/:app-name/:version`.

   `specs` is a collection of API specifications, as documented by `vase.spec/spec`

   The routes will support all the operations defined in the
   spec. Callers should treat the format of these routes as
   opaque. They may change in number, quantity, or layout."
  [api-root descriptions & {:keys [make-interceptors-fn] :or {make-interceptors-fn identity} :as opts}]
  (mapcat (partial r/descriptor-routes api-root make-interceptors-fn) descriptions))

(s/fdef routes
        :args (s/cat :api-route vase.descriptor/valid-uri?
                     :descs (s/spec (s/* :vase.descriptor/description)))
        :ret  ::vase.descriptor/route-table)
