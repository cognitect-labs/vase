(defproject com.cognitect/vase-redux "0.1.0-SNAPSHOT"
  :description "Vase: Pedestal API Container"
  :url "https://github.com/relevance/vase"
  :dependencies [;; Platform
                 [org.clojure/clojure "1.6.0"]

                 ;; Datomic
                 [com.datomic/datomic-free "0.9.5153" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                                                   [com.fasterxml.jackson.core/jackson-databind]
                                                                   [joda-time]]]
                 [io.rkn/conformity "0.3.4" :exclusions [com.datomic/datomic-free]]

                 ;; Pedestal
                 [io.pedestal/pedestal.service "0.4.0" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                                                    [com.fasterxml.jackson.core/jackson-databind]
                                                                    [com.fasterxml.jackson.datatype/jackson-datatype-json-org]]]
                 [ohpauleez/themis "0.1.1"]

                 ;; Cleanup
                 [commons-codec "1.9"]
                 [joda-time "2.6"]
                 [com.fasterxml.jackson.core/jackson-core "2.4.4"]
                 [com.fasterxml.jackson.core/jackson-databind "2.4.4"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-json-org "2.4.4"]
                 [cheshire "5.4.0" :exclusions [[com.fasterxml.jackson.core/jackson-core]]]]
  :profiles {:dev {:source-paths ["dev"]
                   :resource-paths ["config"
                                    "resources"
                                    "test/resources"]

                   :dependencies [[org.clojure/tools.trace "0.7.6"]
                                  [org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/test.check "0.5.9"]
                                  [io.pedestal/pedestal.jetty "0.4.0"]]}
             :test {:dependencies [[io.pedestal/pedestal.jetty "0.4.0"]
                                   ;; Logging
                                   [org.slf4j/slf4j-api "1.7.12"]
                                   [ch.qos.logback/logback-classic "1.1.3" :exclusions [[org.slf4j/slf4j-api]]]
                                   ;[net.openhft/chronicle-logger-logback "1.1.0" :exclusions [[org.slf4j/slf4j-api]]]
                                   [org.slf4j/jul-to-slf4j "1.7.12"]
                                   [org.slf4j/jcl-over-slf4j "1.7.12"]
                                   [org.slf4j/log4j-over-slf4j "1.7.12"]
                                   [io.pedestal/pedestal.service-tools "0.4.0" :exclusions [[org.slf4j/log4j-over-slf4j]
                                                                                            [org.slf4j/jul-to-slf4j]
                                                                                            [org.slf4j/jcl-over-slf4j]]]]
                    :resource-paths ["resources"
                                     "test/resources"]
                    :jvm-opts ^:replace ["-d64" "-server"
                                         "-XX:+UseConcMarkSweepGC" "-XX:+UseParNewGC"
                                         "-XX:+UseCompressedOops"
                                         "-XX:+ExplicitGCInvokesConcurrent"
                                         "-XX:+CMSParallelRemarkEnabled"
                                         "-Dvaseconfig=./test/resources/system.edn"]}}
  :min-lein-version "2.0.0"
  :global-vars  {*warn-on-reflection* true
                 *assert* true}
  :pedantic? :abort)
