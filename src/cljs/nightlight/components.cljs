(ns nightlight.components
  (:require [nightlight.state :as s]
            [nightlight.repl :as repl]
            [nightlight.editors :as e]
            [nightlight.constants :as c]
            [nightlight.completions :refer [select-completion refresh-completions]]
            [nightlight.control-panel :as cp]
            [nightlight.ajax :as a]
            [paren-soup.dom :as psd]
            [reagent.core :as r]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as icons]))

(def ^:const special-item-style {:font-weight "bold"})

(defn refresh-tree []
  (a/download-tree
    (fn [{:keys [nested-items selection]}]
      (swap! s/runtime-state assoc :nodes nested-items)
      (swap! s/pref-state assoc :selection selection)
      (e/select-node selection))))

(defn new-file-dialog []
  (let [path (r/atom nil)]
    (fn []
      [ui/dialog {:modal true
                  :open (some? (:show-new-file? @s/runtime-state))
                  :actions
                  [(r/as-element
                     [ui/flat-button {:on-click #(swap! s/runtime-state dissoc :show-new-file?)
                                      :style {:margin "10px"}}
                      "Cancel"])
                   (r/as-element
                     [ui/flat-button {:disabled (not (seq @path))
                                      :on-click (fn []
                                                  (swap! s/runtime-state dissoc :show-new-file?)
                                                  (a/new-file @path refresh-tree))
                                      :style {:margin "10px"}}
                      "Create New File"])]}
       [ui/text-field
        {:floating-label-text "Enter a new file name."
         :full-width true
         :on-change #(reset! path (.-value (.-target %)))}]])))

(defn rename-dialog []
  (let [to (r/atom nil)]
    (fn []
      [ui/dialog {:modal true
                  :open (some? (:path-to-rename @s/runtime-state))
                  :actions
                  [(r/as-element
                     [ui/flat-button {:on-click #(swap! s/runtime-state dissoc :path-to-rename)
                                      :style {:margin "10px"}}
                      "Cancel"])
                   (r/as-element
                     [ui/flat-button {:disabled (not (seq @to))
                                      :on-click (fn []
                                                  (let [path (:path-to-rename @s/runtime-state)]
                                                    (swap! s/runtime-state dissoc :path-to-rename)
                                                    (swap! s/pref-state assoc :selection nil)
                                                    (a/rename-file path @to refresh-tree)))
                                      :style {:margin "10px"}}
                      "Rename"])]}
       [ui/text-field
        {:floating-label-text "Enter a new file name."
         :full-width true
         :on-change #(reset! to (.-value (.-target %)))}]
       [:p "Note: To move into a directory, just write the path like this: "]
       [:p [:code "dir/hello.txt"]]])))

(defn delete-dialog []
  (let [path (:path-to-delete @s/runtime-state)]
    [ui/dialog {:modal true
                :open (some? path)
                :actions
                [(r/as-element
                   [ui/flat-button {:on-click #(swap! s/runtime-state dissoc :path-to-delete)
                                    :style {:margin "10px"}}
                    "Cancel"])
                 (r/as-element
                   [ui/flat-button {:on-click (fn []
                                                (swap! s/runtime-state dissoc :path-to-delete)
                                                (swap! s/pref-state assoc :selection nil)
                                                (a/delete-file path refresh-tree))
                                    :style {:margin "10px"}}
                    "Delete"])]}
     "Are you sure you want to delete this file?"]))

(defn icon-button [{:keys [value]}]
  (r/as-element
    [ui/icon-menu {:icon-button-element (r/as-element
                                          [ui/icon-button {:touch true}
                                           [icons/navigation-more-vert {:color (color :grey700)}]])}
     [ui/menu-item {:on-click #(swap! s/runtime-state assoc :path-to-rename value)}
      "Rename"]
     [ui/menu-item {:on-click #(swap! s/runtime-state assoc :path-to-delete value)}
      "Delete"]]))

(defn node->element [{:keys [nested-items] :as node}]
  (let [node (cond
               (seq nested-items)
               (assoc node :nested-items (mapv node->element nested-items))
               (and (not= (:style node) special-item-style)
                    (-> @s/runtime-state :options :read-only? not))
               (assoc node :right-icon-button (icon-button node))
               :else
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
                        :style special-item-style}
                   nodes)
                 nodes)
         nodes (cond
                 (not (:hosted? options))
                 (cons {:primary-text "Clojure REPL"
                        :value c/repl-path
                        :style special-item-style}
                   nodes)
                 (not (:read-only? options))
                 (cons {:primary-text "Control Panel"
                        :value c/control-panel-path
                        :style special-item-style}
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
    [:div {:style {:float "left"}}
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
    [:div {:style {:float "left"
                   :margin-top "5px"
                   :margin-left "20px"}}
     [ui/raised-button {:on-click #(swap! s/runtime-state assoc :show-new-file? true)
                        :disabled (:read-only? options)}
      "New File"]]]
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
               {:keys [update? editors options new-prefs] :as runtime-state}
               {:keys [selection] :as pref-state}]
  [:div {:class "toolbar"}
   [ui/mui-theme-provider
    {:mui-theme mui-theme}
    [ui/toolbar
     {:style {:background-color "transparent"}}
     (when-let [editor (get editors selection)]
       [ui/toolbar-group
        (if (= selection c/control-panel-path)
          (cp/buttons pref-state new-prefs)
          (list
            (when-not (c/repl-path? selection)
              [ui/raised-button {:disabled (or (:read-only? options)
                                               (c/clean? editor))
                                 :on-click #(a/write-file editor)
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
  (let [{:keys [title nodes new-prefs reset-count options] :as runtime-state} @s/runtime-state
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
      (when (and (= selection c/control-panel-path) (:hosted? options))
        (cp/panel mui-theme reset-count new-prefs))
      [right-sidebar mui-theme runtime-state pref-state]
      [rename-dialog]
      [delete-dialog]
      [new-file-dialog]
      [:iframe {:id "cljsapp"
                :class "lower-half"
                :style {:background-color "white"
                        :display (if (= selection c/cljs-repl-path) "block" "none")}}]]]))

