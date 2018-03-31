(ns com.cognitect.vase.spec
  "Contains the clojure.spec.alpha definitions for the Vase
  application specification."
  (:require [io.pedestal.interceptor :as interceptor]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

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
                            :handler (s/alt :fn fn? :interceptors (s/coll-of ::interceptor :kind vector?))
                            :route-name (s/? (s/cat :_ #(= :route-name %) :name keyword?))
                            :constraints (s/? (s/cat :_ #(= :constraints %)
                                                     :constraints (s/map-of keyword?
                                                                            #(instance? java.util.regex.Pattern %))))))
(s/def ::route-table (s/* (s/spec ::route-table-route)))

;; -- Vase app-specs --
(s/def ::activated-apis  (s/or :base keyword?
                               :top-level (s/+ keyword?)))
(s/def ::datomic-uri (s/with-gen (s/and string? valid-uri? #(.startsWith % "datomic"))
                       #(gen/return (str "datomic:mem://" (java.util.UUID/randomUUID)))))

;; -- descriptor apis specs --
(s/def :vase.api/schemas (s/+ qualified-keyword?))
(s/def :vase.api/forward-headers (s/+ string?))

;; -- routes --
(s/def ::get (s/or :one ::interceptor
                   :many (s/+ ::interceptor)))
(s/def ::put (s/or :one ::interceptor
                   :many (s/+ ::interceptor)))
(s/def ::post (s/or :one ::interceptor
                    :many (s/+ ::interceptor)))
(s/def ::delete (s/or :one ::interceptor
                      :many (s/+ ::interceptor)))
(s/def ::head (s/or :one ::interceptor
                    :many (s/+ ::interceptor)))
(s/def ::options (s/or :one ::interceptor
                       :many (s/+ ::interceptor)))

(s/def ::action (s/and (s/keys :opt-un [::get ::put ::post ::delete ::head ::options])
                       #(not-empty (select-keys % [:get :put :post :delete :head :options]))))
(s/def :vase.api/routes (s/map-of valid-uri? ::action))

(s/def :vase.api/interceptors (s/+ ::interceptor))

(s/def :vase/apis (s/map-of qualified-keyword?
                            (s/keys :req [:vase.api/routes]
                                    :opt [:vase.api/schemas
                                          :vase.api/forward-headers
                                          :vase.api/interceptors])))

;; -- descriptor app specs --
;; -- norms --
(s/def ::tx (s/* (s/or :_ vector? :_ map?)))
(s/def :vase.norm/txes (s/* (s/spec ::tx)))
(s/def :vase.norm/requires (s/* qualified-keyword?))
(s/def :vase/norms (s/map-of qualified-keyword? (s/keys :req [:vase.norm/txes]
                                                        :opt [:vase.norm/requires])))

(s/def :vase/specs (s/map-of qualified-keyword? any?))

;; -- The descriptor spec --
(s/def ::descriptor (s/keys :req [:vase/apis]
                            :opt [:vase/norms
                                  :vase/specs]))

;; -- Vase app-spec --
(s/def ::spec (s/keys :req-un [::activated-apis ::descriptor ::datomic-uri]))
