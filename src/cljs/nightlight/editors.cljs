(ns nightlight.editors
  (:require [paren-soup.core :as ps]
            [clojure.string :as str]
            [nightlight.state :as s]
            [nightlight.completions :as com]
            [nightlight.repl :as repl]
            [nightlight.constants :as c]
            [nightlight.control-panel :as cp]
            [nightlight.ajax :as a]
            [goog.functions :refer [debounce]]
            [reagent.core :as r]
            [cljsjs.codemirror]
            [cljsjs.codemirror.mode.css]
            [cljsjs.codemirror.mode.javascript]
            [cljsjs.codemirror.mode.markdown]
            [cljsjs.codemirror.mode.sass]
            [cljsjs.codemirror.mode.shell]
            [cljsjs.codemirror.mode.sql]
            [cljsjs.codemirror.mode.xml]
            [goog.string :refer [format]]
            [goog.string.format]
            [goog.dom :as gdom])
  (:import goog.net.XhrIo))

(defn get-extension [path]
  (->> (.lastIndexOf path ".")
       (+ 1)
       (subs path)
       str/lower-case))

(defn clear-editor []
  (let [editor (.querySelector js/document "#editor")]
    (set! (.-innerHTML editor) "")
    editor))

(def auto-save
  (debounce
    (fn [editor]
      (when (and (:auto-save? @s/pref-state)
                 (not (c/clean? editor)))
        (a/write-file editor)))
    1000))

(defn toggle-instarepl [editor show?]
  (swap! s/runtime-state update :instarepls assoc (c/get-path editor) show?)
  (-> (.querySelector (c/get-element editor) ".instarepl")
      .-style
      (aset "display" (if show? "list-item" "none")))
  (c/init editor))

(defn get-repl [extension]
  (cond
    (#{"clj" "cljc"} extension)
    (get-in @s/runtime-state [:paths c/repl-path :editor])
    (= "cljs" extension)
    (get-in @s/runtime-state [:paths c/cljs-repl-path :editor])))

(defn show-instarepl? [extension]
  (or (and (#{"clj" "cljc"} extension)
           (not (-> @s/runtime-state :options :hosted?)))
      (and (= "cljs" extension)
           (-> @s/runtime-state :enable-cljs-repl?))))

(defn ps-init [path content]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        extension (get-extension path)
        compiler-fn (if (= extension "cljs") repl/compile-cljs repl/compile-clj)
        scroll-top (atom 0)]
    (set! (.-innerHTML elem)
      (format c/ps-html (if (-> @s/runtime-state :options :read-only?)
                           "false" "true")))
    (-> elem (.querySelector "#content") (gdom/setTextContent content))
    (-> elem (.querySelector "#instarepl") .-style (aset "display" "none"))
    (reify c/Editor
      (get-path [this] path)
      (get-extension [this] extension)
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
        (c/update-content this)
        (auto-save this))
      (redo [this]
        (some-> @editor-atom ps/redo)
        (c/update-content this)
        (auto-save this))
      (update-content [this]
        (swap! s/runtime-state update-in [:paths path] assoc :current-content (c/get-content this)))
      (mark-clean [this]
        (swap! s/runtime-state update-in [:paths path] assoc :saved-content (c/get-content this)))
      (clean? [this]
        (= (get-in @s/runtime-state [:paths path :saved-content])
           (get-in @s/runtime-state [:paths path :current-content])))
      (init [this]
        (com/init-completions path extension editor-atom elem)
        (reset! editor-atom
          (ps/init (.querySelector elem "#paren-soup")
            (clj->js {:before-change-callback
                      (fn [event]
                        (com/completion-shortcut? event))
                      :change-callback
                      (fn [event]
                        (when-not (-> @s/runtime-state :options :read-only?)
                          (when (= (.-type event) "keyup")
                            (c/update-content this)
                            (auto-save this))
                          (when (not= (.-type event) "keydown")
                            (com/refresh-completions path extension))
                          (swap! s/runtime-state update-in [:paths path] assoc :eval-code
                            (or (ps/selected-text) (ps/focused-text)))))
                      :compiler-fn compiler-fn}))))
      (set-theme [this theme]
        (swap! s/runtime-state assoc :paren-soup-css (c/paren-soup-themes theme)))
      (hide [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (reset! scroll-top (.-scrollTop ps))))
      (show [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (set! (.-scrollTop ps) @scroll-top)))
      (eval-selection [this]
        (when-let [editor (get-repl extension)]
          (when-let [code (get-in @s/runtime-state [:paths path :eval-code])]
            (c/eval editor code))))
      (eval [this code]))))

(declare set-selection)

