(ns nightlight.boot
  {:boot/export-tasks true}
  (:require [nightlight.core :refer [start]]
            [boot.core :as core]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(core/deftask nightlight
  [p port PORT int "The port that Nightlight runs on"
   a url URL str "The URL that the ClojureScript app is being served on"
   _ users USERS edn "A map of usernames and passwords to restrict access to"
   _ user USER str "A single username/password, separated by a space"]
  (core/with-pass-thru _
    (start {:port port :url url :users (or users (apply hash-map (str/split user #" ")))})))

(def night nightlight)

(core/deftask sandbox
  [f file FILE str "The path to the policy file."]
  (core/with-pass-thru _
    (when (.exists (io/file file))
      (System/setProperty "java.security.policy" file)
      (System/setSecurityManager (SecurityManager.)))))

