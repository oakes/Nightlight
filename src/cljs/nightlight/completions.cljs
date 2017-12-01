(ns nightlight.completions
  (:require [goog.events :as events]
            [cljs.reader :refer [read-string]]
            [paren-soup.core :as ps]
            [paren-soup.dom :as psd]
            [nightlight.state :as s]
            [nightlight.constants :as c]
            [nightlight.ajax :as a]
            [goog.dom :as gdom]))

(defn select-completion [editor {:keys [context-before context-after start-position]} text]
  (when-let [top-level-elem (psd/get-focused-top-level)]
    (when (seq text)
      (gdom/setTextContent top-level-elem
        (str context-before text context-after))
      (let [pos (+ start-position (count text))]
        (psd/set-cursor-position! top-level-elem [pos pos]))
      (ps/refresh-after-cut-paste! editor))))

(defn refresh-completions [extension completions]
  (when (c/completion-exts extension)
    (if-let [info (psd/get-completion-info)]
      (a/download-completions (assoc info :ext extension) completions)
      (reset! completions nil))))

(defn completion-shortcut? [e]
  (and (= 9 (.-keyCode e))
       (not (.-shiftKey e))
       (psd/get-completion-info)
       (some-> (psd/get-focused-top-level)
               (psd/get-cursor-position true)
               set
               count
               (= 1))))

(defn init-completions [extension editor-atom elem completions]
  (when (c/completion-exts extension)
    (events/listen elem "keyup"
      (fn [e]
        (when (completion-shortcut? e)
          (when-let [comps @completions]
            (when-let [info (psd/get-completion-info)]
              (select-completion @editor-atom info (:value (first comps)))
              (refresh-completions extension completions))))))))

