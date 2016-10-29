(ns nightlight.boot
  {:boot/export-tasks true}
  (:require [nightlight.core :refer [start]]
            [boot.core :as core]))

(core/deftask night
  [p port PORT int "The port that Nightlight runs on."
   u url URL str "The URL that the ClojureScript app is being served on."]
  (core/with-pass-thru _
    (start {:port port :url url})))

