(ns nightlight.editors
  (:require [paren-soup.core :as ps]
            [clojure.string :as str]
            [nightlight.state :as s])
  (:import goog.net.XhrIo))

(def ^:const clojure-exts #{"boot" "clj" "cljc" "cljs" "cljx" "edn" "pxi"})

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

(defn read-file [path]
  (if-let [elem (get-in @s/runtime-state [:editors path])]
    (.appendChild (clear-editor) elem)
    (.send XhrIo
      "/read-file"
      (fn [e]
        (if (.isSuccess (.-target e))
          (->> (.. e -target getResponseText)
               (create-element path)
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

