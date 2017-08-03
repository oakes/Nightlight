(ns leiningen.nightlight
  (:require [leinjacker.deps :as deps]
            [leinjacker.eval :as eval]
            [clojure.tools.cli :as cli]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 4000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be an integer between 0 and 65536"]]
   [nil "--host HOST" "The hostname that Nightlight listens on"
    :default "0.0.0.0"]
   [nil "--url URL" "The URL that the ClojureScript app is being served on"]
   [nil "--users USERS" "A map of usernames and passwords to restrict access to"
    :parse-fn edn/read-string
    :validate [map? "Must be a hash-map"]]
   [nil "--user USER" "A single username/password, separated by a space"
    :parse-fn #(apply hash-map (str/split % #" "))]
   ["-u" "--usage" "Show CLI usage options"]])


(defn start-nightlight
  [{:keys [main] :as project} {:keys [port host url users user] :as options}]
  (eval/eval-in-project
    (deps/add-if-missing
      project
      '[nightlight/lein-nightlight "1.7.2"])
    `(do
       (nightlight.core/start
         {:port ~port :ip ~host :url ~url :users (or ~users ~user)})
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

