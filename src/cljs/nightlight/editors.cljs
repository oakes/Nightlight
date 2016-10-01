(ns nightlight.editors
  (:require [goog.string :refer [format]]
            [goog.string.format]
            [paren-soup.core :as ps])
  (:import goog.net.XhrIo))

(defonce editors (atom {}))

(def ps-html "
<div>
  <button type='button' class='btn btn-default navbar-btn'>Save</button>
  <button type='button' class='btn btn-default navbar-btn'>Undo</button>
  <button type='button' class='btn btn-default navbar-btn'>Redo</button>
</div>
<div class='paren-soup' id='paren-soup'>
  <div class='numbers' id='numbers'></div>
  <div class='content' contenteditable='true' id='content'>%s</div>
</div>
")

(defn create-element [content]
  (let [elem (.createElement js/document "span")]
    (set! (.-innerHTML elem) (format ps-html content))
    elem))

(defn clear-editor []
  (let [editor (.querySelector js/document "#editor")]
    (set! (.-innerHTML editor) "")
    editor))

(defn init-file [path]
  (if-let [elem (get @editors path)]
    (.appendChild (clear-editor) elem)
    (.send XhrIo
      "/file"
      (fn [e]
        (if (.isSuccess (.-target e))
          (let [elem (create-element (.. e -target getResponseText))]
            (swap! editors assoc path elem)
            (.appendChild (clear-editor) elem)
            (ps/init-all))
          (clear-editor)))
      "POST"
      path)))

