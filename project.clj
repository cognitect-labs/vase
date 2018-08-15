(defproject com.cognitect/pedestal.vase "0.9.4-SNAPSHOT"
  :description "Vase: Pedestal API Container"
  :url "https://github.com/cognitect-labs/vase"
  :dependencies [;; Platform
                 [org.clojure/clojure "1.9.0"]

                 ;; Datomic
                 [com.datomic/datomic-free "0.9.5697" :exclusions [[org.slf4j/slf4j-api]
                                                                   [org.slf4j/slf4j-nop]]]
                 [io.rkn/conformity "0.5.1" :exclusions [com.datomic/datomic-free]]

                 ;; Tmp. Work around problem also fixed in pedestal.service 0.5.3-SNAPSHOT
                 [org.clojure/core.async "0.4.474"]

                 ;; Pedestal
                 [io.pedestal/pedestal.service "0.5.3"]
                 [io.pedestal/pedestal.jetty "0.5.3"]

                 [com.cognitect/fern "0.1.3"]

                 ;; Nice errors
                 [expound "0.3.1"]

                 ;; Cleanup
                 [commons-codec "1.11"]
                 [cheshire "5.8.0"]]
  :main          com.cognitect.vase.main
  :pedantic? :warn
  :uberjar-name "vase-standalone.jar"
  :plugins [[camechis/deploy-uberjar "0.3.0"]]
  :jvm-opts ~(let [version     (System/getProperty "java.version")
                   [major _ _] (clojure.string/split version #"\.")]
               (if (>= (java.lang.Integer/parseInt major) 9)
                 ["--add-modules" "java.xml.bind"]
                 []))
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
                                   [io.pedestal/pedestal.service-tools "0.5.3" :exclusions [[org.slf4j/log4j-over-slf4j]
                                                                                            [org.slf4j/jul-to-slf4j]
                                                                                            [org.slf4j/jcl-over-slf4j]]]]
                    :resource-paths ["resources"
                                     "test/resources"]}}
  :min-lein-version "2.0.0")
