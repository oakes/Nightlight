(ns nightlight.editors
  (:require [paren-soup.core :as ps]
            [clojure.string :as str])
  (:import goog.net.XhrIo))

(def ^:const clojure-exts #{"boot" "clj" "cljc" "cljs" "cljx" "edn" "pxi"})

(defonce editors (atom {}))

(def toolbar "
<div>
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

(defn create-element [path content]
  (let [elem (.createElement js/document "span")
        clojure? (-> path get-extension clojure-exts some?)]
    (set! (.-innerHTML elem) (str toolbar (if clojure? ps-html "")))
    (.appendChild (clear-editor) elem)
    (if clojure?
      (do
        (set! (.-textContent (.querySelector js/document ".content")) content)
        (ps/init-all))
      (.CodeMirror js/window
        elem
        (clj->js {:value content :lineNumbers true :theme "lesser-dark"})))
    elem))

(defn init-file [path]
  (if-let [elem (get @editors path)]
    (.appendChild (clear-editor) elem)
    (.send XhrIo
      "/file"
      (fn [e]
        (if (.isSuccess (.-target e))
          (->> (.. e -target getResponseText)
               (create-element path)
               (swap! editors assoc path))
          (clear-editor)))
      "POST"
      path)))

