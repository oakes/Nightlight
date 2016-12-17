(ns nightlight.editors
  (:require [paren-soup.core :as ps]
            [clojure.string :as str]
            [nightlight.state :as s]
            [nightlight.completions :as com]
            [nightlight.repl :as repl]
            [goog.functions :refer [debounce]]
            [reagent.core :as r]
            [reagent.dom.server :refer [render-to-static-markup]]
            [cljsjs.codemirror]
            [cljsjs.codemirror.mode.css]
            [cljsjs.codemirror.mode.javascript]
            [cljsjs.codemirror.mode.markdown]
            [cljsjs.codemirror.mode.sass]
            [cljsjs.codemirror.mode.shell]
            [cljsjs.codemirror.mode.sql]
            [cljsjs.codemirror.mode.xml])
  (:import goog.net.XhrIo))

(def ^:const clojure-exts #{"boot" "clj" "cljc" "cljs" "cljx" "edn" "pxi" "hl"})
(def ^:const completion-exts #{"clj"})
(def ^:const paren-soup-themes {:dark "paren-soup-dark.css" :light "paren-soup-light.css"})
(def ^:const codemirror-themes {:dark "lesser-dark" :light "default"})
(def ^:const extension->mode
  {"css" "css"
   "js" "javascript"
   "md" "markdown"
   "markdown" "markdown"
   "sass" "sass"
   "sh" "shell"
   "sql" "sql"
   "html" "xml"
   "xml" "xml"})

(def ^:const ps-html
  (render-to-static-markup
    [:div {:class "paren-soup" :id "paren-soup"}
     [:div {:class "instarepl" :id "instarepl"}]
     [:div {:class "numbers" :id "numbers"}]
     [:div {:class "content" :id "content" :contentEditable true}]]))

(def ^:const ps-repl-html
  (render-to-static-markup
    [:div {:class "paren-soup" :id "paren-soup"}
     [:div {:class "content" :id "content" :contentEditable true}]]))

(defprotocol Editor
  (get-path [this])
  (get-element [this])
  (get-content [this])
  (get-object [this])
  (can-undo? [this])
  (can-redo? [this])
  (undo [this])
  (redo [this])
  (update-content [this])
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

(def auto-save
  (debounce
    (fn [editor]
      (when (:auto-save? @s/pref-state)
        (write-file editor)))
    1000))

(defn toggle-instarepl [editor show?]
  (swap! s/runtime-state update :instarepls assoc (get-path editor) show?)
  (-> (.querySelector (get-element editor) ".instarepl")
      .-style
      (aset "display" (if show? "list-item" "none")))
  (init editor))

(defn show-instarepl? [extension]
  (or (#{"clj" "cljc"} extension)
      (and (= "cljs" extension) (-> @s/runtime-state :options :url))))

(defn ps-init [path content]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
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
      (get-object [this]
        @editor-atom)
      (can-undo? [this]
        (some-> @editor-atom ps/can-undo?))
      (can-redo? [this]
        (some-> @editor-atom ps/can-redo?))
      (undo [this]
        (some-> @editor-atom ps/undo)
        (update-content this)
        (auto-save this))
      (redo [this]
        (some-> @editor-atom ps/redo)
        (update-content this)
        (auto-save this))
      (update-content [this]
        (swap! s/runtime-state update :current-content assoc path (get-content this)))
      (mark-clean [this]
        (swap! s/runtime-state update :saved-content assoc path (get-content this)))
      (clean? [this]
        (= (get-in @s/runtime-state [:saved-content path])
           (get-in @s/runtime-state [:current-content path])))
      (init [this]
        (when completions?
          (com/init-completions path editor-atom elem))
        (reset! editor-atom
          (ps/init (.querySelector elem "#paren-soup")
            (clj->js {:before-change-callback
                      (fn [event]
                        (com/completion-shortcut? event))
                      :change-callback
                      (fn [event]
                        (when (= (.-type event) "keyup")
                          (update-content this)
                          (auto-save this))
                        (when (and completions? (not= (.-type event) "keydown"))
                          (com/refresh-completions path)))
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
      (get-object [this]
        @editor-atom)
      (can-undo? [this]
        (some-> @editor-atom ps/can-undo?))
      (can-redo? [this]
        (some-> @editor-atom ps/can-redo?))
      (undo [this]
        (some-> @editor-atom ps/undo)
        (update-content this))
      (redo [this]
        (some-> @editor-atom ps/redo)
        (update-content this))
      (update-content [this]
        (swap! s/runtime-state update :current-content assoc path (get-content this)))
      (mark-clean [this])
      (clean? [this] true)
      (init [this]
        (com/init-completions path editor-atom elem)
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
                        (repl/scroll-to-bottom elem)
                        (when (not= (.-type event) "keydown")
                          (com/refresh-completions path)
                          (update-content this)))
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
        extension (get-extension path)]
    (reify Editor
      (get-path [this] path)
      (get-element [this] elem)
      (get-content [this]
        (some-> @editor-atom .getValue))
      (get-object [this]
        @editor-atom)
      (can-undo? [this]
        (some-> @editor-atom .historySize .-undo (> 0)))
      (can-redo? [this]
        (some-> @editor-atom .historySize .-redo (> 0)))
      (undo [this]
        (some-> @editor-atom .undo)
        (update-content this)
        (auto-save this))
      (redo [this]
        (some-> @editor-atom .redo)
        (update-content this)
        (auto-save this))
      (update-content [this]
        (swap! s/runtime-state update :current-content assoc path (get-content this)))
      (mark-clean [this]
        (swap! s/runtime-state update :saved-content assoc path (get-content this)))
      (clean? [this]
        (= (get-in @s/runtime-state [:saved-content path])
           (get-in @s/runtime-state [:current-content path])))
      (init [this]
        (reset! editor-atom
          (doto
            (.CodeMirror js/window
              elem
              (clj->js {:value content
                        :lineNumbers true
                        :theme (:dark codemirror-themes)
                        :mode (extension->mode extension)}))
            (.on "change"
              (fn [editor-object change]
                (update-content this)
                (auto-save this))))))
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
    (set-theme (:theme @s/pref-state))))

(defn init-repl [path]
  (->> (ps-repl-init path)
       (init-editor)
       (swap! s/runtime-state update :editors assoc path)))

(defn download-file [path]
  (.send XhrIo
    "/read-file"
    (fn [e]
      (if (.isSuccess (.-target e))
        (let [editor (->> (.. e -target getResponseText) (create-editor path) (init-editor))
              content (get-content editor)]
          (swap! s/runtime-state
            (fn [state]
              (-> state
                  (assoc-in [:editors path] editor)
                  (assoc-in [:saved-content path] content)
                  (assoc-in [:current-content path] content)))))
        (clear-editor)))
    "POST"
    path))

(defn select-node [path]
  (if-let [editor (get-in @s/runtime-state [:editors path])]
    (show-editor editor)
    (if (repl/repl-path? path)
      (init-repl path)
      (download-file path))))

