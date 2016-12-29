(ns nightlight.components
  (:require [nightlight.state :as s]
            [nightlight.repl :as repl]
            [nightlight.editors :as e]
            [nightlight.constants :as c]
            [nightlight.completions :refer [select-completion refresh-completions]]
            [nightlight.control-panel :as cp]
            [paren-soup.dom :as psd]
            [reagent.core :as r]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]))

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
                        :value c/cljs-repl-path
                        :style {:font-weight "bold"}}
                   nodes)
                 nodes)
         nodes (cond
                 (not (:hosted? options))
                 (cons {:primary-text "Clojure REPL"
                        :value c/repl-path
                        :style {:font-weight "bold"}}
                   nodes)
                 (not (:read-only? options))
                 (cons {:primary-text "Control Panel"
                        :value c/control-panel-path
                        :style {:font-weight "bold"}}
                   nodes)
                 :else
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
                    {:keys [nodes options] :as runtime-state}
                    {:keys [auto-save? theme] :as pref-state}]
  [:span
   [:div {:class "settings" :style {:padding-left "10px"}}
    [ui/toggle {:label "Auto Save"
                :label-position "right"
                :default-toggled auto-save?
                :on-toggle (fn [event value]
                             (swap! s/pref-state assoc :auto-save? value))
                :disabled (:read-only? options)}]
    [ui/toggle {:label "Theme"
                :label-position "right"
                :default-toggled (= :light theme)
                :on-toggle (fn [event value]
                             (let [theme (if value :light :dark)]
                               (swap! s/pref-state assoc :theme theme)
                               (doseq [editor (-> runtime-state :editors vals)]
                                 (c/set-theme editor theme))))}]]
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
                               (select-completion (c/get-object editor) info value)
                               (refresh-completions selection)))}]
              (let [bg-color (if (= :light theme) "rgba(0, 0, 0, 0.2)" "rgba(255, 255, 255, 0.2)")]
                (map node->element (assoc-in comps [0 :style :background-color] bg-color)))))]]))))

(defn toolbar [mui-theme
               {:keys [update? editors options] :as runtime-state}
               {:keys [selection] :as pref-state}]
  [:div {:class "toolbar"}
   [ui/mui-theme-provider
    {:mui-theme mui-theme}
    [ui/toolbar
     {:style {:background-color "transparent"}}
     (when-let [editor (get editors selection)]
       [ui/toolbar-group
        (if (= selection c/control-panel-path)
          (cp/buttons runtime-state)
          (list
            (when-not (c/repl-path? selection)
              [ui/raised-button {:disabled (or (:read-only? options)
                                               (c/clean? editor))
                                 :on-click #(e/write-file editor)
                                 :key :save}
               "Save"])
            [ui/raised-button {:disabled (or (:read-only? options)
                                             (not (c/can-undo? editor)))
                               :on-click #(c/undo editor)
                               :key :undo}
             "Undo"]
            [ui/raised-button {:disabled (or (:read-only? options)
                                             (not (c/can-redo? editor)))
                               :on-click #(c/redo editor)
                               :key :redo}
             "Redo"]))
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
                         :on-click #(.open js/window c/page-url)}
       "Update"]]]]])

(defn app []
  (let [{:keys [title nodes new-options] :as runtime-state} @s/runtime-state
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
      (when (and (= selection c/control-panel-path) new-options)
        (cp/panel mui-theme new-options))
      [cp/new-library-dialog]
      [right-sidebar mui-theme runtime-state pref-state]
      [:iframe {:id "cljsapp"
                :class "lower-half"
                :style {:display (if (= selection c/cljs-repl-path) "block" "none")}}]]]))

