(ns nightlight.watch
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint]
            [org.httpkit.server :refer [send! with-channel on-receive on-close]]
            [hawk.core :as hawk]
            [dynadoc.static :as dyn]))

(defonce channels (atom #{}))
(defonce file-content (atom {}))
(defonce cljs-info (atom nil))

(defn init-cljs-info []
  (when-not @cljs-info
    (reset! cljs-info (dyn/get-cljs-nses-and-vars))))

(defonce watcher (hawk/watch! [{:paths [(.getCanonicalPath (io/file "."))]
                                :handler (fn [ctx {:keys [kind file]}]
                                           (when (#{:create :modify} kind)
                                             (let [path (.getCanonicalPath file)]
                                               (when (and (.isFile file)
                                                          (not= (@file-content path)
                                                                (slurp file)))
                                                 (doseq [channel @channels]
                                                   (send! channel path))))
                                             (when (str/ends-with? (.getName file) ".cljs")
                                               (try
                                                 (swap! cljs-info #(dyn/read-cljs-file % file))
                                                 (catch Exception _))))
                                           ctx)}]))

(defn watch-request [request]
  (with-channel request channel
    (on-close channel
      (fn [status]
        (swap! channels disj channel)))
    (on-receive channel
      (fn [_]
        (swap! channels conj channel)))))

