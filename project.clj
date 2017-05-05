(defproject com.cognitect/pedestal.vase "0.9.2-SNAPSHOT"
  :description "Vase: Pedestal API Container"
  :url "https://github.com/cognitect-labs/pedestal.vase"
  :dependencies [;; Platform
                 [org.clojure/clojure "1.9.0-alpha14"]

                 ;; Datomic
                 [com.datomic/datomic-free "0.9.5554" :exclusions [[org.slf4j/slf4j-api]
                                                                   [org.slf4j/slf4j-nop]]]
                 [io.rkn/conformity "0.4.0" :exclusions [com.datomic/datomic-free]]

                 ;; Pedestal
                 [io.pedestal/pedestal.service "0.5.2"]

                 [com.cognitect/fern "0.1.0-SNAPSHOT"]

                 ;; Cleanup
                 [commons-codec "1.10"]
                 [cheshire "5.6.3"]]
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
                                  [org.clojure/tools.reader "1.0.0-beta4"]
                                  [io.pedestal/pedestal.jetty "0.5.2"]
                                  [org.clojure/test.check "0.9.0"]
                                   ;; Logging
                                  [org.slf4j/slf4j-api "1.7.22"]
                                  [ch.qos.logback/logback-classic "1.1.8" :exclusions [[org.slf4j/slf4j-api]]]
                                  ;[net.openhft/chronicle-logger-logback "1.1.0" :exclusions [[org.slf4j/slf4j-api]]]
                                  [org.slf4j/jul-to-slf4j "1.7.22"]
                                  [org.slf4j/jcl-over-slf4j "1.7.22"]
                                  [org.slf4j/log4j-over-slf4j "1.7.22"]]}
             :test {:dependencies [[org.clojure/test.check "0.9.0"]
                                   [io.pedestal/pedestal.service-tools "0.5.2" :exclusions [[org.slf4j/log4j-over-slf4j]
                                                                                            [org.slf4j/jul-to-slf4j]
                                                                                            [org.slf4j/jcl-over-slf4j]]]]
                    :resource-paths ["resources"
                                     "test/resources"]}}
  :min-lein-version "2.0.0")
