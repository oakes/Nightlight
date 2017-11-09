(ns nightlight.watch
  (:require [clojure.java.io :as io]
            [org.httpkit.server :refer [send! with-channel on-receive on-close]]
            [hawk.core :as hawk]))

(defonce channels (atom #{}))

(defonce file-content (atom {}))

(defonce watcher (hawk/watch! [{:paths [(.getCanonicalPath (io/file "."))]
                                :handler (fn [ctx {:keys [file]}]
                                           (let [path (.getCanonicalPath file)]
                                             (when (and (.isFile file)
                                                        (not= (@file-content path)
                                                              (slurp file)))
                                               (doseq [channel @channels]
                                                 (send! channel path)))))}]))

(defn watch-request [request]
  (with-channel request channel
    (on-close channel
      (fn [status]
        (swap! channels disj channel)))
    (on-receive channel
      (fn [_]
        (swap! channels conj channel)))))

