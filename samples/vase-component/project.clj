; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
; which can be found in the file epl-v10.html at the root of this distribution.
;
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
;
; You must not remove this notice, or any other, from this software.

(defproject com.cognitect/vase-component "0.9.3-SNAPSHOT"
  :description "Sample application using Component library to set up system"
  :url "https://github.com/cognitect-labs/vase"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-beta3"]
                 [io.pedestal/pedestal.service "0.5.3"]
                 [io.pedestal/pedestal.jetty "0.5.3"]
                 [com.cognitect/pedestal.vase "0.9.3-SNAPSHOT"]
                 [com.stuartsierra/component "0.3.2"]]
  :main ^:skip-aot vase-component.main
  :resource-paths ["config", "resources"]
  :profiles {:dev {:dependencies [[com.stuartsierra/component.repl "0.2.0"]
                                  [org.clojure/tools.reader "1.1.0"]
                                  [org.clojure/tools.namespace "0.3.0-alpha3"]]
                   :source-paths ["dev"]}})
