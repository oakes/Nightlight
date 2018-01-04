(ns nightlight.watch
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [org.httpkit.server :refer [send! with-channel on-receive on-close]]
            [hawk.core :as hawk]
            [dynadoc.watch :as dw]))

(defonce *channels (atom #{}))
(defonce *file-content (atom {}))

(defn init-watcher! []
  (when-not @dw/*cljs-info
    (dw/init-watcher!))
  (hawk/watch! [{:paths [(.getCanonicalPath (io/file "."))]
                 :handler (fn [ctx {:keys [kind file]}]
                            (when (#{:create :modify} kind)
                              (let [path (.getCanonicalPath file)]
                                (when (and (.isFile file)
                                           (not= (@*file-content path)
                                                 (slurp file)))
                                  (doseq [channel @*channels]
                                    (send! channel path)))))
                            ctx)}]))

(defn watch-request [request]
  (with-channel request channel
    (on-close channel
      (fn [status]
        (swap! *channels disj channel)))
    (on-receive channel
      (fn [_]
        (swap! *channels conj channel)))))

