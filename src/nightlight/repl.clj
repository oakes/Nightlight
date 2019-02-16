(ns nightlight.repl
  (:require [clojure.string :as str]
            [org.httpkit.server :refer [send! with-channel on-receive on-close]])
  (:import [clojure.lang LineNumberingPushbackReader]
           [java.io PipedWriter PipedReader PrintWriter]))

(defonce *state (atom {}))

(defn remove-returns [^String s]
  (str/escape s {\return ""}))

(defn pipe-into-console! [in-pipe channel]
  (let [ca (char-array 256)]
    (.start
      (Thread.
        (fn []
          (loop []
            (when-let [read (try (.read in-pipe ca)
                              (catch Exception _))]
              (when (pos? read)
                (let [s (remove-returns (String. ca 0 read))]
                  (send! channel s)
                  (Thread/sleep 100) ; prevent thread from being flooded
                  (recur))))))))))

(defn create-pipes []
  (let [out-pipe (PipedWriter.)
        in (LineNumberingPushbackReader. (PipedReader. out-pipe))
        pout (PipedWriter.)
        out (PrintWriter. pout)
        in-pipe (PipedReader. pout)]
    {:in in :out out :in-pipe in-pipe :out-pipe out-pipe}))

(defn start-repl-thread! [main-ns channel pipes]
  (let [{:keys [in-pipe in out]} pipes]
    (pipe-into-console! in-pipe channel)
    (.start
      (Thread.
        (fn []
          (binding [*out* out
                    *err* out
                    *in* in]
            (try
              (clojure.main/repl
                :init
                (fn []
                  (when main-ns
                    (doto main-ns require in-ns)))
                :read
                (fn [request-prompt request-exit]
                  (let [form (clojure.main/repl-read request-prompt request-exit)]
                    (if (= 'nightlight.repl/exit form) request-exit form))))
              (catch Exception e (some-> (.getMessage e) println))
              (finally (println "=== Finished ==="))))))))
  pipes)

(defn repl-request [request main-ns]
  (with-channel request channel
    (on-close channel
      (fn [status]
        (when-let [{:keys [in-pipe out-pipe]} (get @*state channel)]
          (doto out-pipe (.write "nightlight.repl/exit\n") (.flush))
          (.close in-pipe))
        (swap! *state dissoc channel)))
    (on-receive channel
      (fn [text]
        (when-not (get @*state channel)
          (->> (create-pipes)
               (start-repl-thread! main-ns channel)
               (swap! *state assoc channel)))
        (doto (get-in @*state [channel :out-pipe])
          (.write text)
          (.flush))))))

