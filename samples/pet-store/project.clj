(defproject pet-store "0.9.3-SNAPSHOT"
  :description "Vase sample application: Pet Store demo, partial"
  :url "https://github.com/cognitect-labs/vase/tree/master/samples/pet-store"
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
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "pet-store.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.3"]]}
             :uberjar {:aot [pet-store.server]}}
  :main ^{:skip-aot true} pet-store.server)
