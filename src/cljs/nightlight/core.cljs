(ns nightlight.core
  (:require [paren-soup.core]
            [cljsjs.bootstrap]
            [cljsjs.bootstrap-treeview]))

(def model [{:text "Parent 1"
             :nodes [{:text "Child 1"}]}])

(.treeview (js/$ "#tree") (clj->js {:data model}))

