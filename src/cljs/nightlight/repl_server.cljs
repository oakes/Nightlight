(ns nightlight.repl-server
  (:require [eval-soup.core :as es]))

(def ^:const cljs-start-ns 'cljs.user)

(defn form->serializable [form]
  (if (instance? js/Error form)
    (array (or (some-> form .-cause .-message) (.-message form))
      (.-fileName form) (.-lineNumber form))
    (pr-str form)))

(defn init-cljs-server []
  (when (not= js/window.self js/window.top)
    (let [*current-ns (atom cljs-start-ns)]
      (set! (.-onmessage js/window)
        (fn [e]
          (es/code->results
            (.-forms (.-data e))
            (fn [results]
              (.postMessage (.-parent js/window)
                (clj->js {:type (.-type (.-data e))
                          :results (into-array (mapv form->serializable results))
                          :ns (str @*current-ns)})
                "*"))
            {:*current-ns *current-ns
             :custom-load (fn [opts cb]
                            (cb {:lang :clj :source ""}))})))
      (.postMessage (.-parent js/window)
        (clj->js {:type "repl"
                  :results (array "")
                  :ns (str @*current-ns)})
        "*"))))

(init-cljs-server)

