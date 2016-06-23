(ns vase
  (:require [vase.routes :as routes]
            [vase.util :as util]
            [vase.literals]
            [vase.spec]
            [clojure.spec :as spec]))

(defn load-descriptor
  "Given a resource name, loads a descriptor, using the proper readers to get
   support for Vase literals."
  [res]
  (util/edn-resource res))

(defn extract-specs
  [descriptor]
  (doseq [[app-name app-map] descriptor]
    (when-let [specs (:vase.specs app-map)]
      (doseq [[k specv] specs]
        (let [sv (cond
                   (spec/spec? specv) specv
                   (list? specv) (eval specv)
                   (symbol? specv) (resolve specv)
                   :else specv)]
          (eval `(spec/def ~k ~sv)))))))

(defn routes
  "Return a seq of route vectors for Pedestal's table routing syntax. Routes
  will all begin with `api-root/:app-name/:version`.

  `spec-or-specs` is either a single spec (as a map) or a collection of specs.

  The routes will support all the operations defined in the
  spec. Callers should treat the format of these routes as
  opaque. They may change in number, quantity, or layout."
  ([api-root spec-or-specs]
   (routes api-root spec-or-specs {}))
  ([api-root spec-or-specs opts]
   (let [{:keys [make-interceptors-fn]
          :or {make-interceptors-fn identity}} opts
         specs     (if (sequential? spec-or-specs) spec-or-specs [spec-or-specs])
         expanded-specs (mapcat (fn [spec]
                                  (if (sequential? (:version spec))
                                    (mapv #(assoc spec :version %) (:version spec))
                                    [spec]))
                                specs)
         routes    (mapcat (partial routes/spec-routes api-root make-interceptors-fn) expanded-specs)
         api-route (routes/api-description-route
                     api-root
                     make-interceptors-fn
                     routes
                     :describe-apis)]
     (cons api-route routes))))

(spec/fdef routes
           :args (spec/or :no-options (spec/cat :api-route vase.spec/valid-uri?
                                                :spec-or-specs (spec/or :single-spec ::vase.spec/spec
                                                                        :multiple-specs (spec/* ::vase.spec/spec)))
                          :with-options (spec/cat :api-route vase.spec/valid-uri?
                                                  :spec-or-specs (spec/or :single-spec ::vase.spec/spec
                                                                          :multiple-specs (spec/* ::vase.spec/spec))
                                                  :opts map?))
           :ret  ::vase.spec/route-table)
