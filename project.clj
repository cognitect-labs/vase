(defproject com.cognitect/pedestal.vase "0.9.4-SNAPSHOT"
  :description "Vase: Pedestal API Container"
  :url "https://github.com/cognitect-labs/vase"
  :dependencies [;; Platform
                 [org.clojure/clojure "1.9.0"]

                 ;; Datomic
                 [com.datomic/datomic-free "0.9.5697" :exclusions [[org.slf4j/slf4j-api]
                                                                   [org.slf4j/slf4j-nop]
                                                                   [org.eclipse.jetty/jetty-util]
                                                                   [org.eclipse.jetty/jetty-client]]]
                 [com.datomic/client-cloud "0.8.63"]
                 [io.rkn/conformity "0.5.1" :exclusions [com.datomic/datomic-free]]

                 ;; Pedestal
                 [io.pedestal/pedestal.service "0.5.4"]
                 [io.pedestal/pedestal.jetty "0.5.4"]

                 ;; Pin Jetty versions to avoid conflict between Datomic and Pedestal
                 [org.eclipse.jetty/jetty-util "9.4.10.v20180503"]
                 [org.eclipse.jetty/jetty-client "9.4.10.v20180503"]

                 [com.cognitect/fern "0.1.3"]

                 ;; Nice errors
                 [expound "0.3.1"]

                 ;; Cleanup
                 [commons-codec "1.11"]
                 [cheshire "5.8.0"]]

  :main          ^:skip-aot com.cognitect.vase.main
  :pedantic?     :warn
  :uberjar-name  "vase-standalone.jar"
  :plugins       []
  :jvm-opts      ~(let [version     (System/getProperty "java.version")
                        [major _ _] (clojure.string/split version #"\.")]
                    (if (>= (java.lang.Integer/parseInt major) 9)
                      ["--add-modules" "java.xml.bind"]
                      []))
  :test-selectors {:default (complement :cloud)
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
                   :dependencies [[org.clojure/tools.trace "0.7.9"]
                                  [org.clojure/tools.namespace "0.3.0-alpha3" :exclusions [[org.clojure/tools.reader]]]
                                  [org.clojure/tools.reader "1.2.2"]
                                  [org.clojure/test.check "0.9.0"]
                                   ;; Logging
                                  [org.slf4j/slf4j-api "1.7.25"]
                                  [ch.qos.logback/logback-classic "1.2.3" :exclusions [[org.slf4j/slf4j-api]]]
                                  [org.slf4j/jul-to-slf4j "1.7.25"]
                                  [org.slf4j/jcl-over-slf4j "1.7.25"]
                                  [org.slf4j/log4j-over-slf4j "1.7.25"]]}
             :test {:dependencies [[org.clojure/test.check "0.9.0"]
                                   [io.pedestal/pedestal.service-tools "0.5.4" :exclusions [[org.slf4j/log4j-over-slf4j]
                                                                                            [org.slf4j/jul-to-slf4j]
                                                                                            [org.slf4j/jcl-over-slf4j]]]]
                    :resource-paths ["resources"
                                     "test/resources"]}}
  :min-lein-version "2.0.0")
