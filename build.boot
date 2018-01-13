(set-env!
  :dependencies '[[adzerk/boot-cljs "2.1.4" :scope "test"]
                  [adzerk/boot-reload "0.5.2" :scope "test"]
                  [seancorfield/boot-tools-deps "0.1.4" :scope "test"]
                  [javax.xml.bind/jaxb-api "2.3.0"] ; necessary for Java 9 compatibility
                  [orchestra "2017.11.12-1" :scope "test"]]
  :repositories (conj (get-env :repositories)
                  ["clojars" {:url "https://clojars.org/repo/"
                              :username (System/getenv "CLOJARS_USER")
                              :password (System/getenv "CLOJARS_PASS")}]))

(require
  '[orchestra.spec.test :refer [instrument]]
  '[clojure.edn :as edn]
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  '[boot-tools-deps.core :refer [deps]])

(task-options!
  pom {:project 'nightlight
       :version "2.1.2-SNAPSHOT"
       :description "An embedded Clojure editor"
       :url "https://github.com/oakes/Nightlight"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}
       :dependencies (->> "deps.edn"
                          slurp
                          edn/read-string
                          :deps
                          (reduce
                            (fn [deps [artifact info]]
                              (if-let [version (:mvn/version info)]
                                (conj deps
                                  (transduce cat conj [artifact version]
                                    (select-keys info [:scope :exclusions])))
                                deps))
                            []))}
  push {:repo "clojars"}
  sift {:include #{#"nightlight-public/main.out"}
        :invert true})

(deftask local []
  (set-env! :resource-paths #{"src/clj" "src/cljs" "resources" "prod-resources"})
  (comp (deps :aliases [:cljs]) (cljs :optimizations :advanced) (sift) (pom) (jar) (install)))

(deftask deploy []
  (set-env! :resource-paths #{"src/clj" "src/cljs" "resources" "prod-resources"})
  (comp (deps :aliases [:cljs]) (cljs :optimizations :advanced) (sift) (pom) (jar) (push)))

(deftask run []
  (set-env! :resource-paths #{"src/clj" "src/cljs" "resources" "dev-resources"})
  (comp
    (deps :aliases [:cljs])
    (watch)
    (reload :asset-path "nightlight-public")
    (cljs :source-map true :optimizations :none :compiler-options {:asset-path "main.out"})
    (with-pass-thru _
      (require '[nightlight.core :refer [dev-start]])
      (instrument)
      ((resolve 'dev-start) {:port 4000 :url "http://localhost:4000"}))
    (target)))

