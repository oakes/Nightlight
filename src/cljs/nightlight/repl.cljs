(ns nightlight.repl
  (:require [cljs.reader :refer [read-string]]
            [nightlight.state :as s]
            [paren-soup.core :as ps]
            [nightlight.repl-server :as rs])
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

(defn init-cljs-client []
  (when (nil? (.-frameElement js/window))
    (set! (.-onmessage js/window)
      (fn [e]
        (let [callback (get-in @s/runtime-state [:callbacks (.-type (.-data e))])]
          (callback (.-results (.-data e)) (.-ns (.-data e))))))))

(defn scroll-to-bottom [elem]
  (let [ps (.querySelector elem "#paren-soup")]
    (set! (.-scrollTop ps) (.-scrollHeight ps))))

(defprotocol ReplSender
  (init [this])
  (send [this text]))

(defn clj-sender [elem editor-atom]
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
            (some-> @editor-atom (ps/append-text! (.-data event)))
            (scroll-to-bottom elem))))
      (send [this text]
        (.send sock (str text "\n"))))))

(defn cljs-sender [elem editor-atom]
  (swap! s/runtime-state update :callbacks assoc "repl"
    (fn [results current-ns]
      (let [result (aget results 0)
            result (if (array? result)
                     (str "Error: " (aget result 0))
                     result)]
        (ps/append-text! @editor-atom (str result "\n"))
        (ps/append-text! @editor-atom (str current-ns "=> "))
        (scroll-to-bottom elem))))
  (let [iframe (.querySelector js/document "#cljsapp")]
    (reify ReplSender
      (init [this]
        (ps/append-text! @editor-atom (str rs/cljs-start-ns "=> ")))
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

