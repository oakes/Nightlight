(ns nightlight.boot
  {:boot/export-tasks true}
  (:require [nightlight.core :refer [start]]
            [boot.core :as core]))

(core/deftask night
  [p port PORT int "The port that Nightlight runs on."]
  (core/with-pass-thru _
    (start {:port port})))

