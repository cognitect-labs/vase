(ns vase.spec
  "Contains the clojure.spec definitions for the Vase
   application specification."
  (:require [io.pedestal.interceptor :as interceptor]
            [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

;; -- Predicates --
(defn valid-uri?
  "Returns true if v is a non-empty string representation of a uri."
  [v]
  (try
    (boolean (and (string? v) (not-empty v) (java.net.URI. v)))
    (catch java.net.URISyntaxException e false)))

;; -- Pedestal-specs --
(s/def ::interceptor #(satisfies? interceptor/IntoInterceptor %))
(s/def ::route-table-route (s/cat :path valid-uri?
                            :verb #{:any :get :put :post :delete :patch :options :head}
                            :handler (s/alt :fn fn? :interceptors (s/coll-of ::interceptor []))
                            :route-name (s/? (s/cat :_ #(= :route-name %) :name keyword?))
                            :constraints (s/? (s/cat :_ #(= :constraints %)
                                                     :constraints (s/map-of keyword?
                                                                            #(instance? java.util.regex.Pattern %))))))
(s/def ::route-table (s/* (s/spec ::route-table-route)))

;; -- Vase specs --
(s/def ::app-name keyword?)
(s/def ::version  (s/or :base keyword?
                        :top-level (s/+ keyword?)))
(s/def ::datomic-uri (s/with-gen (s/and string? valid-uri? #(.startsWith % "datomic"))
                       #(gen/return (str "datomic:mem://" (java.util.UUID/randomUUID)))))

;; -- descriptor api version specs --
(s/def ::schemas (s/* qualified-keyword?))
(s/def ::forward-headers (s/* (s/and string? not-empty)))

;; -- routes --
(s/def ::get ::interceptor)
(s/def ::put ::interceptor)
(s/def ::post ::interceptor)
(s/def ::delete ::interceptor)
(s/def ::head ::interceptor)
(s/def ::options ::interceptor)

(s/def ::action (s/and (s/keys ::opt-un [::get ::put ::post ::delete ::head ::options])
                       #(not-empty (select-keys % [:get :put :post :delete :head :options]))))
(s/def ::route (s/cat :path valid-uri? :actions ::action))
(s/def ::routes (s/* (s/spec ::route)))

(s/def ::interceptors (s/+ ::interceptor))

(s/def ::api-version (s/keys :req-un [::routes] :opt-un [::schemas ::forward-headers ::interceptors]))

;; -- descriptor app specs --
;; -- norms --
(s/def ::tx (s/* (s/or :_ vector? :_ map?)))
(s/def ::txes (s/* (s/spec ::tx)))
(s/def ::requires (s/* qualified-keyword?))
(s/def ::norms (s/map-of qualified-keyword? (s/keys ::req-un [::txes] ::opt-un [::requires])))

(s/def ::specs (s/map-of qualified-keyword? ::s/any))

(s/def ::app (s/and (s/map-of keyword? (s/or :api-version ::api-version
                                             :norms ::norms
                                             :specs ::specs))
                    (s/keys :req-un [::norms]
                            :opt-un [::specs])))

;; -- The descriptor spec --
(s/def ::descriptor (s/and (s/map-of ::app-name ::app) not-empty))

;; -- Vase spec --
(s/def ::spec (s/keys :req-un [::app-name ::version ::descriptor ::datomic-uri]))
