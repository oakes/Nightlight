(require
  '[cljs.build.api :as api]
  '[leiningen.core.project :as p :refer [defproject]]
  '[leiningen.clean :refer [clean]]
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
                  (reduce
                    (fn [deps [artifact info]]
                      (if-let [version (:mvn/version info)]
                        (conj deps
                          (transduce cat conj [artifact version]
                            (select-keys info [:scope :exclusions])))
                        deps))
                    []))]
    {:dependencies deps
     :source-paths []
     :resource-paths paths}))

(defmulti task first)

(defmethod task :default
  [_]
  (let [all-tasks  (-> task methods (dissoc :default) keys sort)
        interposed (->> all-tasks (interpose ", ") (apply str))]
    (println "Unknown or missing task. Choose one of:" interposed)
    (System/exit 1)))

(defmethod task "build"
  [_]
  (-> (read-project-clj) p/init-project clean)
  (println "Building main.js")
  (api/build "src" {:main          'nightlight.core
                    :optimizations :advanced
                    :output-to     "target-js/nightlight-public/main.js"
                    :output-dir    "target/nightlight-public/main.out"}))

(defmethod task "install"
  [_]
  (task ["build"])
  (-> (read-project-clj)
      (dissoc :middleware)
      (merge (read-deps-edn []))
      p/init-project
      install)
  (System/exit 0))

(defmethod task "deploy"
  [_]
  (task ["build"])
  (-> (read-project-clj)
      (dissoc :middleware)
      (merge (read-deps-edn []))
      p/init-project
      (deploy "clojars"))
  (System/exit 0))

;; entry point

(task *command-line-args*)

