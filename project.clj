(defproject
  :description "Consumer Reports On-demand Container Service"
  :url "https://github.com/relevance/cr-ocs"
  :dependencies [;; Platform
                 [org.clojure/clojure "1.7.0-alpha1"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]

                 ;; Datomic
                 [com.datomic/datomic-pro "0.9.4894" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                                                  [org.slf4j/slf4j-nop]
                                                                  [commons-codec]
                                                                  [org.jboss.logging/jboss-logging]
                                                                  [org.jgroups/jgroups]]]
                 [io.rkn/conformity "0.3.0" :exclusions [com.datomic/datomic-free]]

                 [cheshire "5.3.1" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [com.fasterxml.jackson.core/jackson-core "2.3.1"]
                 [me.raynes/conch "0.5.0"] ;; launching transactors on startup

                 ;; Deployment
                 [io.pedestal/pedestal.jetty "0.3.0"]
                 [org.immutant/immutant "1.1.4" :exclusions [[org.jboss.logging/jboss-logging]]]
                 [org.jboss.logging/jboss-logging "3.1.2.GA"]
                 [commons-codec "1.9"]
                 [clj-time "0.6.0"]
                 [org.clojure/data.json "0.2.5"]

                 ;; Pedestal
                 [org.slf4j/jul-to-slf4j "1.7.7"]
                 [org.slf4j/jcl-over-slf4j "1.7.7"]
                 [org.slf4j/log4j-over-slf4j "1.7.7"]
                 [io.pedestal/pedestal.service "0.3.0"]
                 [io.pedestal/pedestal.service-tools "0.3.0" :exclusions [[org.slf4j/log4j-over-slf4j]
                                                                          [org.slf4j/jul-to-slf4j]
                                                                          [org.slf4j/jcl-over-slf4j]]]

                 [ohpauleez/themis "0.1.1"]
                 [com.netflix.hystrix/hystrix-clj "1.4.0-RC4" :exclusions [[org.clojure/clojure]
                                                                           [com.netflix.rxjava/rxjava-core]]]
                 [com.netflix.rxjava/rxjava-core "0.20.0"]]
  :plugins [[lein-immutant "1.2.2" :exclusions [org.clojure/data.json]]]
  :profiles {:dev {:source-paths ["dev"]
                   :resource-paths ["config"
                                    "resources"
                                    ;"datomic/resources"
                                    ;"datomic/datomic-pro-0.9.4880.6.jar"
                                    "test/resources"]

                   :dependencies [[org.clojure/tools.trace "0.7.6"]
                                  [org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/test.check "0.5.9"]]}
             :test {:resource-paths ["resources"
                                    ;"datomic/resources"
                                    ;"datomic/datomic-pro-0.9.4880.6.jar"
                                    "test/resources"]
                    :jvm-opts ^:replace ["-d64" "-server" "-XX:+UseConcMarkSweepGC" "-XX:+UseParNewGC" "-XX:+UseCompressedOops" "-XX:+ExplicitGCInvokesConcurrent" "-XX:+CMSParallelRemarkEnabled" "-Dcrocsconfig=./test/resources/system.edn"]}
             :prod {:resource-paths ["config"
                                     "resources"
                                     ;"datomic/resources"
                                     ;"datomic/datomic-pro-0.9.4880.6.jar"
                                     ]
                    :jvm-opts ^:replace ["-d64" "-server" "-XX:+UseConcMarkSweepGC" "-XX:+UseParNewGC" "-XX:+UseCompressedOops" "-XX:+ExplicitGCInvokesConcurrent" "-XX:+CMSParallelRemarkEnabled" "-Dcrocsconfig=./config/prod_system.edn"]}
             :uberjar {:main ^{:skip-aot true}  [cr-ocs.server]}}
  :min-lein-version "2.0.0"
  :aliases {"run-dev" ["trampoline" "run" "-m" "cr-ocs.server/run-dev"]
            "run-prod" ^{:skip-aot true} ["trampoline" "with-profile" "prod" "run" "-m" "cr-ocs.prod/-main"]
            "run-jboss" ^{:skip-aot true} ["with-profile" "prod" "immutant" "run"]}
  ;; Unless we trampoline, we can't register shutdown hooks, which are used to shutdown the transactor
  :main ^{:skip-aot true} cr-ocs.server
  :global-vars  {*warn-on-reflection* true
                 *assert* true}
  :pedantic? :abort

  ;; Plugin settings
  :immutant {:init immutant.init/start
             :context-path "/"
             :nrepl-port 0}

  ; :java-cmd "/home/example_user/bin/java1.7"
  ; ; Different JVM options for performance
  ; :jvm-opts ["-Xmx1g"]
  ; ; JDK 1.7
  ; ; Ideal for heap sizes around 4GB or larger, where stop-the-world is acceptable
  ; :jvm-opts ["-d64" "-server" "-XX:+UseG1GC" "-XX:+ExplicitGCInvokesConcurrent" "-XX:+UseCompressedStrings" "-XX:MaxGCPauseMillis=50"]
  ; ; JDK 1.6
  ; :jvm-opts ["-d64" "-server" "-XX:+UseConcMarkSweepGC" "-XX:+UseParNewGC" "-XX:+UseCompressedOops" "-XX:+ExplicitGCInvokesConcurrent" "-XX:+CMSParallelRemarkEnabled"]
  ; :jvm-opts ["-server" "-Xmx1g" "-XX:+UseConcMarkSweepGC" "-XX:+UseParNewGC" "-XX:+UseCompressedOops"]
  ; :jvm-opts ["-server" "-Xmx50mb" "-XX:+UseConcMarkSweepGC" "-XX:+UseParNewGC" "-XX:+UseCompressedOops"]
  ; :jvm-opts ^:replace []
  :jvm-opts ^:replace ["-d64" "-server" "-XX:+UseConcMarkSweepGC" "-XX:+UseParNewGC" "-XX:+UseCompressedOops" "-XX:+ExplicitGCInvokesConcurrent" "-XX:+CMSParallelRemarkEnabled"])

