(ns nightlight.components
  (:require [nightlight.state :as s]
            [nightlight.repl :as repl]
            [nightlight.editors :as e]
            [nightlight.completions :refer [select-completion refresh-completions]]
            [paren-soup.dom :as psd]
            [reagent.core :as r]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]))

(def ^:const page-url "https://clojars.org/nightlight")

(defn node->element [{:keys [nested-items] :as node}]
  (let [node (if (seq nested-items)
               (assoc node :nested-items (mapv node->element nested-items))
               node)]
    (r/as-element [ui/list-item node])))

(defn tree [mui-theme
            {:keys [nodes options] :as runtime-state}
            {:keys [selection] :as pref-state}]
  [ui/mui-theme-provider
   {:mui-theme mui-theme}
   (let [nodes (if (:url options)
                 (cons {:primary-text "ClojureScript REPL"
                        :value repl/cljs-repl-path
                        :style {:font-weight "bold"}}
                   nodes)
                 nodes)
         nodes (cons {:primary-text "Clojure REPL"
                      :value repl/repl-path
                      :style {:font-weight "bold"}}
                 nodes)
         nodes (map node->element nodes)]
     (vec
       (concat
         [ui/selectable-list
          {:value selection
           :on-change (fn [event value]
                        (when selection
                          (e/unselect-node selection))
                        (swap! s/pref-state assoc :selection value)
                        (e/select-node value))}]
         nodes)))])

(defn left-sidebar [mui-theme
                    {:keys [nodes] :as runtime-state}
                    {:keys [auto-save? theme] :as pref-state}]
  [:span
   [:div {:class "settings" :style {:padding-left "10px"}}
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
                               (doseq [editor (-> runtime-state :editors vals)]
                                 (e/set-theme editor theme))))}]]
   [:div {:class "leftsidebar"}
    [tree mui-theme runtime-state pref-state]
    [:div {:id "tree" :style {:display "none"}}]]])

(defn right-sidebar [mui-theme
                     {:keys [editors completions] :as runtime-state}
                     {:keys [selection theme] :as pref-state}]
  (when-let [editor (get editors selection)]
    (when-let [comps (get completions selection)]
      (when (seq comps)
        [:div {:class "rightsidebar"
               :on-mouse-down #(.preventDefault %)
               :style {:background-color (if (= :light theme) "#f7f7f7" "#272b30")}}
         [ui/mui-theme-provider
          {:mui-theme mui-theme}
          (vec
            (concat
              [ui/selectable-list
               {:on-change (fn [event value]
                             (when-let [info (psd/get-completion-info)]
                               (select-completion (e/get-object editor) info value)
                               (refresh-completions selection)))}]
              (let [bg-color (if (= :light theme) "rgba(0, 0, 0, 0.2)" "rgba(255, 255, 255, 0.2)")]
                (map node->element (assoc-in comps [0 :style :background-color] bg-color)))))]]))))

(defn toolbar [mui-theme
               {:keys [update? editors] :as runtime-state}
               {:keys [selection] :as pref-state}]
  [:div {:class "toolbar"}
   [ui/mui-theme-provider
    {:mui-theme mui-theme}
    [ui/toolbar
     {:style {:background-color "transparent"}}
     (when-let [editor (get editors selection)]
       [ui/toolbar-group
        (when-not (repl/repl-path? selection)
          [ui/raised-button {:disabled (e/clean? editor)
                             :on-click #(e/write-file editor)}
           "Save"])
        [ui/raised-button {:disabled (not (e/can-undo? editor))
                           :on-click #(e/undo editor)}
         "Undo"]
        [ui/raised-button {:disabled (not (e/can-redo? editor))
                           :on-click #(e/redo editor)}
         "Redo"]
        (when (-> selection e/get-extension e/show-instarepl?)
          [ui/toggle {:label "InstaREPL"
                      :label-position "right"
                      :default-toggled (get-in runtime-state [:instarepls selection])
                      :on-toggle (fn [event value]
                                   (e/toggle-instarepl editor value))}])])
     [ui/toolbar-group
      {:style {:z-index 100}}
      [ui/raised-button {:background-color "#FF6F00"
                         :style {:display (if update? "block" "none")}
                         :on-click #(.open js/window page-url)}
       "Update"]]]]])

(defn app []
  (let [{:keys [title nodes] :as runtime-state} @s/runtime-state
        {:keys [theme selection] :as pref-state} @s/pref-state
        paren-soup-css (if (= :light theme) "paren-soup-light.css" "paren-soup-dark.css")
        text-color (if (= :light theme) (color :black) (color :white))
        mui-theme (if (= :light theme)
                    (get-mui-theme (aget js/MaterialUIStyles "LightRawTheme"))
                    (get-mui-theme
                      (doto (aget js/MaterialUIStyles "DarkRawTheme")
                        (aset "palette" "accent1Color" "darkgray")
                        (aset "palette" "accent2Color" "darkgray")
                        (aset "palette" "accent3Color" "darkgray"))))]
    [ui/mui-theme-provider
     {:mui-theme mui-theme}
     [:span
      [:title title]
      [:link {:rel "stylesheet" :type "text/css" :href paren-soup-css}]
      [:link {:rel "stylesheet" :type "text/css" :href "style.css"}]
      [left-sidebar mui-theme runtime-state pref-state]
      [:span {:class "outer-editor"}
       [toolbar mui-theme runtime-state pref-state]
       [:span {:id "editor"}]]
      [right-sidebar mui-theme runtime-state pref-state]
      [:iframe {:id "cljsapp"
                :style {:display (if (= selection repl/cljs-repl-path) "block" "none")}}]]]))

