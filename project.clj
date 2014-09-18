(defproject
  :description "Consumer Reports On-demand Container Service"
  :url "https://github.com/relevance/vase"
  :dependencies [;; Platform
                 [org.clojure/clojure "1.6.0"]

                 ;; Datomic
                 [com.datomic/datomic-pro "0.9.4894" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                                                  [org.slf4j/slf4j-nop]
                                                                  [commons-codec]
                                                                  [org.jboss.logging/jboss-logging]
                                                                  [org.jgroups/jgroups]]]
                 [io.rkn/conformity "0.3.0" :exclusions [com.datomic/datomic-free]]

                 [cheshire "5.3.1"]

                 ;; Deployment
                 [org.jboss.logging/jboss-logging "3.1.2.GA"]
                 ;; Pedestal
                 [io.pedestal/pedestal.service "0.3.0"]
                 [ohpauleez/themis "0.1.1"]]
  :profiles {:dev {:source-paths ["dev"]
                   :resource-paths ["config"
                                    "resources"
                                    "test/resources"]

                   :dependencies [[org.clojure/tools.trace "0.7.6"]
                                  [org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/test.check "0.5.9"]]}
             :test {:dependencies [[io.pedestal/pedestal.jetty "0.3.0"]
                                   [org.slf4j/jul-to-slf4j "1.7.7"]
                                   [org.slf4j/jcl-over-slf4j "1.7.7"]
                                   [org.slf4j/log4j-over-slf4j "1.7.7"]
                                   [io.pedestal/pedestal.service-tools "0.3.0" :exclusions [[org.slf4j/log4j-over-slf4j]
                                                                                            [org.slf4j/jul-to-slf4j]
                                                                                            [org.slf4j/jcl-over-slf4j]]]]
                    :resource-paths ["resources"
                                    "test/resources"]
                    :jvm-opts ^:replace ["-d64" "-server" "-XX:+UseConcMarkSweepGC" "-XX:+UseParNewGC" "-XX:+UseCompressedOops" "-XX:+ExplicitGCInvokesConcurrent" "-XX:+CMSParallelRemarkEnabled" "-Dcrocsconfig=./test/resources/system.edn"]}}
  :min-lein-version "2.0.0"
  :global-vars  {*warn-on-reflection* true
                 *assert* true}
  :pedantic? :abort)
