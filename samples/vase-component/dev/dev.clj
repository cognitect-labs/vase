(ns dev
  (:require
   [clojure.core.async :as async :refer [<! <!! alt!!]]
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer [javadoc]]
   [clojure.pprint :refer [pprint]]
   [clojure.reflect :refer [reflect]]
   [clojure.repl :refer [apropos dir doc find-doc pst source]]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer [refresh refresh-all clear]]
   [com.stuartsierra.component :as component]
   [com.stuartsierra.component.repl :refer [system start stop reset set-init]]
   [vase-component.system :as system]
   [vase-component.endpoint :as endpoint]))

(defn dev-system
  [_]
  (-> (system/system)
      (update :http endpoint/dev-mode)))

(set-init dev-system)

(defn run-tests []
  (binding [clojure.test/*test-out* *out*]
    (clojure.test/run-all-tests #"vase-component.+-test")))

(s/check-asserts true)

(defmethod clojure.core/print-method
  clojure.core.async.impl.channels.ManyToManyChannel
  [ch writer]
  (.write writer "<Channel>"))
