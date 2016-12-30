(ns nightlight.control-panel
  (:require [clojure.string :as str]
            [nightlight.repl :as repl]
            [nightlight.constants :as c]
            [nightlight.state :as s]
            [paren-soup.core :as ps]
            [goog.string :refer [format]]
            [goog.string.format]
            [reagent.core :as r]
            [cljs-react-material-ui.reagent :as ui]))

(defn init-status-receiver []
  (let [protocol (if (= (.-protocol js/location) "https:") "wss:" "ws:")
        host (-> js/window .-location .-host)
        path (-> js/window .-location .-pathname)
        sock (js/WebSocket. (str protocol "//" host path "status"))
        text (atom nil)]
    (swap! s/runtime-state assoc :status-text text)
    (set! (.-onopen sock)
      (fn [event]
        (.send sock "")))
    (set! (.-onmessage sock)
      (fn [event]
        (swap! text str (.-data event))))))

(defn append-text! [editor elem text]
  (when-let [s @text]
    (ps/append-text! editor s)
    (repl/scroll-to-bottom elem)
    (reset! text nil)))

(defn control-panel-init []
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        scroll-top (atom 0)
        text (:status-text @s/runtime-state)]
    (add-watch text :append
      (fn [_ _ _ new-text]
        (when-let [editor @editor-atom]
          (when (.-parentNode elem)
            (append-text! editor elem text)))))
    (set! (.-innerHTML elem) (format c/ps-repl-html "false"))
    (reify c/Editor
      (get-path [this] c/control-panel-path)
      (get-element [this] elem)
      (get-content [this]
        (.-textContent (.querySelector elem "#content")))
      (get-object [this]
        @editor-atom)
      (can-undo? [this]
        false)
      (can-redo? [this]
        false)
      (undo [this])
      (redo [this])
      (update-content [this])
      (mark-clean [this])
      (clean? [this] true)
      (init [this]
        (-> (.querySelector elem "#content") .-style (aset "whiteSpace" "pre-wrap"))
        (doto
          (reset! editor-atom
            (ps/init (.querySelector elem "#paren-soup")
              (clj->js {:change-callback
                        (fn [event]
                          (repl/scroll-to-bottom elem))
                        :console-callback
                        (fn [text])
                        :compiler-fn (fn [_ _])
                        :disable-clj? true})))
          (append-text! elem text)))
      (set-theme [this theme]
        (swap! s/runtime-state assoc :paren-soup-css (c/paren-soup-themes theme)))
      (hide [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (reset! scroll-top (.-scrollTop ps))))
      (show [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (set! (.-scrollTop ps) @scroll-top))
        (when-let [editor @editor-atom]
          (append-text! editor elem text))))))

(defn buttons [old-prefs new-prefs]
  (list
    [ui/raised-button {:disabled (= old-prefs new-prefs)
                       :on-click (fn []
                                   (reset! s/pref-state new-prefs)
                                   (swap! s/runtime-state assoc :title (:project-name new-prefs)))
                       :key :save}
     "Save"]
    [ui/raised-button {:disabled (= old-prefs new-prefs)
                       :on-click (fn []
                                   (swap! s/runtime-state assoc :new-prefs old-prefs)
                                   (swap! s/runtime-state update :reset-count inc))
                       :key :reset}
     "Reset"]
    [ui/raised-button {:on-click #(swap! s/runtime-state assoc :show-add-library? true)
                       :key :add-library}
     "Add Library"]))

(defn sanitize-name [s]
  (str/replace s #"\"" ""))

(defn new-library-dialog []
  (let [library-name (atom "")
        library-version (atom "")]
    [ui/dialog {:modal true
                :open (some? (:show-add-library? @s/runtime-state))
                :actions
                [(r/as-element
                   [ui/flat-button {:on-click #(swap! s/runtime-state dissoc :show-add-library?)
                                    :style {:margin "10px"}}
                    "Cancel"])
                 (r/as-element
                   [ui/flat-button {:on-click #(do
                                                 (swap! s/runtime-state update-in [:new-prefs :deps]
                                                   conj [(symbol @library-name) @library-version])
                                                 (swap! s/runtime-state dissoc :show-add-library?))
                                    :style {:margin "10px"}}
                    "Add Library"])]}
     [ui/text-field
      {:floating-label-text "Name"
       :hint-text "Example: org.clojure/core.async"
       :on-change #(reset! library-name (sanitize-name (.-value (.-target %))))}]
     [ui/text-field
      {:floating-label-text "Version"
       :hint-text "Example: 1.0.0"
       :on-change #(reset! library-version (sanitize-name (.-value (.-target %))))}]]))

(defn panel [mui-theme reset-count {:keys [deps project-name main-ns] :as new-prefs}]
  (when new-prefs
    [ui/mui-theme-provider
     {:mui-theme mui-theme}
     [:div {:class "lower-half"
            :style {:overflow "auto"}}
      [ui/card {:class "card"
                ; this is a hacky way to force the control panel
                ; to re-render after reset is clicked
                :key (str "control-panel-" reset-count)}
       [ui/card-title {:title "Project Info"}]
       [:div [ui/text-field {:floating-label-text "Name"
                             :defaultValue project-name
                             :on-change (fn [e]
                                          (swap! s/runtime-state assoc-in
                                            [:new-prefs :project-name]
                                            (.-value (.-target e))))}]]
       [:div [ui/text-field {:floating-label-text "Main Namespace"
                             :defaultValue main-ns
                             :on-change (fn [e]
                                          (swap! s/runtime-state assoc-in
                                            [:new-prefs :main-ns]
                                            (.-value (.-target e))))}]]]
      [ui/card {:class "card"}
       [ui/card-title {:title "Libraries"}]
       (for [dep deps]
         (let [text (->> dep (map str) (str/join " "))]
           [ui/chip {:key text
                     :style {:font-family "monospace"
                             :margin "10px"}
                     :on-request-delete (fn [e]
                                          (swap! s/runtime-state
                                            (fn [state]
                                              (->> (get-in state [:new-prefs :deps])
                                                   (remove #(= % dep))
                                                   vec
                                                   (assoc-in state [:new-prefs :deps])))))}
            text]))]
      [new-library-dialog]]]))

