(ns leiningen.nightlight
  (:require
    [leinjacker.deps :as deps]
    [leinjacker.eval :as eval]
    [clojure.tools.cli :as cli]))


(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 4000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be an integer between 0 and 65536"]]
   ["-a" "--url URL" "The URL that the ClojureScript app is being served on"]
   ["-u" "--usage" "Show CLI usage options"]])


(defn start-nightlight
  [{:keys [main] :as project} {:keys [port url] :as options}]
  (eval/eval-in-project
    (deps/add-if-missing
      project
      '[nightlight/lein-nightlight "1.3.1"])
    `(do
       (nightlight.core/start {:port ~port :url ~url})
       (when '~main (require '~main)))
    `(require 'nightlight.core)))


(defn nightlight
  "A conveninent Nightlight launcher
  Run with -u to see CLI usage."
  [project & args]
  (let [cli (cli/parse-opts args cli-options)]
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

