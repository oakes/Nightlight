(ns nightlight.core
  (:require [paren-soup.core]
            [cljsjs.bootstrap]
            [cljsjs.bootstrap-treeview]))

(.treeview (js/$ "#tree")
  (clj->js {:data [{:text "Parent 1"
                    :nodes [{:text "Child 1"}]}]}))

