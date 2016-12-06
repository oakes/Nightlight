(ns nightlight.components
  (:require [nightlight.state :as s]
            [nightlight.repl :as repl]
            [nightlight.editors :as e]
            [reagent.core :as r]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]))

(def ^:const page-url "https://clojars.org/nightlight")

(defn node->element [{:keys [nested-items] :as node}]
  (let [node (if (seq nested-items)
               (assoc node :nested-items (mapv node->element nested-items))
               node)]
    (r/as-element [ui/list-item node])))

(defn tree [theme nodes]
  (let [text-color (if (= :light theme) (color :black) (color :white))]
    [ui/mui-theme-provider
     {:mui-theme (get-mui-theme {:palette {:text-color text-color}})}
     (vec
       (concat
         [ui/selectable-list
          {:value (:selection @s/pref-state)
           :on-change (fn [event value]
                        (swap! s/pref-state assoc :selection value)
                        (e/select-node value))}]
         (map node->element nodes)))]))

(defn left-sidebar [auto-save? theme nodes]
  [:div {:class "leftsidebar"}
   [ui/toggle {:label "Auto Save"
               :label-position "right"
               :default-toggled auto-save?
               :on-toggle (fn [event value]
                            (swap! s/pref-state assoc :auto-save? value))}]
   [ui/toggle {:label "Theme"
               :label-position "right"
               :default-toggled (= :light theme)
               :on-toggle (fn [event value]
                            (let [theme (if value :light :dark)]
                              (swap! s/pref-state assoc :theme theme)
                              (doseq [editor (-> @s/runtime-state :editors vals)]
                                (e/set-theme editor theme))))}]
   [tree theme nodes]
   [:div {:id "tree" :style {:display "none"}}]])

(defn app []
  (let [{:keys [title update? nodes]} @s/runtime-state
        {:keys [selection auto-save? theme]} @s/pref-state
        bootstrap-css (if (= :light theme) "bootstrap-light.min.css" "bootstrap-dark.min.css")
        paren-soup-css (if (= :light theme) "paren-soup-light.css" "paren-soup-dark.css")
        text-color (if (= :light theme) (color :black) (color :white))]
    [ui/mui-theme-provider
     {:mui-theme (get-mui-theme {:palette {:text-color text-color}})}
     [:span
      [:title title]
      [:link {:rel "stylesheet" :type "text/css" :href bootstrap-css}]
      [:link {:rel "stylesheet" :type "text/css" :href paren-soup-css}]
      [:link {:rel "stylesheet" :type "text/css" :href "style.css"}]
      [left-sidebar auto-save? theme nodes]
      [:span {:id "editor"}]
      [:button {:type "button"
                :class "btn btn-warning"
                :id "update"
                :style {:display (if update? "block" "none")}
                :on-click #(.open js/window page-url)}
       "Update Nightlight"]
      [:iframe {:id "cljsapp"
                :style {:display (if (= selection repl/cljs-repl-path) "block" "none")}}]]]))

