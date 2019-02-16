(ns nightlight.watch
  (:require [nightlight.state :as s]
            [reagent.core :as r]))

(defonce modified-files (r/atom #{}))

(defn init-watcher! []
  (let [protocol (if (= (.-protocol js/location) "https:") "wss:" "ws:")
        host (-> js/window .-location .-host)
        sock (js/WebSocket. (str protocol "//" host "/watch"))]
    (set! (.-onopen sock)
      (fn [event]
        (.send sock "")))
    (set! (.-onmessage sock)
      (fn [event]
        (let [path (.-data event)]
          (when-let [editor (get-in @s/runtime-state [:editors path])]
            (swap! modified-files conj path)))))))

