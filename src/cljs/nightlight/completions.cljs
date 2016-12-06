(ns nightlight.completions
  (:require [goog.events :as events]
            [cljs.reader :refer [read-string]]
            [paren-soup.core :as ps]
            [paren-soup.dom :as psd]
            [nightlight.state :as s])
  (:import goog.net.XhrIo))

(declare refresh-completions)

(defn select-completion [editor {:keys [context-before context-after start-position]} text]
  (when-let [top-level-elem (psd/get-focused-top-level)]
    (set! (.-textContent top-level-elem)
      (str context-before text context-after))
    (let [pos (+ start-position (count text))]
      (psd/set-cursor-position! top-level-elem [pos pos]))
    (->> (ps/init-state (.querySelector js/document "#content") true false)
         (ps/add-parinfer true -1 :paren)
         (ps/edit-and-refresh! editor))))

(defn refresh-completions [path]
  (if-let [info (psd/get-completion-info)]
    (.send XhrIo
      "/completions"
      (fn [e]
        (swap! s/runtime-state update :completions assoc path
          (read-string (.. e -target getResponseText))))
      "POST"
      (pr-str info))
    (swap! s/runtime-state update :completions assoc path [])))

(defn completion-shortcut? [e]
  (and (= 9 (.-keyCode e))
       (not (.-shiftKey e))
       (psd/get-completion-info)
       (some-> (psd/get-focused-top-level)
               (psd/get-cursor-position true)
               set
               count
               (= 1))))

(defn init-completions [path editor-atom elem]
  (events/listen elem "keyup"
    (fn [e]
      (when (completion-shortcut? e)
        (when-let [comps (get-in @s/runtime-state [:completions path])]
          (when-let [info (psd/get-completion-info)]
            (select-completion @editor-atom info (:value (first comps)))
            (refresh-completions path)))))))

