(defproject com.cognitect/pedestal.vase "0.9.0-SNAPSHOT"
  :description "Vase: Pedestal API Container"
  :url "https://github.com/cognitect-labs/pedestal.vase"
  :dependencies [;; Platform
                 [org.clojure/clojure "1.9.0-alpha13"]

                 ;; Datomic
                 [com.datomic/datomic-free "0.9.5404" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                                                   [com.fasterxml.jackson.core/jackson-databind]
                                                                   [joda-time]
                                                                   [org.slf4j/slf4j-nop]]]
                 [io.rkn/conformity "0.4.0" :exclusions [com.datomic/datomic-free]]

                 ;; Pedestal
                 [io.pedestal/pedestal.service "0.5.1" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                                                    [com.fasterxml.jackson.core/jackson-databind]
                                                                    [com.fasterxml.jackson.datatype/jackson-datatype-json-org]]]

                 ;; Cleanup
                 [commons-codec "1.10"]
                 [joda-time "2.9.4"]
                 [com.fasterxml.jackson.core/jackson-core "2.8.3"]
                 [com.fasterxml.jackson.core/jackson-databind "2.8.3"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-json-org "2.8.3"]
                 [cheshire "5.6.3" :exclusions [[com.fasterxml.jackson.core/jackson-core]]]]
  :profiles {:srepl {:jvm-opts ^:replace ["-XX:+UseG1GC"
                                          "-Dclojure.server.repl={:port 5555 :accept clojure.core.server/repl}"]}
             :dev {:aliases {"dumbrepl" ["trampoline" "run" "-m" "clojure.main/main"]
                             "srepl" ["with-profile" "srepl" "trampoline" "run" "-m" "clojure.main/main"]}
                   :source-paths ["dev"]
                   :resource-paths ["config"
                                    "resources"
                                    "test/resources"]
                   :dependencies [[org.clojure/tools.trace "0.7.9"]
                                  [org.clojure/tools.namespace "0.2.10"]
                                  [io.pedestal/pedestal.jetty "0.5.1"]
                                  [org.clojure/test.check "0.9.0"]
                                   ;; Logging
                                  [org.slf4j/slf4j-api "1.7.21"]
                                  [ch.qos.logback/logback-classic "1.1.7" :exclusions [[org.slf4j/slf4j-api]]]
                                  ;[net.openhft/chronicle-logger-logback "1.1.0" :exclusions [[org.slf4j/slf4j-api]]]
                                  [org.slf4j/jul-to-slf4j "1.7.21"]
                                  [org.slf4j/jcl-over-slf4j "1.7.21"]
                                  [org.slf4j/log4j-over-slf4j "1.7.21"]]}
             :test {:dependencies [[org.clojure/test.check "0.9.0"]
                                   [io.pedestal/pedestal.service-tools "0.5.1" :exclusions [[org.slf4j/log4j-over-slf4j]
                                                                                            [org.slf4j/jul-to-slf4j]
                                                                                            [org.slf4j/jcl-over-slf4j]]]]
                    :resource-paths ["resources"
                                     "test/resources"]}}
  :min-lein-version "2.0.0")
