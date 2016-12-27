(ns nightlight.editors
  (:require [paren-soup.core :as ps]
            [clojure.string :as str]
            [nightlight.state :as s]
            [nightlight.completions :as com]
            [nightlight.repl :as repl]
            [nightlight.editor-utils :as eu]
            [nightlight.status :as status]
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

(defn write-file [editor]
  (when-not (-> @s/runtime-state :options :read-only?)
    (.send XhrIo
      "write-file"
      (fn [e]
        (eu/mark-clean editor))
      "POST"
      (pr-str {:path (eu/get-path editor) :content (eu/get-content editor)}))))

(def auto-save
  (debounce
    (fn [editor]
      (when (:auto-save? @s/pref-state)
        (write-file editor)))
    1000))

(defn toggle-instarepl [editor show?]
  (swap! s/runtime-state update :instarepls assoc (eu/get-path editor) show?)
  (-> (.querySelector (eu/get-element editor) ".instarepl")
      .-style
      (aset "display" (if show? "list-item" "none")))
  (eu/init editor))

(defn show-instarepl? [extension]
  (or (and (#{"clj" "cljc"} extension)
           (not (-> @s/runtime-state :options :hosted?)))
      (and (= "cljs" extension)
           (-> @s/runtime-state :options :url))))

(defn ps-init [path content]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        extension (get-extension path)
        compiler-fn (if (= extension "cljs") repl/compile-cljs repl/compile-clj)
        completions? (eu/completion-exts extension)
        scroll-top (atom 0)]
    (set! (.-innerHTML elem)
      (format eu/ps-html (if (-> @s/runtime-state :options :read-only?)
                           "false" "true")))
    (set! (.-textContent (.querySelector elem "#content")) content)
    (-> elem (.querySelector "#instarepl") .-style (aset "display" "none"))
    (reify eu/Editor
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
        (eu/update-content this)
        (auto-save this))
      (redo [this]
        (some-> @editor-atom ps/redo)
        (eu/update-content this)
        (auto-save this))
      (update-content [this]
        (swap! s/runtime-state update :current-content assoc path (eu/get-content this)))
      (mark-clean [this]
        (swap! s/runtime-state update :saved-content assoc path (eu/get-content this)))
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
                            (eu/update-content this)
                            (auto-save this))
                          (when (and completions? (not= (.-type event) "keydown"))
                            (com/refresh-completions path))))
                      :compiler-fn compiler-fn}))))
      (set-theme [this theme]
        (swap! s/runtime-state assoc :paren-soup-css (eu/paren-soup-themes theme)))
      (save-scroll-position [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (reset! scroll-top (.-scrollTop ps))))
      (update-scroll-position [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (set! (.-scrollTop ps) @scroll-top))))))

(defn ps-repl-init [path]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        sender (repl/create-repl-sender path elem editor-atom)
        scroll-top (atom 0)]
    (set! (.-innerHTML elem) (format eu/ps-repl-html "true"))
    (reify eu/Editor
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
        (eu/update-content this))
      (redo [this]
        (some-> @editor-atom ps/redo)
        (eu/update-content this))
      (update-content [this]
        (swap! s/runtime-state update :current-content assoc path (eu/get-content this)))
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
                        (when (not= (.-type event) "keydown")
                          (com/refresh-completions path)
                          (eu/update-content this)))
                      :console-callback
                      (fn [text]
                        (repl/send sender text))
                      :compiler-fn (fn [_ _])})))
        (repl/init sender)
        @editor-atom)
      (set-theme [this theme]
        (swap! s/runtime-state assoc :paren-soup-css (eu/paren-soup-themes theme)))
      (save-scroll-position [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (reset! scroll-top (.-scrollTop ps))))
      (update-scroll-position [this]
        (when-let [ps (.querySelector elem "#paren-soup")]
          (set! (.-scrollTop ps) @scroll-top))))))

(defn cm-init [path content]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        extension (get-extension path)
        scroll-top (atom 0)]
    (reify eu/Editor
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
        (eu/update-content this)
        (auto-save this))
      (redo [this]
        (some-> @editor-atom .redo)
        (eu/update-content this)
        (auto-save this))
      (update-content [this]
        (swap! s/runtime-state update :current-content assoc path (eu/get-content this)))
      (mark-clean [this]
        (swap! s/runtime-state update :saved-content assoc path (eu/get-content this)))
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
                        :theme (:dark eu/codemirror-themes)
                        :mode (eu/extension->mode extension)
                        :readOnly (if (-> @s/runtime-state :options :read-only?)
                                    "nocursor"
                                    false)}))
            (.on "change"
              (fn [editor-object change]
                (eu/update-content this)
                (auto-save this))))))
      (set-theme [this theme]
        (some-> @editor-atom (.setOption "theme" (eu/codemirror-themes theme))))
      (save-scroll-position [this]
        (when-let [editor @editor-atom]
          (reset! scroll-top (-> editor .getScrollInfo .-top))))
      (update-scroll-position [this]
        (when-let [editor @editor-atom]
          (.scrollTo editor nil @scroll-top))))))

(defn create-editor [path content]
  (if (-> path get-extension eu/clojure-exts some?)
    (ps-init path content)
    (cm-init path content)))

(defn show-editor [editor]
  (.appendChild (clear-editor) (eu/get-element editor))
  (when-let [outer-editor (.querySelector js/document ".outer-editor")]
    (set! (.-bottom (.-style outer-editor))
      (if (#{repl/cljs-repl-path status/status-path}
            (eu/get-path editor))
        "50%" "0%")))
  (eu/update-scroll-position editor))

(defn init-editor [editor]
  (doto editor
    (show-editor)
    (eu/init)
    (eu/set-theme (:theme @s/pref-state))))

(defn download-file [path]
  (.send XhrIo
    "read-file"
    (fn [e]
      (if (.isSuccess (.-target e))
        (let [editor (->> (.. e -target getResponseText) (create-editor path) (init-editor))
              content (eu/get-content editor)]
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
      (repl/repl-path? path)
      (init-and-add-editor path (ps-repl-init path))
      (= path status/status-path)
      (init-and-add-editor status/status-path (status/status-init))
      :else
      (download-file path))))

(defn unselect-node [path]
  (when-let [old-editor (get-in @s/runtime-state [:editors path])]
    (eu/save-scroll-position old-editor)))

