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

(defn refresh-tree []
  (a/download-tree
    (fn [{:keys [nested-items selection]}]
      (swap! s/runtime-state assoc :nodes nested-items)
      (swap! s/pref-state assoc :selection selection)
      (e/select-node selection))))

(defn new-file-dialog []
  (let [path (r/atom nil)
        upload (r/atom nil)]
    (fn []
      [ui/dialog {:modal true
                  :open (= :new (:dialog @s/runtime-state))
                  :actions
                  [(r/as-element
                     [ui/flat-button {:on-click (fn []
                                                  (swap! s/runtime-state dissoc :dialog)
                                                  (reset! path nil)
                                                  (reset! upload nil))
                                      :style {:margin "10px"}}
                      "Cancel"])
                   (r/as-element
                     [ui/flat-button {:disabled (and (not (seq @path))
                                                     (not @upload))
                                      :on-click (fn []
                                                  (if-let [form @upload]
                                                    (a/new-file-upload form refresh-tree)
                                                    (a/new-file @path refresh-tree))
                                                  (swap! s/runtime-state dissoc :dialog)
                                                  (reset! path nil)
                                                  (reset! upload nil))
                                      :style {:margin "10px"}}
                      "New File"])]}
       [ui/text-field
        {:floating-label-text "Enter a new file name"
         :full-width true
         :disabled (some? @upload)
         :on-change #(reset! path (.-value (.-target %)))}]
       (when (-> @s/runtime-state :options :hosted?)
         [:span
          [:p {:style {:text-align "center"}} "— or —"]
          [:p "Upload a file: "]
          [:form {:action "upload"
                  :enc-type "multipart/form-data"}
           [:input {:type "file"
                    :disabled (some? (seq @path))
                    :multiple "multiple"
                    :on-change #(reset! upload (.-target %))}]]])])))

(defn rename-dialog []
  (let [to (r/atom nil)]
    (fn []
      (let [node (:node @s/runtime-state)]
        [ui/dialog {:modal true
                    :open (= :rename (:dialog @s/runtime-state))
                    :actions
                    [(r/as-element
                       [ui/flat-button {:on-click (fn []
                                                    (swap! s/runtime-state dissoc :dialog :node)
                                                    (reset! to nil))
                                        :style {:margin "10px"}}
                        "Cancel"])
                     (r/as-element
                       [ui/flat-button {:disabled (not (seq @to))
                                        :on-click (fn []
                                                    (let [path (:value node)]
                                                      (swap! s/runtime-state
                                                        (fn [state]
                                                          (-> state
                                                              (dissoc :dialog :node)
                                                              (update :editors dissoc path)
                                                              (update :saved-content dissoc path)
                                                              (update :current-content dissoc path))))
                                                      (swap! s/pref-state assoc :selection nil)
                                                      (a/rename-file path @to refresh-tree)
                                                      (reset! to nil)))
                                        :style {:margin "10px"}}
                        "Rename"])]}
         [ui/text-field
          {:floating-label-text (str "Enter a new file name for " (:primary-text node))
           :full-width true
           :on-change #(reset! to (.-value (.-target %)))}]
         [:p "Note: To move into a directory, just write the path like this: "]
         [:p [:code "dir/hello.txt"]]]))))

(defn delete-dialog []
  (let [node (:node @s/runtime-state)]
    [ui/dialog {:modal true
                :open (= :delete (:dialog @s/runtime-state))
                :actions
                [(r/as-element
                   [ui/flat-button {:on-click #(swap! s/runtime-state dissoc :dialog :node)
                                    :style {:margin "10px"}}
                    "Cancel"])
                 (r/as-element
                   [ui/flat-button {:on-click (fn []
                                                (let [path (:value node)]
                                                  (swap! s/runtime-state
                                                          (fn [state]
                                                            (-> state
                                                                (dissoc :dialog :node)
                                                                (update :editors dissoc path)
                                                                (update :saved-content dissoc path)
                                                                (update :current-content dissoc path))))
                                                  (swap! s/pref-state assoc :selection nil)
                                                  (a/delete-file path refresh-tree)))
                                    :style {:margin "10px"}}
                    "Delete"])]}
     (str "Are you sure you want to delete " (:primary-text node) "?")]))

(defn icon-button [node]
  (r/as-element
    [ui/icon-menu {:icon-button-element (r/as-element
                                          [ui/icon-button {:touch true}
                                           [icons/navigation-more-vert {:color (color :grey700)}]])}
     [ui/menu-item {:on-click #(swap! s/runtime-state assoc :dialog :rename :node node)}
      "Rename"]
     [ui/menu-item {:on-click #(swap! s/runtime-state assoc :dialog :delete :node node)}
      "Delete"]]))

(defn node->element [editable? {:keys [nested-items] :as node}]
  (let [node (cond
               (seq nested-items)
               (assoc node :nested-items (mapv (partial node->element editable?) nested-items))
               (and editable?
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
   (let [header-nodes (->> [(cond
                              (not (:hosted? options))
                              {:primary-text "Clojure REPL"
                               :value c/repl-path
                               :style {:font-weight "bold"}}
                              (not (:read-only? options))
                              {:primary-text "Control Panel"
                               :value c/control-panel-path
                               :style {:font-weight "bold"}})
                            (when (:url options)
                              {:primary-text "ClojureScript REPL"
                               :value c/cljs-repl-path
                               :style {:font-weight "bold"}})]
                           (remove nil?)
                           (map (partial node->element false)))
         nodes (->> nodes
                    (map (partial node->element true))
                    (concat header-nodes))]
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
     [ui/raised-button {:on-click #(swap! s/runtime-state assoc :dialog :new)
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
                (map (partial node->element false)
                  (assoc-in comps [0 :style :background-color] bg-color)))))]]))))

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
                         :style {:display (if (:hosted? options) "block" "none")}
                         :on-click #(set! (.-location js/window) "export.zip")}
       "Export"]
      [ui/raised-button {:background-color "#FF6F00"
                         :style {:display (if (:url options) "block" "none")}
                         :on-click #(.open js/window (:url options))}
       "View App"]
      [ui/raised-button {:background-color "#FF6F00"
                         :style {:display (if update? "block" "none")}
                         :on-click #(.open js/window c/page-url)}
       "Update"]]]]])

(defn cljs-repl-overlay [url]
  [:span
   [:span {:class "overlay"
           :style {:background-color "black"
                   :opacity 0.2
                   :z-index 100}}]
   [:span {:class "overlay"
           :style {:z-index 100}}
    [ui/raised-button {:background-color "#FF6F00"
                       :on-click (fn []
                                   (repl/init-cljs url)
                                   (swap! s/runtime-state assoc :enable-cljs-repl? true))
                       :style {:margin "auto"
                               :position "absolute"
                               :top 0
                               :left 0
                               :right 0
                               :bottom 0
                               :height 40
                               :width 80}}
     "Start"]]])

(defn app []
  (let [{:keys [title nodes new-prefs reset-count options enable-cljs-repl?] :as runtime-state} @s/runtime-state
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
      (case selection
        c/control-panel-path
        (when (:hosted? options)
          (cp/panel mui-theme reset-count new-prefs))
        c/cljs-repl-path
        (when-not enable-cljs-repl?
          [cljs-repl-overlay (:url options)])
        nil)
      [right-sidebar mui-theme runtime-state pref-state]
      [rename-dialog]
      [delete-dialog]
      [new-file-dialog]
      [:iframe {:id "cljsapp"
                :class "lower-half"
                :style {:background-color (if enable-cljs-repl? "white" "none")
                        :display (if (= selection c/cljs-repl-path) "block" "none")}}]]]))

