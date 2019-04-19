(defproject omnext-todo "0.9.4-SNAPSHOT"
  :description "Vase sample application, Todo list compatible with Om.next"
  :url "https://github.com/cognitect-labs/vase/tree/master/samples/omnext-todo"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [io.pedestal/pedestal.service "0.5.5"]
                 [com.cognitect/pedestal.vase "0.9.3"]

                 ;; Remove this line and uncomment one of the next lines to
                 ;; use Immutant or Tomcat instead of Jetty:
                 [io.pedestal/pedestal.jetty "0.5.5"]
                 ;; [io.pedestal/pedestal.immutant "0.5.3"]
                 ;; [io.pedestal/pedestal.tomcat "0.5.3"]

                 [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [org.slf4j/log4j-over-slf4j "1.7.25"]

                 ;; Om.next Demand-driven API
                 ;[org.clojure/clojurescript "1.9.293"]
                 [org.omcljs/om "1.0.0-alpha47"]

                 ;; Deps cleanup
                 ;;  -- Om and Pedestal
                 [com.cognitect/transit-clj "0.8.313"]
                 ;;  -- Vase and Pedestal
                 [cheshire "5.8.1"]]
  :pedantic? :abort
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  ;; If you use HTTP/2 or ALPN, use the java-agent to pull in the correct alpn-boot dependency
  ;:java-agents [[org.mortbay.jetty.alpn/jetty-alpn-agent "2.0.3"]]
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "omnext-todo.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.5"]]}
             :uberjar {:aot [omnext-todo.server]}}
  :main ^{:skip-aot true} omnext-todo.server)
