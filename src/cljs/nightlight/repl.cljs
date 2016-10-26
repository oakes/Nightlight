(ns nightlight.repl
  (:require [paren-soup.core :as ps]
            [cljs.reader :refer [read-string]]
            [eval-soup.core :as es]
            [nightlight.state :as s])
  (:import goog.net.XhrIo))

(def ^:const repl-path "*REPL*")
(def ^:const cljs-repl-path "*CLJS-REPL*")

(def repl-path? #{repl-path cljs-repl-path})

(defn repl-node [{:keys [path]}]
  {:text "Clojure REPL"
   :path repl-path
   :icon "glyphicon glyphicon-chevron-right"
   :state {:selected (= path repl-path)}})

(defn cljs-repl-node [{:keys [path]}]
  {:text "ClojureScript REPL"
   :path cljs-repl-path
   :icon "glyphicon glyphicon-chevron-right"
   :state {:selected (= path cljs-repl-path)}})

(defn init-cljs [url]
  (when (nil? (.-frameElement js/window))
    (let [iframe (.querySelector js/document "#cljsapp")]
      (set! (.-src iframe) url))))

(defn form->serializable [form]
  (if (instance? js/Error form)
    (array (or (some-> form .-cause .-message) (.-message form))
      (.-fileName form) (.-lineNumber form))
    (pr-str form)))

(defn init-cljs-server []
  (when (.-frameElement js/window)
    (set! (.-onmessage js/window)
      (fn [e]
        (es/code->results
          (.-forms (.-data e))
          (fn [results]
            (.postMessage (.-parent js/window)
              (clj->js {:type (.-type (.-data e))
                        :results (into-array (mapv form->serializable results))})
              "*")))))))

(defn init-cljs-client []
  (when (nil? (.-frameElement js/window))
    (set! (.-onmessage js/window)
      (fn [e]
        ((get-in @s/runtime-state [:callbacks (.-type (.-data e))])
         (.-results (.-data e)))))))

(defprotocol ReplSender
  (init [this])
  (send [this text]))

(defn clj-sender [elem editor-atom]
  (let [sock (js/WebSocket. (str "ws://" (-> js/window .-location .-host) "/repl"))]
    (reify ReplSender
      (init [this]
        (set! (.-onopen sock)
          (fn [event]
            (.send sock "")))
        (set! (.-onmessage sock)
          (fn [event]
            (some-> @editor-atom (ps/append-text! (.-data event)))
            (let [content (.querySelector elem "#content")]
              (set! (.-scrollTop content) (.-scrollHeight content))))))
      (send [this text]
        (.send sock (str text "\n"))))))

(defn cljs-sender [elem editor-atom]
  (swap! s/runtime-state update :callbacks assoc "repl"
    (fn [results]
      (let [result (aget results 0)
            result (if (array? result)
                     (str "Error: " (aget result 0))
                     result)]
        (ps/append-text! @editor-atom (str result "\n"))
        (ps/append-text! @editor-atom "=> ")
        (let [content (.querySelector elem "#content")]
          (set! (.-scrollTop content) (.-scrollHeight content))))))
  (let [iframe (.querySelector js/document "#cljsapp")]
    (reify ReplSender
      (init [this]
        (ps/append-text! @editor-atom "=> "))
      (send [this text]
        (.postMessage (.-contentWindow iframe)
          (clj->js {:type "repl" :forms (array text)})
          "*")))))

(defn create-repl-sender [path elem editor-atom]
  (if (= path cljs-repl-path)
    (cljs-sender elem editor-atom)
    (clj-sender elem editor-atom)))

(defn compile-clj [forms cb]
  (try
    (.send XhrIo
      "/eval"
      (fn [e]
        (if (.isSuccess (.-target e))
          (->> (.. e -target getResponseText)
               read-string
               (mapv #(if (vector? %) (into-array %) %))
               cb)
          (cb [])))
      "POST"
      (pr-str (into [] forms)))
    (catch js/Error _ (cb []))))

(defn compile-cljs [forms cb]
  (swap! s/runtime-state update :callbacks assoc "instarepl" cb)
  (let [iframe (.querySelector js/document "#cljsapp")]
    (.postMessage (.-contentWindow iframe)
      (clj->js {:type "instarepl" :forms (into-array forms)})
      "*")))

