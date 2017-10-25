(defproject com.cognitect/pedestal.vase "0.9.3-SNAPSHOT"
  :description "Vase: Pedestal API Container"
  :url "https://github.com/cognitect-labs/pedestal.vase"
  :dependencies [;; Platform
                 [org.clojure/clojure "1.9.0-beta3"]

                 ;; Datomic
                 [com.datomic/datomic-free "0.9.5561.62" :exclusions [[org.slf4j/slf4j-api]
                                                                      [org.slf4j/slf4j-nop]]]
                 [io.rkn/conformity "0.5.1" :exclusions [com.datomic/datomic-free]]

                 ;; Tmp. Work around problem also fixed in pedestal.service 0.5.3-SNAPSHOT
                 [org.clojure/core.async "0.3.443"]

                 ;; Pedestal
                 [io.pedestal/pedestal.service "0.5.3"]

                 ;; Cleanup
                 [commons-codec "1.11"]
                 [cheshire "5.8.0"]]
  :pedantic? :abort
  :profiles {:srepl {:jvm-opts ^:replace ["-XX:+UseG1GC"
                                          "-Dclojure.server.repl={:port 5555 :accept clojure.core.server/repl}"]}
             :dev {:aliases {"crepl" ["trampoline" "run" "-m" "clojure.main/main"]
                             "srepl" ["with-profile" "srepl" "trampoline" "run" "-m" "clojure.main/main"]}
                   :source-paths ["dev"]
                   :resource-paths ["config"
                                    "resources"
                                    "test/resources"]
                   :dependencies [[org.clojure/tools.trace "0.7.9"]
                                  [org.clojure/tools.namespace "0.3.0-alpha3" :exclusions [[org.clojure/tools.reader]]]
                                  [org.clojure/tools.reader "1.1.0"]
                                  [io.pedestal/pedestal.jetty "0.5.3"]
                                  [org.clojure/test.check "0.9.0"]
                                   ;; Logging
                                  [org.slf4j/slf4j-api "1.7.25"]
                                  [ch.qos.logback/logback-classic "1.2.3" :exclusions [[org.slf4j/slf4j-api]]]
                                  [org.slf4j/jul-to-slf4j "1.7.25"]
                                  [org.slf4j/jcl-over-slf4j "1.7.25"]
                                  [org.slf4j/log4j-over-slf4j "1.7.25"]]}
             :test {:dependencies [[org.clojure/test.check "0.9.0"]
                                   [io.pedestal/pedestal.service-tools "0.5.3" :exclusions [[org.slf4j/log4j-over-slf4j]
                                                                                            [org.slf4j/jul-to-slf4j]
                                                                                            [org.slf4j/jcl-over-slf4j]]]]
                    :resource-paths ["resources"
                                     "test/resources"]}}
  :min-lein-version "2.0.0")
