(ns nightlight.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.util.response :refer [redirect]]
            [ring.util.request :refer [body-string]]
            [eval-soup.core :as es]
            [compliment.core :as com]
            [nightlight.repl :as repl]
            [org.httpkit.server :refer [run-server]])
  (:import [java.io File FilenameFilter]))

(def ^:const max-file-size (* 1024 1024 2))
(def ^:const pref-file ".nightlight.edn")

(defonce web-server (atom nil))
(defonce options (atom nil))

(defn form->serializable [form]
  (if (instance? Exception form)
    [(.getMessage form)]
    (pr-str form)))

(defn read-state []
  (try (edn/read-string (slurp pref-file))
    (catch Exception _ {:auto-save? true :theme :dark})))

(defn file-node
  ([^File file]
   (let [pref-state (read-state)]
     (-> (file-node file pref-state)
         (assoc :selection (:selection pref-state))
         (assoc :options @options))))
  ([^File file {:keys [expansions] :as pref-state}]
   (let [path (.getCanonicalPath file)
         children (->> (reify FilenameFilter
                         (accept [this dir filename]
                           (not (.startsWith filename "."))))
                       (.listFiles file)
                       (mapv #(file-node % pref-state)))
         node {:primary-text (.getName file)
               :value path
               :initially-open (contains? expansions path)}]
     (if (seq children)
       (assoc node :nested-items children)
       node))))

(defn delete-parents-recursively!
  "Deletes the given file along with all empty parents up to top-level-file."
  [top-level-file file]
  (when (and (zero? (count (.listFiles file)))
             (not (.equals file top-level-file)))
    (io/delete-file file true)
    (->> file
         .getParentFile
         (delete-parents-recursively! top-level-file)))
  nil)

(defn handler [request]
  (case (:uri request)
    "/" {:status 200
         :headers {"Content-Type" "text/html"}
         :body (-> "nightlight-public/index.html" io/resource slurp)}
    "/eval" {:status 200
             :headers {"Content-Type" "text/plain"}
             :body (->> request
                        body-string
                        edn/read-string
                        es/code->results
                        (mapv form->serializable)
                        pr-str)}
    "/tree" {:status 200
             :headers {"Content-Type" "text/plain"}
             :body (-> "." io/file .getCanonicalFile file-node pr-str)}
    "/read-file" (when-let [f (some-> request body-string io/file)]
                   (cond
                     (not (.isFile f))
                     {:status 400
                      :headers {}
                      :body "Not a file."}
                     (> (.length f) max-file-size)
                     {:status 400
                      :headers {}
                      :body "File too large."}
                     :else
                     {:status 200
                      :headers {"Content-Type" "text/plain"}
                      :body f}))
    "/write-file" (let [{:keys [path content]} (-> request body-string edn/read-string)]
                    (spit path content)
                    {:status 200})
    "/new-file" (let [file (->> request body-string (io/file "."))]
                  (.mkdirs (.getParentFile file))
                  (.createNewFile file)
                  (->> (assoc (read-state) :selection (.getCanonicalPath file))
                       pr-str
                       (spit pref-file))
                  {:status 200})
    "/rename-file" (let [{:keys [from to]} (-> request body-string edn/read-string)
                         from-file (io/file from)
                         to-file (io/file "." to)]
                     (.mkdirs (.getParentFile to-file))
                     (.renameTo from-file to-file)
                     (delete-parents-recursively! (io/file ".") from-file)
                     {:status 200})
    "/delete-file" (let [file (-> request body-string io/file)]
                     (delete-parents-recursively! (io/file ".") file)
                     {:status 200})
    "/read-state" {:status 200
                   :headers {"Content-Type" "text/plain"}
                   :body (pr-str (read-state))}
    "/write-state" {:status 200
                    :headers {"Content-Type" "text/plain"}
                    :body (spit pref-file (body-string request))}
    "/completions" {:status 200
                    :headers {"Content-Type" "text/plain"}
                    :body (let [{:keys [ns context-before context-after prefix text]}
                                (->> request body-string edn/read-string)]
                            (try
                              (->> {:ns ns
                                    :context (read-string (str context-before "__prefix__" context-after))}
                                   (com/completions prefix)
                                   (map (fn [{:keys [candidate]}]
                                          {:primary-text candidate
                                           :value candidate}))
                                   (filter #(not= text (:primary-text %)))
                                   (take 50)
                                   vec
                                   pr-str)
                              (catch Exception _ "[]")))}
    "/repl" (repl/repl-request request)
    nil))

(defn print-server [server]
  (println
    (str "Started Nightlight on http://localhost:"
      (-> server meta :local-port)))
  server)

(defn start
  ([opts]
   (start (wrap-resource handler "nightlight-public") opts))
  ([app opts]
   (when-not @web-server
     (->> (merge {:port 0} opts)
          (reset! options)
          (run-server app)
          (reset! web-server)
          print-server))))

(defn dev-start [opts]
  (when-not @web-server
    (.mkdirs (io/file "target" "nightlight-public"))
    (start (wrap-file handler "target/nightlight-public") opts)))

