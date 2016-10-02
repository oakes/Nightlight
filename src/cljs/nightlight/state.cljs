(ns nightlight.state
  (:import goog.net.XhrIo))

(defonce pref-state (atom {}))

(defonce runtime-state (atom {}))

(add-watch pref-state :write-prefs
  (fn [_ _ _ new-state]
    (.send XhrIo
      "/write-state"
      (fn [e])
      "POST"
      (pr-str new-state))))

