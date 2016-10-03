(ns nightlight.editors
  (:require [paren-soup.core :as ps]
            [clojure.string :as str]
            [nightlight.state :as s]
            [goog.functions :refer [debounce]])
  (:import goog.net.XhrIo))

(def ^:const clojure-exts #{"boot" "clj" "cljc" "cljs" "cljx" "edn" "pxi"})

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
  (show [this]))

(def toolbar "
<div class='toolbar'>
  <button type='button' class='btn btn-default navbar-btn' id='save'>Save</button>
  <button type='button' class='btn btn-default navbar-btn' id='undo'>Undo</button>
  <button type='button' class='btn btn-default navbar-btn' id='redo'>Redo</button>
</div>
")

(def ps-html "
<div class='paren-soup' id='paren-soup'>
  <div class='instarepl' id='instarepl'></div>
  <div class='numbers' id='numbers'></div>
  <div class='content' contenteditable='true' id='content'></div>
</div>
")

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
  (let [save-elem (-> (get-element editor) (.querySelector "#save"))
        undo-elem (-> (get-element editor) (.querySelector "#undo"))
        redo-elem (-> (get-element editor) (.querySelector "#redo"))]
    (.addEventListener save-elem "click" #(write-file editor))
    (.addEventListener undo-elem "click" #(undo editor))
    (.addEventListener redo-elem "click" #(redo editor))))

(defn update-buttons [editor]
  (let [save-css (-> (get-element editor) (.querySelector "#save") .-classList)
        undo-css (-> (get-element editor) (.querySelector "#undo") .-classList)
        redo-css (-> (get-element editor) (.querySelector "#redo") .-classList)]
    (if (clean? editor)
      (.add save-css "disabled")
      (.remove save-css "disabled"))
    (if (can-undo? editor)
      (.remove undo-css "disabled")
      (.add undo-css "disabled"))
    (if (can-redo? editor)
      (.remove redo-css "disabled")
      (.add redo-css "disabled"))))

(def auto-save
  (debounce
    (fn [editor]
      (when (:auto-save? @s/pref-state)
        (write-file editor)))
    1000))

(defn ps-init [path content]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        last-content (atom content)]
    (set! (.-innerHTML elem) (str toolbar ps-html))
    (set! (.-textContent (.querySelector elem "#content")) content)
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
        (reset! editor-atom
          (ps/init (.querySelector elem "#paren-soup")
            (clj->js {:change-callback
                      (fn [event]
                        (when (= (.-type event) "keyup")
                          (auto-save this))
                        (update-buttons this))}))))
      (show [this]
        (.appendChild (clear-editor) elem)
        (-> elem
            (.querySelector ".instarepl")
            .-style
            (aset "display" (if (:instarepl? @s/pref-state) "list-item" "none")))
        (init this)))))

(defn cm-init [path content]
  (let [elem (.createElement js/document "span")
        editor-atom (atom nil)
        last-content (atom content)]
    (set! (.-innerHTML elem) toolbar)
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
              (clj->js {:value content :lineNumbers true :theme "lesser-dark"}))
            (.on "change"
              (fn [editor-object change]
                (auto-save this)
                (update-buttons this))))))
      (show [this]
        (.appendChild (clear-editor) (get-element this))
        (init this)))))

(defn create-editor [path content]
  (if (-> path get-extension clojure-exts some?)
    (ps-init path content)
    (cm-init path content)))

(defn init-editor [editor]
  (doto editor
    (show)
    (update-buttons)
    (connect-buttons)))

(defn read-file [path]
  (if-let [editor (get-in @s/runtime-state [:editors path])]
    (show editor)
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
      path)))

(defn refresh []
  (when-let [path (:selection @s/pref-state)]
    (when-let [editor (get-in @s/runtime-state [:editors path])]
      (show editor))))

