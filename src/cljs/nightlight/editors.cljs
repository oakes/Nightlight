(ns nightlight.editors
  (:require [paren-soup.core :as ps]
            [clojure.string :as str]
            [nightlight.state :as s]
            [goog.functions :refer [debounce]])
  (:import goog.net.XhrIo))

(def ^:const clojure-exts #{"boot" "clj" "cljc" "cljs" "cljx" "edn" "pxi"})

(defprotocol Editor
  (get-element [this])
  (get-content [this])
  (can-undo? [this])
  (can-redo? [this])
  (mark-clean [this])
  (clean? [this]))

(def toolbar "
<div class='toolbar'>
  <button type='button' class='btn btn-default navbar-btn' id='save'>Save</button>
  <button type='button' class='btn btn-default navbar-btn' id='undo'>Undo</button>
  <button type='button' class='btn btn-default navbar-btn' id='redo'>Redo</button>
</div>
")

(def ps-html "
<div class='paren-soup' id='paren-soup'>
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

(defn update-buttons [editor]
  (let [save (-> (get-element editor) (.querySelector "#save") .-classList)
        undo (-> (get-element editor) (.querySelector "#undo") .-classList)
        redo (-> (get-element editor) (.querySelector "#redo") .-classList)]
    (if (clean? editor)
      (.add save "disabled")
      (.remove save "disabled"))
    (if (can-undo? editor)
      (.remove undo "disabled")
      (.add undo "disabled"))
    (if (can-redo? editor)
      (.remove redo "disabled")
      (.add redo "disabled"))))

(def auto-save
  (debounce
    (fn [editor]
      (mark-clean editor))
    1000))

(defn ps-init [elem content]
  (set! (.-innerHTML elem) (str toolbar ps-html))
  (.appendChild (clear-editor) elem)
  (set! (.-textContent (.querySelector elem "#content")) content)
  (let [editor-atom (atom nil)
        last-content (atom "")
        editor (reify Editor
                 (get-element [this]
                   elem)
                 (get-content [this]
                   (.-textContent (.querySelector elem "#content")))
                 (can-undo? [this]
                   (some-> @editor-atom ps/can-undo?))
                 (can-redo? [this]
                   (some-> @editor-atom ps/can-redo?))
                 (mark-clean [this]
                   (reset! last-content (get-content this))
                   (update-buttons this))
                 (clean? [this]
                   (= @last-content (get-content this))))]
    (mark-clean editor)
    (reset! editor-atom
      (ps/init (.querySelector elem "#paren-soup")
        (clj->js {:change-callback
                  (fn [event]
                    (when (= (.-type event) "keyup")
                      (auto-save editor))
                    (update-buttons editor))})))
    (update-buttons editor)
    editor))

(defn cm-init [elem content]
  (set! (.-innerHTML elem) toolbar)
  (.appendChild (clear-editor) elem)
  (let [editor-atom (atom nil)
        last-content (atom "")
        editor (reify Editor
                 (get-element [this]
                   elem)
                 (get-content [this]
                   (some-> @editor-atom .getValue))
                 (can-undo? [this]
                   (some-> @editor-atom .historySize .-undo (> 0)))
                 (can-redo? [this]
                   (some-> @editor-atom .historySize .-redo (> 0)))
                 (mark-clean [this]
                   (reset! last-content (get-content this))
                   (update-buttons this))
                 (clean? [this]
                   (= @last-content (get-content this))))]
    (reset! editor-atom
      (doto
        (.CodeMirror js/window
          elem
          (clj->js {:value content :lineNumbers true :theme "lesser-dark"}))
        (.on "change"
          (fn [editor-object change]
            (auto-save editor)
            (update-buttons editor)))))
    (mark-clean editor)
    editor))

(defn create-editor [path content]
  (let [elem (.createElement js/document "span")]
    (if (-> path get-extension clojure-exts some?)
      (ps-init elem content)
      (cm-init elem content))))

(defn read-file [path]
  (if-let [editor (get-in @s/runtime-state [:editors path])]
    (.appendChild (clear-editor) (get-element editor))
    (.send XhrIo
      "/read-file"
      (fn [e]
        (if (.isSuccess (.-target e))
          (->> (.. e -target getResponseText)
               (create-editor path)
               (swap! s/runtime-state update :editors assoc path))
          (clear-editor)))
      "POST"
      path)))

(defn write-file [path content]
  (.send XhrIo
    "/write-file"
    (fn [e])
    "POST"
    (pr-str {:path path :content content})))

