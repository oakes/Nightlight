(ns nightlight.repl
  (:require [cljs.reader :refer [read-string]]
            [nightlight.constants :as c]
            [nightlight.state :as s]
            [nightlight.repl-server :as rs])
  (:import goog.net.XhrIo))

(defn init-cljs [url]
  (when (= js/window.self js/window.top)
    (let [iframe (.querySelector js/document "#cljsapp")]
      (set! (.-src iframe) url))))

(defn init-cljs-client []
  (when (= js/window.self js/window.top)
    (set! (.-onmessage js/window)
      (fn [e]
        (let [data (.-data e)
              callback (get-in @s/runtime-state [:callbacks (aget data "type")])]
          (callback (aget data "results") (aget data "ns")))))))

(defn scroll-to-bottom [elem]
  (when-let [ps (.querySelector elem "#paren-soup")]
    (set! (.-scrollTop ps) (.-scrollHeight ps))))

(defprotocol ReplSender
  (init [this])
  (send [this text]))

(defn clj-sender [text-atom]
  (let [protocol (if (= (.-protocol js/location) "https:") "wss:" "ws:")
        host (-> js/window .-location .-host)
        sock (js/WebSocket. (str protocol "//" host "/repl"))]
    (reify ReplSender
      (init [this]
        (set! (.-onopen sock)
          (fn [event]
            (.send sock "")))
        (set! (.-onmessage sock)
          (fn [event]
            (swap! text-atom str (.-data event)))))
      (send [this text]
        (.send sock (str text "\n"))))))

(defn cljs-sender [text-atom]
  (swap! s/runtime-state update :callbacks assoc "repl"
    (fn [results current-ns]
      (let [result (aget results 0)
            result (if (array? result)
                     (str "Error: " (aget result 0))
                     result)]
        (swap! text-atom str result "\n" current-ns "=> "))))
  (let [iframe (.querySelector js/document "#cljsapp")]
    (reify ReplSender
      (init [this]
        (swap! text-atom str rs/cljs-start-ns "=> "))
      (send [this text]
        (.postMessage (.-contentWindow iframe)
          (clj->js {:type "repl" :forms (array text)})
          "*")))))

(defn create-repl-sender [path text-atom]
  (if (= path c/cljs-repl-path)
    (cljs-sender text-atom)
    (clj-sender text-atom)))

(defn compile-clj [forms cb]
  (try
    (.send XhrIo
      "eval"
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

