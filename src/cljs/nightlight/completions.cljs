(ns nightlight.completions
  (:require [goog.events :as events]
            [cljs.reader :refer [read-string]]
            [paren-soup.core :as ps]
            [paren-soup.dom :as psd])
  (:import goog.net.XhrIo))

(def ^:const shortcuts #{32 38 40})

(declare refresh-completions)

(defn select-completion [editor {:keys [context-before context-after start-position]} text]
  (when-let [top-level-elem (psd/get-focused-top-level)]
    (set! (.-textContent top-level-elem)
      (str context-before text context-after))
    (let [pos (+ start-position (count text))]
      (psd/set-cursor-position! top-level-elem [pos pos]))
    (->> (ps/init-state (.querySelector js/document ".content") true false)
         (ps/add-parinfer true -1 :paren)
         (ps/edit-and-refresh! editor))))

(defn display-completions [editor completions-atom info]
  (let [event (fn [e data]
                (select-completion editor info (.-text data))
                (refresh-completions editor completions-atom))]
    (.treeview (js/$ "#completions")
      (clj->js {:data (clj->js @completions-atom)
                :onNodeSelected event
                :onNodeUnselected event}))))

(defn select-node [editor completions-atom old-id new-id]
  (when (get @completions-atom new-id)
    (swap! completions-atom
      (fn [completions]
        (-> completions
            (assoc-in [old-id :state :selected] false)
            (assoc-in [new-id :state :selected] true))))
    (display-completions editor completions-atom (psd/get-completion-info))))

(defn refresh-completions [editor completions-atom]
  (if-let [info (psd/get-completion-info)]
    (.send XhrIo
      "/completions"
      (fn [e]
        (reset! completions-atom (read-string (.. e -target getResponseText)))
        (select-node editor completions-atom 0 0))
      "POST"
      (pr-str info))
    (do
      (reset! completions-atom [])
      (display-completions editor completions-atom {}))))

(defn refresh? [e]
  (not (or (.-shiftKey e) (= 16 (.-keyCode e)))))

(defn completion-shortcut? [e]
  (and (.-shiftKey e) (shortcuts (.-keyCode e))))

(defn init-completions [editor completions-atom elem]
  (events/listen (.querySelector elem "#completions") "mousedown"
    (fn [e]
      (.preventDefault e)))
  (events/listen elem "keydown"
    (fn [e]
      (when (completion-shortcut? e)
        (.preventDefault e))))
  (events/listen elem "keyup"
    (fn [e]
      (when (completion-shortcut? e)
        (when-let [node (some-> (.treeview (js/$ "#completions") "getSelected") (aget 0))]
          (case (.-keyCode e)
            32 (when-let [info (psd/get-completion-info)]
                 (select-completion editor info (.-text node))
                 (refresh-completions editor completions-atom))
            38 (select-node editor completions-atom (.-nodeId node) (dec (.-nodeId node)))
            40 (select-node editor completions-atom (.-nodeId node) (inc (.-nodeId node)))))
        (.preventDefault e)))))

