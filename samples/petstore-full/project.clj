(defproject petstore-full "0.9.3-SNAPSHOT"
  :description "Vase sample application: Pet Store demo, full"
  :url "https://github.com/cognitect-labs/vase/tree/master/samples/petstore-full"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-beta3"]
                 [io.pedestal/pedestal.service "0.5.3"]
                 [io.pedestal/pedestal.jetty "0.5.3"]
                 [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [org.slf4j/log4j-over-slf4j "1.7.25"]
                 [com.cognitect/pedestal.vase "0.9.3-SNAPSHOT"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :plugins [[info.sunng/lein-bootclasspath-deps "0.2.0"]]
  :boot-dependencies [];; See: https://www.eclipse.org/jetty/documentation/current/alpn-chapter.html#alpn-versions
                      ;[org.mortbay.jetty.alpn/alpn-boot "8.1.4.v20150727"] ;; JDK 1.8.0_51
                      ;[org.mortbay.jetty.alpn/alpn-boot "8.1.3.v20150130"] ;; JDK 1.8.0_31/40/45
                      ;[org.mortbay.jetty.alpn/alpn-boot "8.1.2.v20141202"] ;; JDK 1.8.0_25
                      ;[org.mortbay.jetty.alpn/alpn-boot "8.1.0.v20141016" :prepend true] ;; JDK 1.8.0_20 (1.8 up to _20)

  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "petstore-full.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.3"]]}
             :uberjar {:aot [petstore-full.server]}}
  :main ^{:skip-aot true} petstore-full.server)
