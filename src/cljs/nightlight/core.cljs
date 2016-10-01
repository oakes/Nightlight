(ns nightlight.core
  (:require [cljsjs.bootstrap]
            [cljsjs.bootstrap-treeview]
            [paren-soup.core :as ps]
            [cljs.reader :refer [read-string]])
  (:import goog.net.XhrIo))

(defn init-tree []
  (.send XhrIo
      "/tree"
      (fn [e]
        (when (.isSuccess (.-target e))
          (->> (.. e -target getResponseText)
               read-string
               (hash-map :data)
               clj->js
               (.treeview (js/$ "#tree")))))
      "GET"))

(init-tree)

(ps/init-all)

