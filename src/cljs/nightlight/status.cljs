(ns nightlight.status
  (:require [nightlight.repl :as repl]
            [nightlight.editor-utils :as eu]
            [nightlight.state :as s]
            [paren-soup.core :as ps]
            [goog.string :refer [format]]
            [goog.string.format]
            [cljs-react-material-ui.reagent :as ui]))

(def ^:const status-path "*STATUS*")

(def ^:const status-item
  {:primary-text "Status"
   :value "*STATUS*"
   :style {:font-weight "bold"}})

(defn init-status-receiver []
  (let [protocol (if (= (.-protocol js/location) "https:") "wss:" "ws:")
        host (-> js/window .-location .-host)
        path (-> js/window .-location .-pathname)
        sock (js/WebSocket. (str protocol "//" host path "status"))]
    (set! (.-onopen sock)
      (fn [event]
        (.send sock "")))
    (swap! s/runtime-state assoc :status-socket sock)))

(defn status-init []
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        scroll-top (atom 0)
        text (atom nil)
        sock (:status-socket @s/runtime-state)]
    (add-watch text :append
      (fn [_ _ _ new-text]
        (when new-text
          (when-let [editor @editor-atom]
            (when (.-parentNode elem)
              (ps/append-text! editor new-text)
              (repl/scroll-to-bottom elem)
              (reset! text nil))))))
    (set! (.-innerHTML elem) (format eu/ps-repl-html "false"))
    (reify eu/Editor
      (get-path [this] status-path)
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
        (reset! editor-atom
          (ps/init (.querySelector elem "#paren-soup")
            (clj->js {:change-callback
                      (fn [event]
                        (repl/scroll-to-bottom elem))
                      :console-callback
                      (fn [text])
                      :compiler-fn (fn [_ _])
                      :disable-clj? true})))
        (set! (.-onmessage sock)
          (fn [event]
            (swap! text str (.-data event))))
        @editor-atom)
      (set-theme [this theme]
        (swap! s/runtime-state assoc :paren-soup-css (eu/paren-soup-themes theme)))
      (hide [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (reset! scroll-top (.-scrollTop ps))))
      (show [this]
        (when-let [s @text]
          (when-let [editor @editor-atom]
            (ps/append-text! editor s)
            (reset! text nil)))
        (when-let [ps (.querySelector elem "#paren-soup")]
          (set! (.-scrollTop ps) @scroll-top))))))

(defn buttons []
  (list
    [ui/raised-button {:disabled false
                       :on-click (fn [])
                       :key :save}
     "Save"]))

