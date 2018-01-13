(ns leiningen.nightlight
  (:require [leinjacker.deps :as deps]
            [leinjacker.eval :as eval]
            [clojure.tools.cli :as cli]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [nightlight.utils :as u]))


(defn start-nightlight
  [{:keys [main] :as project} options]
  (eval/eval-in-project
    (deps/add-if-missing
      project
      '[nightlight/lein-nightlight "2.1.2"])
    `(do
       (nightlight.core/start ~options)
       (when '~main (require '~main)))
    `(require 'nightlight.core)))


(defn nightlight
  "A conveninent Nightlight launcher
  Run with -u to see CLI usage."
  [project & args]
  (let [cli (cli/parse-opts args u/cli-options)]
    (cond
      ;; if there are CLI errors, print error messages and usage summary
      (:errors cli)
      (println (:errors cli) "\n" (:summary cli))
      ;; if user asked for CLI usage, print the usage summary
      (get-in cli [:options :usage])
      (println (:summary cli))
      ;; in other cases start Nightlight
      :otherwise
      (start-nightlight project (:options cli)))))

