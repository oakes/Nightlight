(ns nightlight.boot
  {:boot/export-tasks true}
  (:require [nightlight.core :refer [start]]
            [boot.core :as core]))

(core/deftask night
  [p port PORT int "The port that Nightlight runs on."
   u cljs-url CLJSURL str "The address that the ClojureScript app is being served on."]
  (core/with-pass-thru _
    (start {:port port :cljs-url cljs-url})))