(defn ps-repl-init [path]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        text (atom nil)
        sender (repl/create-repl-sender path text)
        scroll-top (atom 0)
        extension (if (= path c/repl-path) "clj" "cljs")]
    (add-watch text :append
      (fn [_ _ _ new-text]
        (when new-text
          (when-let [editor @editor-atom]
            (when (.-parentNode elem)
              (ps/append-text! editor new-text)
              (repl/scroll-to-bottom elem)
              (reset! text nil))))))
    (set! (.-innerHTML elem) (format c/ps-repl-html "true"))
    (reify c/Editor
      (get-path [this] path)
      (get-extension [this] extension)
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
        (c/update-content this))
      (redo [this]
        (some-> @editor-atom ps/redo)
        (c/update-content this))
      (update-content [this]
        (swap! s/runtime-state update-in [:paths path] assoc :current-content (c/get-content this)))
      (mark-clean [this])
      (clean? [this] true)
      (init [this]
        (com/init-completions path extension editor-atom elem)
        (-> (.querySelector elem "#content") .-style (aset "whiteSpace" "pre-wrap"))
        (reset! editor-atom
          (ps/init (.querySelector elem "#paren-soup")
            (clj->js {:before-change-callback
                      (fn [event]
                        (com/completion-shortcut? event))
                      :change-callback
                      (fn [event]
                        (repl/scroll-to-bottom elem)
                        (when (not= (.-type event) "keydown")
                          (com/refresh-completions path extension)
                          (c/update-content this)))
                      :console-callback
                      (fn [text]
                        (repl/send sender text))
                      :compiler-fn (fn [_ _])})))
        (repl/init sender)
        @editor-atom)
      (set-theme [this theme]
        (swap! s/runtime-state assoc :paren-soup-css (c/paren-soup-themes theme)))
      (hide [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (reset! scroll-top (.-scrollTop ps))))
      (show [this]
        (when-let [s @text]
          (when-let [editor @editor-atom]
            (ps/append-text! editor s)
            (reset! text nil)))
        (when-let [ps (.querySelector elem "#paren-soup")]
          (set! (.-scrollTop ps) @scroll-top)))
      (eval-selection [this])
      (eval [this code]
        (swap! text str code \newline)
        (repl/send sender code)
        (set-selection path)
        (when-let [sidebar (.querySelector js/document ".leftsidebar")]
          (set! (.-scrollTop sidebar) 0))))))

(defn cm-init [path content]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        extension (get-extension path)
        scroll-top (atom 0)]
    (reify c/Editor
      (get-path [this] path)
      (get-extension [this] extension)
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
        (c/update-content this)
        (auto-save this))
      (redo [this]
        (some-> @editor-atom .redo)
        (c/update-content this)
        (auto-save this))
      (update-content [this]
        (swap! s/runtime-state update-in [:paths path] assoc :current-content (c/get-content this)))
      (mark-clean [this]
        (swap! s/runtime-state update-in [:paths path] assoc :saved-content (c/get-content this)))
      (clean? [this]
        (= (get-in @s/runtime-state [:paths path :saved-content])
           (get-in @s/runtime-state [:paths path :current-content])))
      (init [this]
        (reset! editor-atom
          (doto
            (.CodeMirror js/window
              elem
              (clj->js {:value content
                        :lineNumbers true
                        :theme (:dark c/codemirror-themes)
                        :mode (c/extension->mode extension)
                        :readOnly (if (-> @s/runtime-state :options :read-only?)
                                    "nocursor"
                                    false)}))
            (.on "change"
              (fn [editor-object change]
                (c/update-content this)
                (auto-save this))))))
      (set-theme [this theme]
        (some-> @editor-atom (.setOption "theme" (c/codemirror-themes theme))))
      (hide [this]
        (when-let [editor @editor-atom]
          (reset! scroll-top (-> editor .getScrollInfo .-top))))
      (show [this]
        (when-let [editor @editor-atom]
          (.scrollTo editor nil @scroll-top)))
      (eval-selection [this])
      (eval [this code]))))

(defn create-editor [path content]
  (if (-> path get-extension c/clojure-exts some?)
    (ps-init path content)
    (cm-init path content)))

(defn show-editor [editor]
  (.appendChild (clear-editor) (c/get-element editor))
  (when-let [outer-editor (.querySelector js/document ".outer-editor")]
    (set! (.-bottom (.-style outer-editor))
      (if (#{c/cljs-repl-path c/control-panel-path}
            (c/get-path editor))
        "50%" "0%")))
  (c/show editor))

(defn init-editor [editor]
  (doto editor
    (show-editor)
    (c/init)
    (c/set-theme (:theme @s/pref-state))))

(defn download-file [path]
  (.send XhrIo
    "read-file"
    (fn [e]
      (if (.isSuccess (.-target e))
        (let [editor (->> (.. e -target getResponseText) (create-editor path) (init-editor))
              content (c/get-content editor)]
          (swap! s/runtime-state assoc-in [:paths path]
            {:editor editor
             :saved-content content
             :current-content content}))
        (clear-editor)))
    "POST"
    path))

(defn init-and-add-editor [path editor]
  (->> editor
       (init-editor)
       (swap! s/runtime-state update-in [:paths path] assoc :editor)))

(defn select-node [path]
  (if-let [editor (get-in @s/runtime-state [:paths path :editor])]
    (show-editor editor)
    (if path
      (download-file path)
      (clear-editor))))

(defn unselect-node [path]
  (when-let [old-editor (get-in @s/runtime-state [:paths path :editor])]
    (c/hide old-editor)))

(defn set-selection [new-path]
  (when-let [old-path (:selection @s/pref-state)]
    (unselect-node old-path))
  (swap! s/pref-state assoc :selection new-path)
  (select-node new-path))

