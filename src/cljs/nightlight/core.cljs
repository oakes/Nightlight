(ns nightlight.core
  (:require [paren-soup.core]
            [cljsjs.bootstrap]
            [cljsjs.bootstrap-treeview]
            [paren-soup.core :as ps]))

(.treeview (js/$ "#tree")
  (clj->js {:data [{:text "Parent 1"
                    :nodes [{:text "Child 1"}]}]}))

(ps/init-all)

