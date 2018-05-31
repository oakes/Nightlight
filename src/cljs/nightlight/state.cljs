(ns nightlight.state
  (:require [reagent.core :as r])
  (:import goog.net.XhrIo))

(defonce pref-state (r/atom {:left-sidebar-width 300}))
(defonce runtime-state (r/atom {:title "Nightlight"}))

(add-watch pref-state :write-prefs
  (fn [_ _ old-state new-state]
    (swap! runtime-state assoc :new-prefs new-state)
    (when-not (-> @runtime-state :options :read-only?)
      (.send XhrIo
        "write-state"
        (fn [e]
          ; if main ns or deps changed, refresh the cljs iframe
          (when-let [url (get-in @runtime-state [:options :url])]
            (when (not= (select-keys old-state [:main-ns :deps])
                        (select-keys new-state [:main-ns :deps]))
              (let [iframe (.querySelector js/document "#cljsapp")]
                (set! (.-src iframe) url)))))
        "POST"
        (pr-str new-state)))))
