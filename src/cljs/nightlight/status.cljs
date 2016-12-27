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

(defn status-receiver [elem editor-atom]
  (let [protocol (if (= (.-protocol js/location) "https:") "wss:" "ws:")
        host (-> js/window .-location .-host)
        path (-> js/window .-location .-pathname)
        sock (js/WebSocket. (str protocol "//" host path "status"))]
    (set! (.-onmessage sock)
      (fn [event]
        (some-> @editor-atom (ps/append-text! (.-data event)))
        (repl/scroll-to-bottom elem)))))

(defn status-init []
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        scroll-top (atom 0)]
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
        (status-receiver elem editor-atom)
        @editor-atom)
      (set-theme [this theme]
        (swap! s/runtime-state assoc :paren-soup-css (eu/paren-soup-themes theme)))
      (save-scroll-position [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (reset! scroll-top (.-scrollTop ps))))
      (update-scroll-position [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (set! (.-scrollTop ps) @scroll-top))))))

(defn restart-button []
  [ui/raised-button {:disabled false
                     :on-click (fn [])}
   "Save"])

