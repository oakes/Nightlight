(ns nightlight.editors
  (:require [paren-soup.core :as ps]
            [clojure.string :as str]
            [nightlight.state :as s]
            [nightlight.completions :as com]
            [nightlight.repl :as repl]
            [goog.functions :refer [debounce]]
            [cljsjs.codemirror]
            [reagent.dom.server :refer [render-to-static-markup]])
  (:import goog.net.XhrIo))

(def ^:const clojure-exts #{"boot" "clj" "cljc" "cljs" "cljx" "edn" "pxi" "hl"})
(def ^:const completion-exts #{"clj"})
(def ^:const paren-soup-themes {:dark "paren-soup-dark.css" :light "paren-soup-light.css"})
(def ^:const codemirror-themes {:dark "lesser-dark" :light "default"})

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
     [:div {:class "paren-soup" :id "paren-soup"}
      [:div {:class "content" :id "content" :contentEditable true}]]
     [:div {:class "rightsidebar"}
      [:div {:id "completions"}]]]))

(defprotocol Editor
  (get-path [this])
  (get-element [this])
  (get-content [this])
  (can-undo? [this])
  (can-redo? [this])
  (undo [this])
  (redo [this])
  (mark-clean [this])
  (clean? [this])
  (init [this])
  (set-theme [this theme]))

(defn get-extension [path]
  (->> (.lastIndexOf path ".")
       (+ 1)
       (subs path)
       str/lower-case))

(defn clear-editor []
  (let [editor (.querySelector js/document "#editor")]
    (set! (.-innerHTML editor) "")
    editor))

(defn write-file [editor]
  (.send XhrIo
    "/write-file"
    (fn [e]
      (mark-clean editor))
    "POST"
    (pr-str {:path (get-path editor) :content (get-content editor)})))

