(defproject com.cognitect/pedestal.vase "0.9.4-SNAPSHOT"
  :description "Vase: Pedestal API Container"
  :url "https://github.com/cognitect-labs/vase"
  :exclusions [org.eclipse.jetty/jetty-io
               org.eclipse.jetty/jetty-util
               org.eclipse.jetty/jetty-http
               org.eclipse.jetty/jetty-client]
  :dependencies [;; Platform
                 [org.clojure/clojure "1.10.1"]

                 ;; Datomic
                 [com.datomic/datomic-free "0.9.5697" :exclusions [[org.slf4j/slf4j-api]
                                                                   [org.slf4j/slf4j-nop]]]

                 [com.datomic/client-cloud "0.8.102" :exclusions [commons-logging
                                                                  org.clojure/data.priority-map
                                                                  org.clojure/core.cache]]
                 
                 [io.rkn/conformity "0.5.4" :exclusions [com.datomic/datomic-free]]

                 ;; Pedestal
                 [io.pedestal/pedestal.service "0.5.8" :exclusions [org.clojure/tools.analyzer.jvm]]
                 [io.pedestal/pedestal.jetty "0.5.8"]

                 ;; Pin to avoid conflicts between Datomic and Pedestal
                 [org.eclipse.jetty/jetty-client "9.4.18.v20190429"]
                 [org.clojure/core.async "1.3.610"]
                 [org.clojure/tools.analyzer.jvm "1.1.0"]
                 [org.clojure/core.cache "1.0.207"]
                 
                 ;; Pin joda-time to avoid conflict between Datomic and Pedestal
                 [joda-time "2.10.6"]

                 ;; Configuration
                 [com.cognitect/fern "0.1.6"]
                 [fipp "0.6.17"] ;; pin to avoid puget/fipp conflicts
                 [org.clojure/core.rrb-vector "0.0.14"] 

                 ;; Replace Java EE module for JDK 11
                 [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]

                 ;; Cleanup
                 [commons-codec "1.14"]
                 [cheshire "5.10.0"]]

  :main          ^:skip-aot com.cognitect.vase.main
  :pedantic?     :warn
  :uberjar-name  "vase-standalone.jar"
  :plugins       []
  :jvm-opts      ~(let [version     (System/getProperty "java.version")
                        [major _ _] (clojure.string/split version #"\.")]
                    (if (<= 9 (java.lang.Integer/parseInt major) 10)
                      ["--add-modules" "java.xml.bind"]
                      []))
  :test-selectors {:default (complement :integration)
                   :cloud   :integration
                   :all     (constantly true)}

  :profiles {:srepl {:jvm-opts ^:replace ["-XX:+UseG1GC"
                                          "-Dclojure.server.repl={:port 5555 :accept clojure.core.server/repl}"]}
             :dev {:aliases {"crepl" ["trampoline" "run" "-m" "clojure.main/main"]
                             "srepl" ["with-profile" "srepl" "trampoline" "run" "-m" "clojure.main/main"]}
                   :source-paths ["dev"]
                   :resource-paths ["config"
                                    "resources"
                                    "test/resources"]
                   :dependencies [[org.clojure/tools.trace "0.7.10"]
                                  [org.clojure/tools.namespace "1.0.0" :exclusions [[org.clojure/tools.reader]]]
                                  [org.clojure/tools.reader "1.3.2"]
                                  [org.clojure/test.check "1.1.0"]
                                   ;; Logging
                                  [org.slf4j/slf4j-api "1.7.30"]
                                  [ch.qos.logback/logback-classic "1.2.3" :exclusions [[org.slf4j/slf4j-api]]]
                                  [org.slf4j/jul-to-slf4j "1.7.30"]
                                  [org.slf4j/jcl-over-slf4j "1.7.30"]
                                  [org.slf4j/log4j-over-slf4j "1.7.30"]]}
             :test {:dependencies [[org.clojure/test.check "1.1.0"]
                                   [io.pedestal/pedestal.service-tools "0.5.8" :exclusions [[org.slf4j/log4j-over-slf4j]
                                                                                            [org.slf4j/jul-to-slf4j]
                                                                                            [org.slf4j/jcl-over-slf4j]]]]
                    :resource-paths ["resources"
                                     "test/resources"]}}
  :min-lein-version "2.0.0")
