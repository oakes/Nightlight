(ns nightlight.utils
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
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

(defn form->serializable [form]
  (if (instance? Exception form)
    [(.getMessage form)]
    (pr-str form)))

(defn delete-parents-recursively!
  "Deletes the given file along with all empty parents up to top-level-file."
  [top-level-file file]
  (when (and (zero? (count (.listFiles file)))
             (not (.equals file top-level-file)))
    (io/delete-file file true)
    (->> file
         .getParentFile
         (delete-parents-recursively! top-level-file)))
  nil)

(defn remove-returns [^String s]
  (-> s
      (str/escape {\return ""})
      (str/replace #"\u001b[^\n]*" "")))

