(ns nightlight.core
  (:require [cljsjs.bootstrap]
            [cljsjs.bootstrap-treeview]
            [cljsjs.bootstrap-toggle]
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

(defn init-state [{:keys [instarepl? auto-save?] :as state}]
  (-> (.querySelector js/document "#settings")
      .-style
      (aset "display" "block"))
  (doto (js/$ "#toggleInstaREPL")
    (.bootstrapToggle (if instarepl? "on" "off"))
    (.change (fn [e]
               (swap! s/pref-state assoc :instarepl? (-> e .-target .-checked))
               (e/toggle-instarepl (-> e .-target .-checked)))))
  (doto (js/$ "#toggleAutoSave")
    (.bootstrapToggle (if auto-save? "on" "off"))
    (.change (fn [e] (swap! s/pref-state assoc :auto-save? (-> e .-target .-checked)))))
  (reset! s/pref-state state))

(defn download-state []
  (.send XhrIo
    "/read-state"
    (fn [e]
      (when (.isSuccess (.-target e))
        (init-state (read-string (.. e -target getResponseText))))
      (download-tree))
    "GET"))

(download-state)

