(ns com.cognitect.vase.try)

(defmacro try->
    "Thread the first expression through the remaining expressions.

     A form like (:? classname var ,,,) catches exceptions from
     the preceding code. It continues threading with the value of the
     body expressions as the replacement value.

     A form like (:! classname var ,,,) catches exceptions from the
     preceding code, but does _not_ continue threading. It aborts and the
     entire expression returns ::exit"
    [x & forms]
    (loop [x     x
           forms forms]
      (if forms
        (let [form     (first forms)
              threaded (cond
                         (and (seq? form) (= :! (first form)))
                         (with-meta `(try ~x (catch ~@(rest form) ::exit)) (meta form))

                         (and (seq? form) (= :? (first form)))
                         (with-meta `(try ~x (catch ~@(rest form))) (meta form))

                         (seq? form)
                         (with-meta `(let [v# ~x]
                                       (if (= ::exit v#)
                                         ::exit
                                         (~(first form) v# ~@(next form)))) (meta form))

                         :else
                         `(let [v# ~x]
                            (if (= ::exit v#)
                              ::exit
                              (~form v#))))]
          (recur threaded (next forms)))
        x)))
