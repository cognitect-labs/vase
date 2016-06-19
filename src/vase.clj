(ns vase
  (:require [vase.routes :as r]
            [vase.util :as util]
            [vase.literals]
            [vase.spec]
            [clojure.spec :as s]))

(defn load
  "Given a resource name, loads a descriptor, using the proper readers to get
   support for Vase literals."
  [res]
  (util/edn-resource res))

(defn routes
  "Return a seq of route vectors for Pedestal's table routing syntax. Routes
   will all begin with `api-root/:app-name/:version`.

   `spec-or-specs` is either a single spec (as a map) or a collection of specs.

   The routes will support all the operations defined in the
   spec. Callers should treat the format of these routes as
   opaque. They may change in number, quantity, or layout."
  [api-root spec-or-specs & {:keys [make-interceptors-fn] :or {make-interceptors-fn identity} :as opts}]
  (let [specs     (if (sequential? spec-or-specs) spec-or-specs [spec-or-specs])
        routes    (mapcat (partial r/spec-routes api-root make-interceptors-fn) specs)
        api-route (r/api-description-route api-root make-interceptors-fn routes :describe-apis)]
    (cons api-route routes)))

(s/fdef routes
        :args (s/cat :api-route vase.spec/valid-uri?
                     :spec-or-specs (s/or :single-spec ::vase.spec/spec
                                          :multiple-specs (s/* ::vase.spec/spec)))
        :ret  ::vase.spec/route-table)
