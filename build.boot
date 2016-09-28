(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :dependencies '[[org.clojure/clojure "1.9.0-alpha13"]
                  [ring "1.4.0"]
                  [eval-soup "1.0.0"]]
  :repositories (conj (get-env :repositories)
                  ["clojars" {:url "https://clojars.org/repo/"
                              :username (System/getenv "CLOJARS_USER")
                              :password (System/getenv "CLOJARS_PASS")}]))

(task-options!
  pom {:project 'nightlight
       :version "1.0.0-SNAPSHOT"
       :description "An embedded Clojure IDE"
       :url "https://github.com/oakes/Nightlight"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}}
  push {:repo "clojars"})

(deftask try []
  (comp (pom) (jar) (install)))

(deftask deploy []
  (comp (pom) (jar) (push)))

(deftask run []
  (comp
    (with-pre-wrap fileset
      (require
        '[clojure.spec.test :refer [instrument]]
        '[net.sekao.nightlight.core :refer [start]])
      ((resolve 'instrument))
      ((resolve 'start))
      fileset)))

