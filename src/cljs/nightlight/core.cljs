(ns nightlight.core
  (:require [cljsjs.bootstrap]
            [cljsjs.bootstrap-treeview]
            [cljs.reader :refer [read-string]]
            [nightlight.editors :as e]
            [nightlight.state :as s])
  (:import goog.net.XhrIo))

(defn select-node [path file?]
  (if file?
    (e/read-file path)
    (e/clear-editor)))

(defn node-selected [e data]
  (swap! s/pref-state assoc :selection (.-path data))
  (select-node (.-path data) (.-file data)))

(defn node-expanded [e data]
  (swap! s/pref-state update :expansions #(conj (or % #{}) (.-path data))))

(defn node-collapsed [e data]
  (swap! s/pref-state update :expansions disj (.-path data)))

(defn init-tree [{:keys [text nodes path file?]}]
  (set! (.-title js/document) text)
  (.treeview (js/$ "#tree")
    (clj->js {:data nodes
              :onNodeSelected node-selected
              :onNodeExpanded node-expanded
              :onNodeCollapsed node-collapsed}))
  (select-node path file?))

(defn download-tree []
  (.send XhrIo
    "/tree"
    (fn [e]
      (when (.isSuccess (.-target e))
        (init-tree (read-string (.. e -target getResponseText)))))
    "GET"))

(defn download-state []
  (.send XhrIo
    "/read-state"
    (fn [e]
      (when (.isSuccess (.-target e))
        (reset! s/pref-state (read-string (.. e -target getResponseText))))
      (download-tree))
    "GET"))

(download-state)

