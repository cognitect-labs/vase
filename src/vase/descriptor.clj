(ns vase.descriptor
  "Contains the clojure.spec definitions for the Vase
   application description."
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
(s/def ::route-table-route (s/cat
                            :path        valid-uri?
                            :verb        #{:any :get :put :post :delete :patch :options :head}
                            :handler     (s/alt :fn fn? :interceptors (s/coll-of ::interceptor []))
                            :route-name  (s/? (s/cat :_ #(= :route-name %) :name keyword?))
                            :constraints (s/? (s/cat :_ #(= :constraints %)
                                                     :constraints (s/map-of keyword?
                                                                            #(instance? java.util.regex.Pattern %))))))
(s/def ::route-table (s/* (s/spec ::route-table-route)))

;; -- Vase specs --
(s/def ::datomic-uri (s/with-gen (s/and string? valid-uri? #(.startsWith % "datomic"))
                       #(gen/return (str "datomic:mem://" (java.util.UUID/randomUUID)))))

;; -- routes --
(s/def ::get     ::interceptor)
(s/def ::put     ::interceptor)
(s/def ::post    ::interceptor)
(s/def ::delete  ::interceptor)
(s/def ::head    ::interceptor)
(s/def ::options ::interceptor)

(s/def ::action (s/keys :opt-un [::get ::put ::post ::delete ::head ::options]))
(s/def ::route  (s/cat :path valid-uri? :actions ::action))
(s/def ::routes (s/* (s/spec ::route)))

;; -- norms --
(s/def ::tx-data  (s/* (s/alt :_ vector? :_ map?)))
(s/def ::txes     (s/* (s/spec ::tx-data)))
(s/def ::requires (s/* qualified-keyword?))

;; -- Vase spec --
(s/def ::ident           keyword?)
(s/def ::schemas         (s/* qualified-keyword?))
(s/def ::forward-headers (s/* (s/and string? not-empty)))
(s/def ::norms           (s/* (s/keys :req [::ident] :opt [::requires ::txes])))
(s/def ::endpoints       (s/* (s/keys :req [::ident] :opt [::routes ::schemas ::forward-headers])))
(s/def ::description     (s/keys :req [::endpoints] :opt [::norms ::datomic-uri]))
