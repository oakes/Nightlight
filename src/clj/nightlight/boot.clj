(ns nightlight.boot
  {:boot/export-tasks true}
  (:require [nightlight.core :refer [start]]
            [boot.core :as core]
            [clojure.java.io :as io]))

(core/deftask nightlight
  [p port PORT int "The port that Nightlight runs on"
   a url URL str "The URL that the ClojureScript app is being served on"]
  (core/with-pass-thru _
    (start {:port port :url url})))

(def night nightlight)

(core/deftask sandbox
  [f file FILE str "The path to the policy file."]
  (core/with-pass-thru _
    (when (.exists (io/file file))
      (System/setProperty "java.security.policy" file)
      (System/setSecurityManager (SecurityManager.)))))

