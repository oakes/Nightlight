(ns nightlight.constants
  (:refer-clojure :exclude [eval]))

(def ^:const api-url "https://clojars.org/api/artifacts/nightlight")
(def ^:const page-url "https://clojars.org/nightlight")

(def ^:const clojure-exts #{"boot" "clj" "cljc" "cljs" "cljx" "edn" "pxi" "hl"})
(def ^:const completion-exts #{"clj" "cljc" "cljs"})
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

(def ^:const ps-html "
  <div class='paren-soup' id='paren-soup'>
    <div class='instarepl' id='instarepl'></div>
    <div class='numbers' id='numbers'></div>
    <div class='content' id='content' contenteditable=%s></div>
  </div>
")

(def ^:const ps-repl-html "
  <div class='paren-soup' id='paren-soup'>
    <div class='content' id='content' contenteditable=%s></div>
  </div>
")

(def ^:const repl-path "*REPL*")
(def ^:const cljs-repl-path "*CLJS-REPL*")
(def ^:const repl-path? #{repl-path cljs-repl-path})
(def ^:const control-panel-path "*CONTROL-PANEL*")

(defprotocol Editor
  (get-path [this])
  (get-extension [this])
  (get-element [this])
  (get-content [this])
  (get-object [this])
  (get-focused-text [this])
  (get-completions [this])
  (can-undo? [this])
  (can-redo? [this])
  (undo [this])
  (redo [this])
  (update-content [this])
  (mark-clean [this])
  (clean? [this])
  (init [this])
  (set-theme [this theme])
  (hide [this])
  (show [this])
  (eval-selection [this])
  (eval [this code]))

