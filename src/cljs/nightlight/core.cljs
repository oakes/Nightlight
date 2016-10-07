(ns nightlight.core
  (:require [cljsjs.bootstrap]
            [cljsjs.bootstrap-treeview]
            [cljsjs.bootstrap-toggle]
            [cljs.reader :refer [read-string]]
            [nightlight.editors :as e]
            [nightlight.state :as s])
  (:import goog.net.XhrIo))

(def ^:const version "1.0.0-SNAPSHOT")
(def ^:const api-url "https://clojars.org/api/artifacts/nightlight")
(def ^:const page-url "https://clojars.org/nightlight")

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

(def repl-node {:text "REPL" :path "*REPL*" :file true})

(defn init-tree [{:keys [text nodes path file?]}]
  (set! (.-title js/document) text)
  (.treeview (js/$ "#tree")
    (clj->js {:data (cons repl-node nodes)
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
                   (e/change-css "#bootstrap-css" (get themes theme :dark))
                   (doseq [editor (-> @s/runtime-state :editors vals)]
                     (e/set-theme editor theme))))))
    (e/change-css "#bootstrap-css" (get themes theme :dark)))
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
                 (->> (.. e -target getResponseText)
                      (.parse js/JSON)
                      .-latest_version
                      (not= version)))
        (doto (.querySelector js/document "#update")
          (-> .-style (aset "display" "block"))
          (.addEventListener "click" #(.open js/window page-url)))))
    "GET"))

(defn main []
  (download-state)
  #_(check-version))

(main)

