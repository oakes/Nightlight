(ns nightlight.core
  (:require [cljsjs.bootstrap]
            [cljsjs.bootstrap-treeview]
            [cljs.reader :refer [read-string]]
            [nightlight.editors :as e]
            [nightlight.state :as s])
  (:import goog.net.XhrIo))

(defn select-node [path file?]
  (swap! s/pref-state assoc :selection path)
  (if file?
    (e/read-file path)
    (e/clear-editor)))

(defn init-tree [{:keys [nodes path file?]}]
  (.treeview (js/$ "#tree")
    (clj->js {:data nodes
              :onNodeSelected (fn [e data]
                                (select-node (.-path data) (.-file data)))}))
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

