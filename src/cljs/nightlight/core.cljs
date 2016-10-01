(ns nightlight.core
  (:require [cljsjs.bootstrap]
            [cljsjs.bootstrap-treeview]
            [cljs.reader :refer [read-string]]
            [nightlight.editors :as e])
  (:import goog.net.XhrIo))

(defn node-selected [event data]
  (if (.-file data)
    (e/init-file (.-path data))
    (e/clear-editor)))

(defn init-tree [nodes]
  (.treeview (js/$ "#tree")
    (clj->js {:data nodes
              :onNodeSelected node-selected})))

(.send XhrIo
  "/tree"
  (fn [e]
    (when (.isSuccess (.-target e))
      (init-tree (read-string (.. e -target getResponseText)))))
  "GET")

