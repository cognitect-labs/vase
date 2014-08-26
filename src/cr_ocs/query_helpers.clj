(ns cr-ocs.query-helpers)

;; These are short-hand functions to make it easier to work with
;;  complex queries within descriptors or at the repl

(defn contains-substring? [^String s inner]
  (.contains s (str inner)))

