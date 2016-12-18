(defproject petstore-full "0.9.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha13"]
                 [io.pedestal/pedestal.service "0.5.1"]
                 [io.pedestal/pedestal.jetty "0.5.1"]
                 [ch.qos.logback/logback-classic "1.1.7" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.21"]
                 [org.slf4j/jcl-over-slf4j "1.7.21"]
                 [org.slf4j/log4j-over-slf4j "1.7.21"]
                 [com.datomic/datomic-pro "0.9.5544" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                                                  [com.fasterxml.jackson.core/jackson-databind]
                                                                  [joda-time]]]
                 [com.cognitect/pedestal.vase "0.9.0-SNAPSHOT" :exclusions [com.datomic/datomic-free]]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :plugins [[info.sunng/lein-bootclasspath-deps "0.2.0"]]
  :boot-dependencies [];; See: https://www.eclipse.org/jetty/documentation/current/alpn-chapter.html#alpn-versions
                      ;[org.mortbay.jetty.alpn/alpn-boot "8.1.4.v20150727"] ;; JDK 1.8.0_51
                      ;[org.mortbay.jetty.alpn/alpn-boot "8.1.3.v20150130"] ;; JDK 1.8.0_31/40/45
                      ;[org.mortbay.jetty.alpn/alpn-boot "8.1.2.v20141202"] ;; JDK 1.8.0_25
                      ;[org.mortbay.jetty.alpn/alpn-boot "8.1.0.v20141016" :prepend true] ;; JDK 1.8.0_20 (1.8 up to _20)

  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "petstore-full.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.1"]]}
             :uberjar {:aot [petstore-full.server]}}
  :main ^{:skip-aot true} petstore-full.server)
