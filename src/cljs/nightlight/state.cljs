(ns nightlight.state
  (:require [reagent.core :as r])
  (:import goog.net.XhrIo))

(defonce pref-state (r/atom {}))

(defonce runtime-state (r/atom {:title "Nightlight"}))

(add-watch pref-state :write-prefs
  (fn [_ _ _ new-state]
    (.send XhrIo
      "/write-state"
      (fn [e])
      "POST"
      (pr-str new-state))))

