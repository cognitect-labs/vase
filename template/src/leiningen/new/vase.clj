; Copyright 2017 Cognitect, Inc.

; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
; which can be found in the file epl-v10.html at the root of this distribution.
;
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
;
; You must not remove this notice, or any other, from this software.

(ns leiningen.new.vase
  (:require [leiningen.new.templates :refer [renderer name-to-path ->files
                                             project-name sanitize-ns]]))

(defn vase
  "A Pedestal service + Vase project template."
  [name & args]
  (let [render  (renderer "vase")
        main-ns (sanitize-ns name)
        data    {:raw-name  name
                 :name      (project-name name)
                 :namespace main-ns
                 :sanitized (name-to-path main-ns)}]
    (println (str "Generating a Vase application called " name "."))
    (->files data
             ["README.md"                            (render "README.md"         data)]
             ["project.clj"                          (render "project.clj"       data)]
             ["build.boot"                           (render "build.boot"        data)]
             ["boot.properties"                      (render "boot.properties"   data)]
             ["Capstanfile"                          (render "Capstanfile"       data)]
             ["Dockerfile"                           (render "Dockerfile"        data)]
             [".gitignore"                           (render ".gitignore"        data)]
             ["src/{{sanitized}}/service.clj"        (render "service.clj"       data)]
             ["resources/{{namespace}}_service.fern" (render "vase_service.fern" data)]
             ["config/logback.xml"                   (render "logback.xml"       data)]
             ["test/{{sanitized}}/service_test.clj"  (render "service_test.clj"  data)])))
