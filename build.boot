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
     :source-paths (set paths)
     :resource-paths (set paths)}))

(let [{:keys [source-paths resource-paths dependencies]} (read-deps-edn [])]
  (set-env!
    :source-paths source-paths
    :resource-paths resource-paths
    :dependencies (into '[[adzerk/boot-cljs "2.1.4" :scope "test"]
                          [adzerk/boot-reload "0.5.2" :scope "test"]
                          [javax.xml.bind/jaxb-api "2.3.0" :scope "test"] ; necessary for Java 9 compatibility
                          [orchestra "2017.11.12-1" :scope "test"]]
                        dependencies)
    :repositories (conj (get-env :repositories)
                    ["clojars" {:url "https://clojars.org/repo/"
                                :username (System/getenv "CLOJARS_USER")
                                :password (System/getenv "CLOJARS_PASS")}])))

(require
  '[orchestra.spec.test :refer [instrument]]
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]])

(task-options!
  pom {:project 'nightlight
       :version "2.1.5"
       :description "An embedded Clojure editor"
       :url "https://github.com/oakes/Nightlight"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}}
  push {:repo "clojars"}
  sift {:include #{#"nightlight-public/main.out"}
        :invert true})

(deftask local []
  (set-env!
    :dependencies #(into (set %) (:dependencies (read-deps-edn [:cljs])))
    :resource-paths #(conj % "prod-resources"))
  (comp (cljs :optimizations :advanced) (sift) (pom) (jar) (install)))

(deftask deploy []
  (set-env!
    :dependencies #(into (set %) (:dependencies (read-deps-edn [:cljs])))
    :resource-paths #(conj % "prod-resources"))
  (comp (cljs :optimizations :advanced) (sift) (pom) (jar) (push)))

(deftask run []
  (set-env!
    :dependencies #(into (set %) (:dependencies (read-deps-edn [:cljs])))
    :resource-paths #(conj % "dev-resources"))
  (comp
    (watch)
    (reload :asset-path "nightlight-public")
    (cljs :source-map true :optimizations :none :compiler-options {:asset-path "main.out"})
    (with-pass-thru _
      (require '[nightlight.core :refer [dev-start]])
      (instrument)
      ((resolve 'dev-start) {:port 4000 :url "http://localhost:4000"}))
    (target)))

