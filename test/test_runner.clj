(ns test-runner
  (:require [eftest.runner :refer [run-tests find-tests]]
            [clojure.java.io :as io]))

(defn -main [& _args]
  (println "Running tests in parallel with eftest...")
  (let [test-dirs ["test/unit" "test/integration"]
        tests (mapcat #(find-tests (io/file %)) test-dirs)
        options {:parallel? true
                 :capture-output? true
                 :multithread? :namespaces}]

    (println (str "Found " (count tests) " test namespaces"))
    (println "Running with parallel execution...")

    (let [summary (run-tests tests options)]
      (when (pos? (+ (:fail summary) (:error summary)))
        (System/exit 1)))))