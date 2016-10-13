(ns nightlight.clojurescript-compiler
  (:require [eval-soup.core :as es]))

(defn form->serializable [form]
  (if (instance? js/Error form)
    (array (or (some-> form .-cause .-message) (.-message form))
      (.-fileName form) (.-lineNumber form))
    (pr-str form)))

(set! (.-onmessage js/self)
      (fn [e]
        (let [forms (.-data e)]
          (es/code->results
            forms
            (fn [results]
              (.postMessage js/self (into-array (mapv form->serializable results))))))))

