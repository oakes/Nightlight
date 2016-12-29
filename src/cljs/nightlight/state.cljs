(ns nightlight.state
  (:require [reagent.core :as r])
  (:import goog.net.XhrIo))

(defonce pref-state (r/atom {}))
(defonce runtime-state (r/atom {:title "Nightlight"}))

(add-watch pref-state :write-prefs
  (fn [_ _ _ new-state]
    (swap! runtime-state assoc :new-prefs new-state)
    (when-not (-> @runtime-state :options :read-only?)
      (.send XhrIo
        "write-state"
        (fn [e])
        "POST"
        (pr-str new-state)))))

