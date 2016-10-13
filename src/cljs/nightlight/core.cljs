(ns nightlight.core
  (:require [cljsjs.bootstrap]
            [cljsjs.bootstrap-treeview]
            [cljsjs.bootstrap-toggle]
            [cljs.reader :refer [read-string]]
            [nightlight.editors :as e]
            [nightlight.state :as s])
  (:import goog.net.XhrIo))

(def ^:const version "1.0.0")
(def ^:const api-url "https://clojars.org/api/artifacts/nightlight")
(def ^:const page-url "https://clojars.org/nightlight")

(defn repl-node [{:keys [path]}]
  {:text "Clojure REPL"
   :path e/repl-path
   :icon "glyphicon glyphicon-chevron-right"
   :state {:selected (= path e/repl-path)}})

(defn cljs-repl-node [{:keys [path]}]
  {:text "ClojureScript REPL"
   :path e/cljs-repl-path
   :icon "glyphicon glyphicon-chevron-right"
   :state {:selected (= path e/cljs-repl-path)}})

(defn init-tree [{:keys [text nodes selection file?]}]
  (set! (.-title js/document) text)
  (.treeview (js/$ "#tree")
    (clj->js {:data (concat [(repl-node selection) (cljs-repl-node selection)] nodes)
              :onNodeSelected
              (fn [e data]
                (swap! s/pref-state assoc :selection (.-path data))
                (e/select-node {:path (.-path data) :file? (.-file data)}))
              :onNodeExpanded
              (fn [e data]
                (swap! s/pref-state update :expansions #(conj (or % #{}) (.-path data))))
              :onNodeCollapsed
              (fn [e data]
                (swap! s/pref-state update :expansions disj (.-path data)))}))
  (e/select-node selection))

(defn download-tree []
  (.send XhrIo
    "/tree"
    (fn [e]
      (when (.isSuccess (.-target e))
        (init-tree (read-string (.. e -target getResponseText)))))
    "GET"))

(defn init-state [{:keys [auto-save? theme] :as state}]
  (doto (js/$ "#toggleAutoSave")
    (.bootstrapToggle (if auto-save? "on" "off"))
    (.change (fn [e] (swap! s/pref-state assoc :auto-save?
                       (-> e .-target .-checked)))))
  (let [themes {:dark "bootstrap-dark.min.css" :light "bootstrap-light.min.css"}]
    (doto (js/$ "#toggleTheme")
      (.bootstrapToggle (if (= theme :light) "on" "off"))
      (.change (fn [e]
                 (let [theme (if (-> e .-target .-checked) :light :dark)]
                   (swap! s/pref-state assoc :theme theme)
                   (e/change-css "#bootstrap-css" (get themes theme "bootstrap-dark.min.css"))
                   (doseq [editor (-> @s/runtime-state :editors vals)]
                     (e/set-theme editor theme))))))
    (e/change-css "#bootstrap-css" (get themes theme "bootstrap-dark.min.css")))
  (reset! s/pref-state state))

(defn download-state []
  (.send XhrIo
    "/read-state"
    (fn [e]
      (when (.isSuccess (.-target e))
        (init-state (read-string (.. e -target getResponseText))))
      (download-tree))
    "GET"))

(defn check-version []
  (.send XhrIo
    api-url
    (fn [e]
      (when (and (.isSuccess (.-target e))
                 (some->> (.. e -target getResponseText)
                          (.parse js/JSON)
                          .-latest_version
                          (not= version)))
        (doto (.querySelector js/document "#update")
          (-> .-style (aset "display" "block"))
          (.addEventListener "click" #(.open js/window page-url)))))
    "GET"))

(defn main []
  (download-state)
  (check-version))

(main)

