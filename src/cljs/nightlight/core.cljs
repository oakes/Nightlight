(ns nightlight.core
  (:require [cljsjs.bootstrap]
            [cljsjs.bootstrap-treeview]
            [cljsjs.bootstrap-toggle]
            [cljs.reader :refer [read-string]]
            [nightlight.editors :as e]
            [nightlight.state :as s]
            [nightlight.repl :as repl]
            [reagent.core :as r])
  (:import goog.net.XhrIo))

(def ^:const version "1.2.1")
(def ^:const api-url "https://clojars.org/api/artifacts/nightlight")
(def ^:const page-url "https://clojars.org/nightlight")
(def ^:const bootstrap-themes {:dark "bootstrap-dark.min.css" :light "bootstrap-light.min.css"})

(defn init-tree [{:keys [text nodes selection file? options]}]
  (swap! s/runtime-state #(assoc % :options options :title text))
  (some-> (:url options) repl/init-cljs)
  (let [repl-nodes (if (:url options)
                     [(repl/repl-node selection) (repl/cljs-repl-node selection)]
                     [(repl/repl-node selection)])]
    (.treeview (js/$ "#tree")
      (clj->js {:data (concat repl-nodes nodes)
                :onNodeSelected
                (fn [e data]
                  (e/select-node {:path (.-path data) :file? (.-file data)}))
                :onNodeExpanded
                (fn [e data]
                  (swap! s/pref-state update :expansions #(conj (or % #{}) (.-path data))))
                :onNodeCollapsed
                (fn [e data]
                  (swap! s/pref-state update :expansions disj (.-path data)))})))
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
    (.change (fn [e] (swap! s/pref-state assoc :auto-save? (-> e .-target .-checked)))))
  (doto (js/$ "#toggleTheme")
    (.bootstrapToggle (if (= theme :light) "on" "off"))
    (.change (fn [e]
               (let [theme (if (-> e .-target .-checked) :light :dark)]
                 (swap! s/pref-state assoc :theme theme)
                 (swap! s/runtime-state assoc :bootstrap-css (bootstrap-themes theme))
                 (doseq [editor (-> @s/runtime-state :editors vals)]
                   (e/set-theme editor theme))))))
  (reset! s/pref-state state)
  (s/save-prefs-to-server))

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
                          (#(aget % "latest_version"))
                          (not= version)))
        (swap! s/runtime-state assoc :update? true)))
    "GET"))

(defn app []
  (let [{:keys [title bootstrap-css paren-soup-css update?]} @s/runtime-state]
    [:span
     [:title title]
     [:link {:rel "stylesheet" :type "text/css" :href bootstrap-css}]
     [:link {:rel "stylesheet" :type "text/css" :href paren-soup-css}]
     [:link {:rel "stylesheet" :type "text/css" :href "style.css"}]
     [:div {:id "settings"}
      [:input {:type "checkbox"
               :data-toggle "toggle"
               :id "toggleAutoSave"
               :data-on "Auto Save"
               :data-off "Auto Save"}]
      [:input {:type "checkbox"
               :data-toggle "toggle"
               :id "toggleTheme"
               :data-on "Light Theme"
               :data-off "Light Theme"}]]
     [:button {:type "button"
               :class "btn btn-warning"
               :id "update"
               :style {:display (if update? "block" "none")}
               :on-click #(.open js/window page-url)}
      "Update Nightlight"]
     [:div {:class "leftsidebar"}
      [:div {:id "tree"}]]
     [:span {:id "editor"}]
     [:iframe {:id "cljsapp"}]]))

(def app-with-init
  (with-meta app
    {:component-did-mount (fn [this]
                            (repl/init-cljs-client)
                            (download-state)
                            #_(check-version))}))

(r/render-component [app-with-init] (.querySelector js/document "#app"))

