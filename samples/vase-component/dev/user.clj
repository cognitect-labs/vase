(ns user
  "Quick-start functions for interactive development. This file is
  automatically loaded by Clojure on startup.")

(defn dev
  "Loads and switches to the 'dev' namespace."
  []
  (require 'dev)
  (in-ns 'dev))

(defn go
  "Loads all code, starts the application, and switches to the 'dev'
  namespace."
  []
  (in-ns 'dev)
  (require 'com.stuartsierra.component.repl)
  ((resolve 'com.stuartsierra.component.repl/reset)))
