(def proj '{:app {:project omnext-todo
                  :version "0.0.1-SNAPSHOT"
                  :description "FIXME: write description"
                  :license {:name "Eclipse Public License"
                            :url "http://www.eclipse.org/legal/epl-v10.html"}}
            :source-paths #{"src"}
            :test-paths #{"test"}
            :resource-paths #{"resources" "config"}
            :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                           [io.pedestal/pedestal.service "0.5.2"]
                           [com.cognitect/pedestal.vase "0.9.0"]

                           ;; Remove this line and uncomment one of the next lines to
                           ;; use Immutant or Tomcat instead of Jetty:
                           [io.pedestal/pedestal.jetty "0.5.2"]
                           ;; [io.pedestal/pedestal.immutant "0.5.2-SNAPSHOT"]
                           ;; [io.pedestal/pedestal.tomcat "0.5.2-SNAPSHOT"]

                           [ch.qos.logback/logback-classic "1.1.8" :exclusions [org.slf4j/slf4j-api]]
                           [org.slf4j/jul-to-slf4j "1.7.22"]
                           [org.slf4j/jcl-over-slf4j "1.7.22"]
                           [org.slf4j/log4j-over-slf4j "1.7.22"]]
            :dev-dependencies [[io.pedestal/pedestal.service-tools "0.5.2"]
                               [org.clojure/tools.namespace "0.3.0-alpha3"]]
            :external {:nrepl-options {:bind "127.0.0.1"
                                       :reply true}
                       :main omnext-todo.server}})

(set-env! :source-paths   (set (:source-paths proj))
          :test-paths     (set (:test-paths proj))
          :resource-paths (set (:resource-paths proj))
          ;:repositories (:repositories proj)
          :dependencies   (:dependencies proj))

(task-options! pom {:project (get-in proj [:app :project])
                    :version (str (get-in proj [:app :version]) "-standalone")
                    :description (get-in proj [:app :description])
                    :license (get-in proj [:app :license])})

(load-data-readers!)

;; == Testing tasks ========================================

(deftask with-test
  "Add test to source paths"
  []
  (set-env! :source-paths #(clojure.set/union % (get-env :test-paths)))
  (set-env! :dependencies #(into % (:dev-dependencies proj)))
  ;(set-env! :dependencies #(conj % '[it.frbracch/boot-marginalia "0.1.3-1" :scope "test"]))
  ;(set-env! :dependencies #(conj % '[boot-codox "0.9.6" :scope "test"]))
  identity)

;; Include test/ in REPL sources
(replace-task!
  [r repl] (fn [& xs] (with-test) (apply r xs)))

(require '[clojure.test :refer [run-tests]])

(deftask test
  "Run project tests"
  []
  (with-test) ;; test source paths and test/dev deps added
  (require '[clojure.tools.namespace.find :refer [find-namespaces-in-dir]])
  (let [find-namespaces-in-dir (resolve 'clojure.tools.namespace.find/find-namespaces-in-dir)
        test-nses              (->> (get-env :test-paths)
                                    (mapcat #(find-namespaces-in-dir (clojure.java.io/file %)))
                                    distinct)]
    (doseq [tns test-nses] (require tns))
    (apply clojure.test/run-tests test-nses)))

;; == Dev tasks ============================================

(deftask dumbrepl
  "Launch a standard Clojure REPL"
  []
  ;; Include test/ in REPL sources
  (with-test)
  (clojure.main/repl :init (fn []
                             (use 'clojure.core)
                             (use 'clojure.repl))))

;(deftask docs
;  "Generate API (Codox) and Literate (Marginalia) docs"
;  []
;  (with-test)
;  (require '[codox.boot :refer [codox]])
;  (require '[it.frbracch.boot-marginalia :refer [marginalia]])
;  (let [codox (resolve 'codox.boot/codox)
;        marginalia (resolve 'it.frbracch.boot-marginalia/marginalia)]
;    (comp (codox :name (name (get-in proj [:app :project]))
;                 :description (str (get-in proj [:app :description])
;                                   "\n -- Also: [Literate docs](./uberdoc.html)"))
;          (marginalia :dir "doc"
;                      :desc (str (get-in proj [:app :description])
;                                   "\n -- Also: <a href=\"./index.html\">API docs</a>"))
;          (target))))

;; == Server Tasks =========================================

(deftask build
  "Build my project."
  []
  (comp (aot :namespace #{(get-in proj [:external :main])})
        (pom)
        (uber)
        (jar :main (get-in proj [:external :main]))))

