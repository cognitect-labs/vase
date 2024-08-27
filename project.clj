(defproject com.cognitect/pedestal.vase "0.9.4-SNAPSHOT"
  :description "Vase: Pedestal API Container"
  :url "https://github.com/cognitect-labs/vase"
  :dependencies [;; Platform
                 [org.clojure/clojure "1.11.4"]

                 ;; Datomic
                 [com.datomic/peer "1.0.7187" :exclusions [[org.slf4j/slf4j-api]
                                                           [org.slf4j/slf4j-nop]
                                                           [org.eclipse.jetty/jetty-util]
                                                           [org.eclipse.jetty/jetty-client]]]
                 [com.datomic/client-cloud "1.0.130" :exclusions [commons-logging com.cognitect/transit-clj]]
                 [io.rkn/conformity "0.5.4" :exclusions [com.datomic/datomic-free]]

                 ;; Pedestal
                 [io.pedestal/pedestal.service "0.6.3"]
                 [io.pedestal/pedestal.jetty "0.6.3"]

                 ;; Pin Jetty versions to avoid conflict between Datomic and Pedestal
                 [org.eclipse.jetty/jetty-client "9.4.55.v20240627"]

                 ;; Pin core.async to avoid conflict between Datomic and Pedestal
                 [org.clojure/core.async "1.6.681"]

                 ;; Configuration
                 [com.cognitect/fern "0.1.6" :exclusions [fipp]]
                 [fipp/fipp "0.6.26"]

                 ;; Replace Java EE module for JDK 11
                 [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]

                 ;; Cleanup
                 [commons-codec "1.16.0"]
                 [cheshire "5.11.0"]]

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
                                  [org.clojure/tools.namespace "1.5.0" :exclusions [[org.clojure/tools.reader]]]
                                  [org.clojure/tools.reader "1.3.6"]
                                  [org.clojure/test.check "0.9.0"]
                                   ;; Logging
                                  [org.slf4j/slf4j-api "1.7.36"]
                                  [ch.qos.logback/logback-classic "1.4.6" :exclusions [[org.slf4j/slf4j-api]]]
                                  [org.slf4j/jul-to-slf4j "1.7.36"]
                                  [org.slf4j/jcl-over-slf4j "1.7.36"]
                                  [org.slf4j/log4j-over-slf4j "1.7.36"]]}
             :test {:dependencies [[org.clojure/test.check "0.9.0"]
                                   [io.pedestal/pedestal.service-tools "0.6.3" :exclusions [[org.slf4j/log4j-over-slf4j]
                                                                                            [org.slf4j/jul-to-slf4j]
                                                                                            [org.slf4j/jcl-over-slf4j]]]]
                    :resource-paths ["resources"
                                     "test/resources"]}}
  :min-lein-version "2.0.0")
