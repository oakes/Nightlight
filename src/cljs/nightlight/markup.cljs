(ns nightlight.markup
  (:require [nightlight.state :as s]
            [nightlight.repl :as repl]
            [reagent.dom.server :refer [render-to-static-markup]]))

(def ^:const page-url "https://clojars.org/nightlight")

(defn app []
  (let [{:keys [title bootstrap-css paren-soup-css update?]} @s/runtime-state
        {:keys [selection]} @s/pref-state]
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
               :data-off "Dark Theme"}]]
     [:button {:type "button"
               :class "btn btn-warning"
               :id "update"
               :style {:display (if update? "block" "none")}
               :on-click #(.open js/window page-url)}
      "Update Nightlight"]
     [:div {:class "leftsidebar"}
      [:div {:id "tree"}]]
     [:span {:id "editor"}]
     [:iframe {:id "cljsapp"
               :style {:display (if (= selection repl/cljs-repl-path) "block" "none")}}]]))

(def ^:const toolbar
  (render-to-static-markup
    [:div {:class "toolbar"}
     (for [[id text] [["save" "Save"]
                      ["undo" "Undo"]
                      ["redo" "Redo"]]]
       (list
         ^{:key id}
         [:button {:type "button"
                   :class "btn btn-default navbar-btn"
                   :id id}
          text]
         " "))
     [:input {:type "checkbox"
              :data-toggle "toggle"
              :id "toggleInstaRepl"
              :data-on "InstaREPL"
              :data-off "InstaREPL"}]]))

(def ^:const ps-html
  (render-to-static-markup
    [:span
     [:div {:class "paren-soup" :id "paren-soup"}
      [:div {:class "instarepl" :id "instarepl"}]
      [:div {:class "numbers" :id "numbers"}]
      [:div {:class "content" :id "content" :contentEditable true}]]
     [:div {:class "rightsidebar"}
      [:div {:id "completions"}]]]))

(def ^:const ps-repl-html
  (render-to-static-markup
    [:span
     [:div {:class "toolbar"}
      (for [[id text] [["undo" "Undo"]
                       ["redo" "Redo"]]]
        (list
          ^{:key id}
          [:button {:type "button"
                    :class "btn btn-default navbar-btn"
                    :id id}
           text]
          " "))]
     [:div {:class "paren-soup" :id "paren-soup"}
      [:div {:class "content" :id "content" :contentEditable true}]]
     [:div {:class "rightsidebar"}
      [:div {:id "completions"}]]]))

