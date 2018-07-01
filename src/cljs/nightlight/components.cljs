(ns nightlight.components
  (:require [nightlight.state :as s]
            [nightlight.repl :as repl]
            [nightlight.editors :as e]
            [nightlight.constants :as c]
            [nightlight.completions :refer [select-completion refresh-completions]]
            [nightlight.control-panel :as cp]
            [nightlight.ajax :as a]
            [nightlight.watch :as watch]
            [paren-soup.dom :as psd]
            [reagent.core :as r]
            [cljsjs.material-ui]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as icons]
            [goog.object :as gobj]))

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
                                                              (update :editors dissoc path))))
                                                      (swap! s/pref-state assoc :selection nil)
                                                      (a/rename-file path @to
                                                        (fn [e]
                                                          (let [new-path (.. e -target getResponseText)]
                                                            (swap! s/runtime-state update :editors dissoc new-path)
                                                            (refresh-tree))))
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
                                                                (update :editors dissoc path))))
                                                  (swap! s/pref-state assoc :selection nil)
                                                  (a/delete-file path refresh-tree)))
                                    :style {:margin "10px"}}
                    "Delete"])]}
     (str "Are you sure you want to delete " (:primary-text node) "?")]))

(defn connection-lost-dialog []
  [ui/dialog {:modal true
              :open (= :connection-lost (:dialog @s/runtime-state))
              :actions
              [(r/as-element
                 [ui/flat-button {:on-click #(swap! s/runtime-state dissoc :dialog)
                                  :style {:margin "10px"}}
                  "OK"])]}
   "Connection to server has been lost. Try refreshing or check to see if the server is still running."])

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
               (assoc node :nested-items (->> nested-items
                                              (sort-by :primary-text)
                                              (mapv (partial node->element editable?))))
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
                    (sort-by :primary-text)
                    (map (partial node->element true))
                    (concat header-nodes))]
     (into
       [ui/selectable-list
        {:value selection
         :on-change (fn [event value]
                      (e/set-selection value))}]
       nodes))])

(defn left-sidebar [mui-theme
                    {:keys [nodes editors options] :as runtime-state}
                    {:keys [auto-save? theme] :as pref-state}]
  [:span
   [:div {:class "settings" :style {:padding-left "10px"}}
    [:div {:style {:float "left"
                   :white-space "nowrap"}}
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
                                (doseq [editor (vals editors)]
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
                     {:keys [editors] :as runtime-state}
                     {:keys [selection theme] :as pref-state}]
  (when-let [editor (get editors selection)]
    (when-let [comps (some-> (c/get-completions editor) deref)]
      (when (seq comps)
        [:div {:class "rightsidebar"
               :on-mouse-down #(.preventDefault %)
               :style {:background-color (if (= :light theme)
                                           "rgba(247, 247, 247, 0.7)"
                                           "rgba(39, 43, 48, 0.7)")}}
         [ui/mui-theme-provider
          {:mui-theme mui-theme}
          (into
            [ui/selectable-list
             {:on-change (fn [event value]
                           (when-let [info (psd/get-completion-info)]
                             (select-completion (c/get-object editor) info value)
                             (refresh-completions (c/get-extension editor) (c/get-completions editor))))}]
            (let [bg-color (if (= :light theme) "rgba(0, 0, 0, 0.2)" "rgba(255, 255, 255, 0.2)")]
              (map (partial node->element false)
                (assoc-in comps [0 :style :background-color] bg-color))))]]))))

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
          (list
            [ui/raised-button {:disabled (not (seq (c/get-focused-text editor)))
                               :on-click #(c/eval-selection editor)
                               :key :eval}
             "Eval"]
            [ui/toggle {:label "InstaREPL"
                        :label-position "right"
                        :default-toggled (get-in runtime-state [:instarepls selection])
                        :on-toggle (fn [event value]
                                     (e/toggle-instarepl editor value))
                        :key :instarepl}]))])
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

(defn watcher-overlay [selection]
  (when (@watch/modified-files selection)
    [:span
     [:span {:class "overlay"
             :style {:background-color "black"
                     :opacity 0.8
                     :z-index 100}}]
     [:span {:class "overlay"
             :style {:z-index 100}}
      [:div {:style {:color "white"
                     :margin "auto"
                     :padding-top 50
                     :width 500}}
       "This file was modified externally. You can either refresh your browser or ignore the change and continue editing."
       [:br][:br]
       [ui/raised-button {:background-color "#FF6F00"
                          :on-click #(swap! watch/modified-files disj selection)}
        "Continue"]]]]))

(defn app []
  (let [{:keys [title nodes new-prefs reset-count options connection-lost?]
         :as runtime-state} @s/runtime-state
        {:keys [theme selection]
         :as pref-state} @s/pref-state
        paren-soup-css (if (= :light theme) "paren-soup-light.css" "paren-soup-dark.css")
        text-color (if (= :light theme) (color :black) (color :white))
        mui-theme (if (= :light theme)
                    (get-mui-theme (gobj/get js/MaterialUIStyles "LightRawTheme"))
                    (let [dark-raw-theme (gobj/get js/MaterialUIStyles "DarkRawTheme")]
                      (doto (gobj/get dark-raw-theme "palette")
                        (gobj/set "accent1Color" "#A9A9A9")
                        (gobj/set "accent2Color" "#A9A9A9")
                        (gobj/set "accent3Color" "#A9A9A9"))
                      (get-mui-theme dark-raw-theme)))]
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
      [watcher-overlay selection]
      (case selection
        c/control-panel-path
        (when (:hosted? options)
          (cp/panel mui-theme reset-count new-prefs))
        nil)
      [right-sidebar mui-theme runtime-state pref-state]
      [rename-dialog]
      [delete-dialog]
      [new-file-dialog]
      [connection-lost-dialog]
      [:iframe {:id "cljsapp"
                :class "lower-half"
                :style {:background-color "white"
                        :display (if (= selection c/cljs-repl-path) "block" "none")}
                :src (when (= js/window.self js/window.top)
                       (:url options))}]]]))

