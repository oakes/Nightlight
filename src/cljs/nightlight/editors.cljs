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
            [cljsjs.codemirror.mode.clojure]
            [cljsjs.codemirror.mode.css]
            [cljsjs.codemirror.mode.javascript]
            [cljsjs.codemirror.mode.markdown]
            [cljsjs.codemirror.mode.sass]
            [cljsjs.codemirror.mode.shell]
            [cljsjs.codemirror.mode.sql]
            [cljsjs.codemirror.mode.xml]
            [goog.string :refer [format]]
            [goog.string.format])
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
      (when (:auto-save? @s/pref-state)
        (a/write-file editor)))
    1000))

(defn toggle-instarepl [editor show?]
  (swap! s/runtime-state update :instarepls assoc (c/get-path editor) show?)
  (-> (.querySelector (c/get-element editor) ".instarepl")
      .-style
      (aset "display" (if show? "list-item" "none")))
  (c/init editor))

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
        completions? (c/completion-exts extension)
        scroll-top (atom 0)]
    (set! (.-innerHTML elem)
      (format c/ps-html (if (-> @s/runtime-state :options :read-only?)
                           "false" "true")))
    (set! (.-textContent (.querySelector elem "#content")) content)
    (-> elem (.querySelector "#instarepl") .-style (aset "display" "none"))
    (reify c/Editor
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
        (c/update-content this)
        (auto-save this))
      (redo [this]
        (some-> @editor-atom ps/redo)
        (c/update-content this)
        (auto-save this))
      (update-content [this]
        (swap! s/runtime-state update :current-content assoc path (c/get-content this)))
      (mark-clean [this]
        (swap! s/runtime-state update :saved-content assoc path (c/get-content this)))
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
                        (when-not (-> @s/runtime-state :options :read-only?)
                          (when (= (.-type event) "keyup")
                            (c/update-content this)
                            (auto-save this))
                          (when (and completions? (not= (.-type event) "keydown"))
                            (com/refresh-completions path))))
                      :compiler-fn compiler-fn}))))
      (set-theme [this theme]
        (swap! s/runtime-state assoc :paren-soup-css (c/paren-soup-themes theme)))
      (hide [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (reset! scroll-top (.-scrollTop ps))))
      (show [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (set! (.-scrollTop ps) @scroll-top))))))

(defn ps-repl-init [path]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        text (atom nil)
        sender (repl/create-repl-sender path text)
        scroll-top (atom 0)]
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
        (swap! s/runtime-state update :current-content assoc path (c/get-content this)))
      (mark-clean [this])
      (clean? [this] true)
      (init [this]
        (com/init-completions path editor-atom elem)
        (-> (.querySelector elem "#content") .-style (aset "whiteSpace" "pre-wrap"))
        (reset! editor-atom
          (ps/init (.querySelector elem "#paren-soup")
            (clj->js {:before-change-callback
                      (fn [event]
                        (com/completion-shortcut? event))
                      :change-callback
                      (fn [event]
                        (repl/scroll-to-bottom elem)
                        (when (and (= path c/repl-path) (not= (.-type event) "keydown"))
                          (com/refresh-completions path)
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
          (set! (.-scrollTop ps) @scroll-top))))))

(defn cm-init [path content]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        extension (get-extension path)
        scroll-top (atom 0)]
    (reify c/Editor
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
        (c/update-content this)
        (auto-save this))
      (redo [this]
        (some-> @editor-atom .redo)
        (c/update-content this)
        (auto-save this))
      (update-content [this]
        (swap! s/runtime-state update :current-content assoc path (c/get-content this)))
      (mark-clean [this]
        (swap! s/runtime-state update :saved-content assoc path (c/get-content this)))
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
          (.scrollTo editor nil @scroll-top))))))

(defn create-editor [path content]
  (if (and (-> path get-extension c/clojure-exts some?)
           (= -1 (.indexOf js/navigator.userAgent "Edge")))
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
          (swap! s/runtime-state
            (fn [state]
              (-> state
                  (assoc-in [:editors path] editor)
                  (assoc-in [:saved-content path] content)
                  (assoc-in [:current-content path] content)))))
        (clear-editor)))
    "POST"
    path))

(defn init-and-add-editor [path editor]
  (->> editor
       (init-editor)
       (swap! s/runtime-state update :editors assoc path)))

(defn select-node [path]
  (if-let [editor (get-in @s/runtime-state [:editors path])]
    (show-editor editor)
    (cond
      (c/repl-path? path)
      (init-and-add-editor path (ps-repl-init path))
      (= path c/control-panel-path)
      (when (-> @s/runtime-state :options :hosted?)
        (init-and-add-editor c/control-panel-path (cp/control-panel-init)))
      (nil? path)
      (clear-editor)
      :else
      (download-file path))))

(defn unselect-node [path]
  (when-let [old-editor (get-in @s/runtime-state [:editors path])]
    (c/hide old-editor)))

