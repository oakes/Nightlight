(require
  '[clojure.java.io :as io]
  '[clojure.string :as str]
  '[cljs.build.api :as api]
  '[leiningen.core.project :as p :refer [defproject]]
  '[leiningen.install :refer [install]]
  '[leiningen.deploy :refer [deploy]])

(defn read-project-clj []
  (p/ensure-dynamic-classloader)
  (-> "project.clj" load-file var-get))

(defn read-deps-edn [aliases-to-include]
  (let [{:keys [paths deps aliases]} (-> "deps.edn" slurp clojure.edn/read-string)
        deps (->> (select-keys aliases aliases-to-include)
                  vals
                  (mapcat :extra-deps)
                  (into deps)
                  (map (fn parse-coord [coord]
                         (let [[artifact info] coord
                               s (str artifact)]
                           (if-let [i (str/index-of s "$")]
                             [(symbol (subs s 0 i))
                              (assoc info :classifier (subs s (inc i)))]
                             coord))))
                  (reduce
                    (fn [deps [artifact info]]
                      (if-let [version (:mvn/version info)]
                        (conj deps
                          (transduce cat conj [artifact version]
                            (select-keys info [:exclusions :classifier])))
                        deps))
                    []))
        paths (->> (select-keys aliases aliases-to-include)
                   vals
                   (mapcat :extra-paths)
                   (into paths))]
    {:dependencies deps
     :source-paths []
     :resource-paths paths}))

(defn delete-children-recursively! [f]
  (when (.isDirectory f)
    (doseq [f2 (.listFiles f)]
      (delete-children-recursively! f2)))
  (when (.exists f) (io/delete-file f)))

(defn build-cljs [file-name opts]
  (let [out-file (str "resources/nightlight-public/" file-name ".js")
        out-dir (str "resources/nightlight-public/" file-name ".out")]
    (println "Building" out-file)
    (delete-children-recursively! (io/file out-dir))
    (api/build "src" (merge
                       {:output-to     out-file
                        :output-dir    out-dir}
                       opts))
    (delete-children-recursively! (io/file out-dir))))

(defmulti task first)

(defmethod task :default
  [_]
  (let [all-tasks  (-> task methods (dissoc :default) keys sort)
        interposed (->> all-tasks (interpose ", ") (apply str))]
    (println "Unknown or missing task. Choose one of:" interposed)
    (System/exit 1)))

(defmethod task "build"
  [_]
  (-> (read-project-clj) p/init-project)
  (build-cljs "main"
              {:main          'nightlight.core
               :optimizations :advanced}))

(defmethod task "install"
  [_]
  (task ["build"])
  (-> (read-project-clj)
      (merge (read-deps-edn []))
      p/init-project
      install)
  (System/exit 0))

(defmethod task "deploy"
  [_]
  (task ["build"])
  (-> (read-project-clj)
      (merge (read-deps-edn []))
      p/init-project
      (deploy "clojars"))
  (System/exit 0))

;; entry point

(task *command-line-args*)

