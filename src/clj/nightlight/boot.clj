(ns nightlight.boot
  {:boot/export-tasks true}
  (:require [nightlight.core :refer [start]]
            [boot.core :as core]))

(core/deftask night
  [o opts OPTS edn "Options map."]
  (core/with-pass-thru fs
    (start opts)))

