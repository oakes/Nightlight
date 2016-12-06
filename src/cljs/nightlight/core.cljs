(ns nightlight.core
  (:require [cljs.reader :refer [read-string]]
            [nightlight.editors :as e]
            [nightlight.state :as s]
            [nightlight.repl :as repl]
            [nightlight.components :refer [app]]
            [reagent.core :as r])
  (:import goog.net.XhrIo))

(def ^:const version "1.2.1")
(def ^:const api-url "https://clojars.org/api/artifacts/nightlight")
(def ^:const bootstrap-themes {:dark "bootstrap-dark.min.css" :light "bootstrap-light.min.css"})

(defn init-tree [{:keys [primary-text nested-items selection options]}]
  (swap! s/runtime-state assoc :options options :title primary-text :nodes nested-items)
  (some-> (:url options) repl/init-cljs)
  (e/select-node selection))

(defn download-tree []
  (.send XhrIo
    "/tree"
    (fn [e]
      (when (.isSuccess (.-target e))
        (init-tree (read-string (.. e -target getResponseText)))))
    "GET"))

(defn download-state []
  (.send XhrIo
    "/read-state"
    (fn [e]
      (when (.isSuccess (.-target e))
        (reset! s/pref-state (read-string (.. e -target getResponseText))))
      (download-tree))
    "GET"))

(defn check-version []
  (.send XhrIo
    api-url
    (fn [e]
      (when (and (.isSuccess (.-target e))
                 (some->> (.. e -target getResponseText)
                          (.parse js/JSON)
                          (#(aget % "latest_version"))
                          (not= version)))
        (swap! s/runtime-state assoc :update? true)))
    "GET"))

(def app-with-init
  (with-meta app
    {:component-did-mount (fn [this]
                            (repl/init-cljs-client)
                            (download-state)
                            #_(check-version))}))

(r/render-component [app-with-init] (.querySelector js/document "#app"))

