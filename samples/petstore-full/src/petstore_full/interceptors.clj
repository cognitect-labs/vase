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

(def encrypt-passwords
  (i/interceptor
   {:name ::encrypt-passwords
    :enter (fn [context]
             (let [payloads (get-in context [:request :json-params :payload])
                   payloads (map (fn [m] (if (:petstore.user/password m)
                                          (update m :petstore.user/password encrypt)
                                          m)) payloads)]
               (assoc-in context [:request :json-params :payload] payloads)))}))

(def query-cipher-password
  (i/interceptor
   {:name ::query-cipher-password
    :leave (fn [context]
             (let [user (ffirst (get-in context [:response :body]))
                   user (if (:petstore.user/password user)
                          (assoc user :petstore.user/password "**********")
                          user)]
               (assoc-in context [:response :body] user)))}))

(def cipher-passwords
  (i/interceptor
   {:name ::cipher-passwords
    :leave (fn [context]
             (let [users (get-in context [:response :body :whitelist])
                   users (map (fn [m] (if (:petstore.user/password m)
                                       (assoc m :petstore.user/password "**********")
                                       m)) users)]
               (assoc-in context [:response :body] users)))}))

(def authenticate-user
  (i/interceptor
   {:name ::authenticate-user
    :leave (fn [context]
             (let [[username encrypted] (-> context :response :body first)
                   saved-password (decrypt encrypted)]
               ;; It's a good idea to add info in session or cookie
               ;; if a user is succesfully logged in.
               (if (= saved-password (get-in context [:request :params :password]))
                 (assoc-in context [:response :body] (str "logged in as: " username))
                 (-> context
                     (assoc-in [:response :body] "login failed")
                     (assoc-in [:response :status] 400)))))}))

(def log-off-user
  (i/interceptor
   {:name ::log-off-user
    :enter (fn [context]
             ;; Possibly, this function deletes some info about logged
             ;; in state from session or cookie
             context)}))
