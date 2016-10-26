(ns nightlight.repl
  (:require [paren-soup.core :as ps]
            [cljs.reader :refer [read-string]])
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
  (reify ReplSender
    (init [this]
      (ps/append-text! @editor-atom "=> "))
    (send [this text]
      (ps/eval! @editor-atom text
        (fn [result]
          (let [result (if (array? result)
                         (str "Error: " (aget result 0))
                         result)]
            (ps/append-text! @editor-atom (str result "\n"))
            (ps/append-text! @editor-atom "=> ")
            (let [content (.querySelector elem "#content")]
              (set! (.-scrollTop content) (.-scrollHeight content)))))))))

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
  (cb forms))

(defn create-compiler-fn [path]
  (if (= path cljs-repl-path) compile-cljs compile-clj))

