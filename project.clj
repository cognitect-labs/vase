(defproject com.cognitect/vase "0.1.0-SNAPSHOT"
  :description "Vase: Pedestal API Container"
  :url "https://github.com/relevance/vase"
  :dependencies [;; Platform
                 [org.clojure/clojure "1.9.0-alpha10"]
                 ;; Datomic
                 [com.datomic/datomic-free "0.9.5372" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                                                   [com.fasterxml.jackson.core/jackson-databind]
                                                                   [joda-time]
                                                                   [org.slf4j/slf4j-nop]]]
                 [io.rkn/conformity "0.4.0" :exclusions [com.datomic/datomic-free]]

                 ;; Pedestal
                 [io.pedestal/pedestal.service "0.5.0" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                                                    [com.fasterxml.jackson.core/jackson-databind]
                                                                    [com.fasterxml.jackson.datatype/jackson-datatype-json-org]]]

                 ;; Cleanup
                 [commons-codec "1.10"]
                 [joda-time "2.9.4"]
                 [com.fasterxml.jackson.core/jackson-core "2.7.1"]
                 [com.fasterxml.jackson.core/jackson-databind "2.7.1-1"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-json-org "2.7.1"]
                 [cheshire "5.6.1" :exclusions [[com.fasterxml.jackson.core/jackson-core]]]]
  :profiles {:dev {:source-paths ["dev"]
                   :resource-paths ["config"
                                    "resources"
                                    "test/resources"]

                   :dependencies [[org.clojure/tools.trace "0.7.9"]
                                  [org.clojure/tools.namespace "0.2.10"]
                                  [io.pedestal/pedestal.jetty "0.5.0"]
                                  [org.clojure/test.check "0.9.0"]
                                   ;; Logging
                                  [org.slf4j/slf4j-api "1.7.21"]
                                  [ch.qos.logback/logback-classic "1.1.7" :exclusions [[org.slf4j/slf4j-api]]]
                                  ;[net.openhft/chronicle-logger-logback "1.1.0" :exclusions [[org.slf4j/slf4j-api]]]
                                  [org.slf4j/jul-to-slf4j "1.7.21"]
                                  [org.slf4j/jcl-over-slf4j "1.7.21"]
                                  [org.slf4j/log4j-over-slf4j "1.7.21"]]}
             :test {:dependencies [[org.clojure/test.check "0.9.0"]
                                   [io.pedestal/pedestal.service-tools "0.5.0" :exclusions [[org.slf4j/log4j-over-slf4j]
                                                                                            [org.slf4j/jul-to-slf4j]
                                                                                            [org.slf4j/jcl-over-slf4j]]]]
                    :resource-paths ["resources"
                                     "test/resources"]}}
  :min-lein-version "2.0.0")