(defn connect-buttons [editor]
  (some-> (get-element editor)
          (.querySelector "#save")
          (.addEventListener "click" #(write-file editor)))
  (some-> (get-element editor)
          (.querySelector "#undo")
          (.addEventListener "click" #(undo editor)))
  (some-> (get-element editor)
          (.querySelector "#redo")
          (.addEventListener "click" #(redo editor))))

(defn update-buttons [editor]
  (when-let [save-css (some-> (get-element editor) (.querySelector "#save") .-classList)]
    (if (clean? editor)
      (.add save-css "disabled")
      (.remove save-css "disabled")))
  (when-let [undo-css (some-> (get-element editor) (.querySelector "#undo") .-classList)]
    (if (can-undo? editor)
      (.remove undo-css "disabled")
      (.add undo-css "disabled")))
  (when-let [redo-css (some-> (get-element editor) (.querySelector "#redo") .-classList)]
    (if (can-redo? editor)
      (.remove redo-css "disabled")
      (.add redo-css "disabled"))))

(def auto-save
  (debounce
    (fn [editor]
      (when (:auto-save? @s/pref-state)
        (write-file editor)))
    1000))

(defn toggle-instarepl [editor show?]
  (-> (.querySelector (get-element editor) ".instarepl")
      .-style
      (aset "display" (if show? "list-item" "none")))
  (init editor))

(defn show-instarepl? [extension]
  (or (#{"clj" "cljc"} extension)
      (and (= "cljs" extension) (-> @s/runtime-state :options :url))))

(defn init-instarepl [editor]
  (if (-> editor get-path get-extension show-instarepl?)
    (doto (js/$ "#toggleInstaRepl")
      (.bootstrapToggle "off")
      (.change (fn [e] (toggle-instarepl editor (-> e .-target .-checked)))))
    (some-> js/document
            (.querySelector "#toggleInstaRepl")
            .-style
            (aset "display" "none"))))

(defn ps-init [path content]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        last-content (atom content)
        extension (get-extension path)
        compiler-fn (if (= extension "cljs") repl/compile-cljs repl/compile-clj)
        completions? (completion-exts extension)]
    (set! (.-innerHTML elem) ps-html)
    (set! (.-textContent (.querySelector elem "#content")) content)
    (-> elem (.querySelector "#instarepl") .-style (aset "display" "none"))
    (reify Editor
      (get-path [this] path)
      (get-element [this] elem)
      (get-content [this]
        (.-textContent (.querySelector elem "#content")))
      (can-undo? [this]
        (some-> @editor-atom ps/can-undo?))
      (can-redo? [this]
        (some-> @editor-atom ps/can-redo?))
      (undo [this]
        (some-> @editor-atom ps/undo)
        (auto-save this)
        (update-buttons this))
      (redo [this]
        (some-> @editor-atom ps/redo)
        (auto-save this)
        (update-buttons this))
      (mark-clean [this]
        (reset! last-content (get-content this))
        (update-buttons this))
      (clean? [this]
        (= @last-content (get-content this)))
      (init [this]
        (when completions?
          (com/init-completions editor-atom elem))
        (reset! editor-atom
          (ps/init (.querySelector elem "#paren-soup")
            (clj->js {:before-change-callback
                      (fn [event]
                        (com/completion-shortcut? event))
                      :change-callback
                      (fn [event]
                        (when (= (.-type event) "keyup")
                          (auto-save this))
                        (update-buttons this)
                        (when (and completions? (not= (.-type event) "keydown"))
                          (com/refresh-completions @editor-atom)))
                      :compiler-fn compiler-fn}))))
      (set-theme [this theme]
        (swap! s/runtime-state assoc :paren-soup-css (paren-soup-themes theme))))))

(defn ps-repl-init [path]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        sender (repl/create-repl-sender path elem editor-atom)]
    (set! (.-innerHTML elem) ps-repl-html)
    (reify Editor
      (get-path [this] path)
      (get-element [this] elem)
      (get-content [this]
        (.-textContent (.querySelector elem "#content")))
      (can-undo? [this]
        (some-> @editor-atom ps/can-undo?))
      (can-redo? [this]
        (some-> @editor-atom ps/can-redo?))
      (undo [this]
        (some-> @editor-atom ps/undo)
        (update-buttons this))
      (redo [this]
        (some-> @editor-atom ps/redo)
        (update-buttons this))
      (mark-clean [this])
      (clean? [this] true)
      (init [this]
        (com/init-completions editor-atom elem)
        (-> (.querySelector elem "#content") .-style (aset "whiteSpace" "pre-wrap"))
        (when (= path repl/cljs-repl-path)
          (-> (.querySelector elem "#paren-soup") .-style (aset "height" "50%")))
        (reset! editor-atom
          (ps/init (.querySelector elem "#paren-soup")
            (clj->js {:before-change-callback
                      (fn [event]
                        (com/completion-shortcut? event))
                      :change-callback
                      (fn [event]
                        (update-buttons this)
                        (repl/scroll-to-bottom elem)
                        (when (not= (.-type event) "keydown")
                          (com/refresh-completions @editor-atom)))
                      :console-callback
                      (fn [text]
                        (repl/send sender text))
                      :compiler-fn (fn [_ _])})))
        (repl/init sender)
        @editor-atom)
      (set-theme [this theme]
        (swap! s/runtime-state assoc :paren-soup-css (paren-soup-themes theme))))))

(defn cm-init [path content]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        last-content (atom content)]
    (reify Editor
      (get-path [this] path)
      (get-element [this] elem)
      (get-content [this]
        (some-> @editor-atom .getValue))
      (can-undo? [this]
        (some-> @editor-atom .historySize .-undo (> 0)))
      (can-redo? [this]
        (some-> @editor-atom .historySize .-redo (> 0)))
      (undo [this]
        (some-> @editor-atom .undo)
        (auto-save this)
        (update-buttons this))
      (redo [this]
        (some-> @editor-atom .redo)
        (auto-save this)
        (update-buttons this))
      (mark-clean [this]
        (reset! last-content (get-content this))
        (update-buttons this))
      (clean? [this]
        (= @last-content (get-content this)))
      (init [this]
        (reset! editor-atom
          (doto
            (.CodeMirror js/window
              elem
              (clj->js {:value content :lineNumbers true :theme (:dark codemirror-themes)}))
            (.on "change"
              (fn [editor-object change]
                (auto-save this)
                (update-buttons this))))))
      (set-theme [this theme]
        (some-> @editor-atom (.setOption "theme" (codemirror-themes theme)))))))

(defn create-editor [path content]
  (if (-> path get-extension clojure-exts some?)
    (ps-init path content)
    (cm-init path content)))

(defn show-editor [editor]
  (.appendChild (clear-editor) (get-element editor)))

(defn init-editor [editor]
  (doto editor
    (show-editor)
    (init)
    (init-instarepl)
    (set-theme (:theme @s/pref-state))
    (update-buttons)
    (connect-buttons)))

(defn init-repl [path]
  (->> (ps-repl-init path)
       (init-editor)
       (swap! s/runtime-state update :editors assoc path)))

(defn download-file [path]
  (.send XhrIo
    "/read-file"
    (fn [e]
      (if (.isSuccess (.-target e))
        (->> (.. e -target getResponseText)
             (create-editor path)
             (init-editor)
             (swap! s/runtime-state update :editors assoc path))
        (clear-editor)))
    "POST"
    path))

(defn select-node [path]
  (if-let [editor (get-in @s/runtime-state [:editors path])]
    (show-editor editor)
    (if (repl/repl-path? path)
      (init-repl path)
      (download-file path))))

