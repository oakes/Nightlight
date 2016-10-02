(ns nightlight.editors
  (:require [paren-soup.core :as ps]
            [clojure.string :as str]
            [nightlight.state :as s]
            [goog.functions :refer [debounce]])
  (:import goog.net.XhrIo))

(def ^:const clojure-exts #{"boot" "clj" "cljc" "cljs" "cljx" "edn" "pxi"})

(defprotocol Editor
  (get-element [this])
  (can-undo? [this])
  (can-redo? [this]))

(def toolbar "
<div class='toolbar'>
  <button type='button' class='btn btn-default navbar-btn'>Save</button>
  <button type='button' class='btn btn-default navbar-btn'>Undo</button>
  <button type='button' class='btn btn-default navbar-btn'>Redo</button>
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

(def auto-save
  (debounce
    (fn []
      (.log js/console "save"))
    1000))

(defn ps-init [elem content]
  (set! (.-innerHTML elem) (str toolbar ps-html))
  (.appendChild (clear-editor) elem)
  (set! (.-textContent (.querySelector js/document ".content")) content)
  (let [editor (ps/init (.querySelector js/document "#paren-soup")
                 (clj->js {:change-callback
                           (fn [e]
                             (when (= (.-type e) "keyup")
                               (auto-save)))}))]
    (reify Editor
      (get-element [this]
        elem)
      (can-undo? [this]
        (ps/can-undo? editor))
      (can-redo? [this]
        (ps/can-redo? editor)))))

(defn cm-init [elem content]
  (set! (.-innerHTML elem) toolbar)
  (.appendChild (clear-editor) elem)
  (let [editor (.CodeMirror js/window
                 elem
                 (clj->js {:value content :lineNumbers true :theme "lesser-dark"}))]
    (.on editor "change"
      (fn [editor change]
        (auto-save)))
    (reify Editor
      (get-element [this]
        elem)
      (can-undo? [this]
        (-> editor .historySize .-undo (> 0)))
      (can-redo? [this]
        (-> editor .historySize .-redo (> 0))))))

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

