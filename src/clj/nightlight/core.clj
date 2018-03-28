(ns nightlight.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.util.response :refer [redirect]]
            [ring.util.request :refer [body-string]]
            [eval-soup.core :as es]
            [compliment.core :as com]
            [nightlight.repl :as repl]
            [nightlight.watch :as watch]
            [nightlight.utils :as u]
            [org.httpkit.server :refer [run-server]]
            [clojure.tools.cli :as cli]
            [dynadoc.watch :as dw])
  (:import [java.io File FilenameFilter]))

(def ^:const max-file-size (* 1024 1024 2))
(def ^:const pref-file ".nightlight.edn")

(defonce *web-server (atom nil))
(defonce *options (atom nil))

(defn read-state []
  (try (edn/read-string (slurp pref-file))
    (catch Exception _ {:auto-save? true :theme :dark})))

(defn file-node
  ([^File file]
   (let [pref-state (read-state)]
     (-> (file-node file pref-state)
         (assoc :selection (:selection pref-state))
         (assoc :options @*options))))
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

(defn handler [request]
  (case (:uri request)
    "/" {:status 200
         :headers {"Content-Type" "text/html"}
         :body (-> "nightlight-public/index.html" io/resource slurp)}
    "/eval" {:status 200
             :headers {"Content-Type" "text/plain"}
             :body (-> request
                       body-string
                       edn/read-string
                       (es/code->results {:disable-timeout? true})
                       ((partial mapv u/form->serializable))
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
                    (swap! watch/*file-content assoc path content)
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
                     (u/delete-parents-recursively! (io/file ".") from-file)
                     {:status 200
                      :headers {"Content-Type" "text/plain"}
                      :body (.getCanonicalPath to-file)})
    "/delete-file" (let [file (-> request body-string io/file)]
                     (u/delete-parents-recursively! (io/file ".") file)
                     {:status 200})
    "/read-state" {:status 200
                   :headers {"Content-Type" "text/plain"}
                   :body (pr-str (read-state))}
    "/write-state" {:status 200
                    :headers {"Content-Type" "text/plain"}
                    :body (spit pref-file (body-string request))}
    "/completions" {:status 200
                    :headers {"Content-Type" "text/plain"}
                    :body (let [{:keys [ext ns context-before context-after prefix text]}
                                (->> request body-string edn/read-string)]
                            (case ext
                              ("clj" "cljc")
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
                                (catch Exception _ "[]"))
                              "cljs"
                              (->> (concat
                                     (vals (get @dw/*cljs-info 'cljs.core))
                                     (vals (get @dw/*cljs-info ns)))
                                   (filter #(-> % :sym str
                                                (str/starts-with? prefix)))
                                   (map (fn [{:keys [sym]}]
                                          (let [s (str sym)]
                                            {:primary-text s
                                             :value s})))
                                   (filter #(not= text (:primary-text %)))
                                   set
                                   (sort-by :sym)
                                   (take 50)
                                   vec
                                   pr-str)))}
    "/repl" (repl/repl-request request)
    "/watch" (watch/watch-request request)
    nil))

(defn print-server [server]
  (println
    (str "Started Nightlight on http://localhost:"
      (-> server meta :local-port)))
  server)

(defn wrap-auth [app opts]
  (if-let [users (or (:users opts) (:user opts))]
    (wrap-basic-authentication app
      (fn [uname pass]
        (= (get users uname) pass)))
    app))

(defn start
  ([opts]
   (start (wrap-resource handler "nightlight-public") opts))
  ([app opts]
   (watch/init-watcher!)
   (when-not @*web-server
     (->> (merge {:port 0} opts)
          (reset! *options)
          (run-server (wrap-auth app opts))
          (reset! *web-server)
          print-server))))

(defn dev-start [opts]
  (when-not @*web-server
    (.mkdirs (io/file "target" "nightlight-public"))
    (start (wrap-file handler "target/nightlight-public") opts)))

(defn -main [& args]
  (let [cli (cli/parse-opts args u/cli-options)]
    (cond
      ;; if there are CLI errors, print error messages and usage summary
      (:errors cli)
      (do
        (println (:errors cli) "\n" (:summary cli))
        (System/exit 0))
      ;; if user asked for CLI usage, print the usage summary
      (get-in cli [:options :usage])
      (do
        (println (:summary cli))
        (System/exit 0))
      ;; in other cases start Nightlight
      :otherwise
      (start (:options cli)))))

