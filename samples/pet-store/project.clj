(defproject pet-store "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha7"]
                 [io.pedestal/pedestal.service "0.5.1-SNAPSHOT"]
                 [io.pedestal/pedestal.jetty "0.5.1-SNAPSHOT"]
                 [ch.qos.logback/logback-classic "1.1.7" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.21"]
                 [org.slf4j/jcl-over-slf4j "1.7.21"]
                 [org.slf4j/log4j-over-slf4j "1.7.21"]
                 [com.cognitect/vase "0.1.0-SNAPSHOT"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "pet-store.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.1-SNAPSHOT"]]}
             :uberjar {:aot [pet-store.server]}}
  :main ^{:skip-aot true} pet-store.server)
