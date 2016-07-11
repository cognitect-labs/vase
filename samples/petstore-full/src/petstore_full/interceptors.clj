(ns petstore-full.interceptors
  (:require [clojure.instant :as instant]
            [io.pedestal.interceptor :as i])
  (:import [javax.crypto Cipher SecretKey]
           [javax.crypto.spec SecretKeySpec]
           [java.util Base64]))

(def date-conversion
  (i/interceptor
   {:name ::date-conversion
    :enter (fn [context]
             (let [payloads (get-in context [:request :json-params :payload])
                   payloads (map (fn [m] (if (:petstore.order/shipDate m)
                                          (update m :petstore.order/shipDate instant/read-instant-date)
                                          m))
                                 payloads)]
               (assoc-in context [:request :json-params :payload] payloads)))}))

(def secret-key "It Is Secret Key")  ;; exactly 16 bytes
(def ^SecretKey skey (SecretKeySpec. (.getBytes secret-key "UTF-8") "AES"))
(def ^Cipher encryptor (doto (Cipher/getInstance "AES") (.init Cipher/ENCRYPT_MODE skey)))
(def ^Cipher decryptor (doto (Cipher/getInstance "AES") (.init Cipher/DECRYPT_MODE skey)))

(defn encrypt
  [s]
  (let [bytes (.doFinal encryptor (.getBytes s "UTF-8"))]
    (.encodeToString (Base64/getEncoder) bytes)))

(defn decrypt
  [s]
  (let [bytes (.decode (Base64/getDecoder) s)
        bytes (.doFinal decryptor bytes)]
    (String. bytes)))

(def encrypt-password
  (i/interceptor
   {:name ::encrypt-password
    :enter (fn [context]
             (let [payloads (get-in context [:request :json-params :payload])
                   payloads (map (fn [m] (if (:petstore.user/password m)
                                          (update m :petstore.user/password encrypt)
                                          m)) payloads)]
               (assoc-in context [:request :json-params :payload] payloads)))}))

(def cipher-password
  (i/interceptor
   {:name ::cipher-password
    :leave (fn [context]
             (prn ::cipher-password (get-in context [:response :body]))
             (let [user (ffirst (get-in context [:response :body]))
                   user (if (:petstore.user/password user)
                          (assoc user :petstore.user/password "**********")
                          user)]
               (assoc-in context [:response :body] user)))}))

(defn cipher-passwords
  [name]
  (i/interceptor
   {:name name
    :leave (fn [context]
             (prn ::cipher-password (get-in context [:response :body]))
             (let [users (get-in context [:response :body :whitelist])
                   users (map (fn [m] (if (:petstore.user/password m)
                                       (assoc m :petstore.user/password "**********")
                                       m)) users)]
               (assoc-in context [:response :body] users)))}))

(def cipher-passwords-after-post
  (cipher-passwords ::cipher-passwords-after-post))

(def cipher-passwords-after-delete
  (cipher-passwords ::cipher-passwords-after-delete))

(def authenticate-user
  (i/interceptor
   {:name ::authenticate-user
    :leave (fn [context]
             (let [[username encrypted] (-> context :response :body first)
                   saved-password (decrypt encrypted)]
               (if (= saved-password (get-in context [:request :params :password]))
                 (assoc-in context [:response :body] "success")
                 (-> context
                     (assoc-in [:response :body] "failure")
                     (assoc-in [:response :status] 400)))))}))
