(set-env!
  :source-paths #{"src/clj" "src/cljs"}
  :resource-paths #{"resources"}
  :dependencies '[[adzerk/boot-cljs "1.7.228-1" :scope "test"]
                  [adzerk/boot-reload "0.4.12" :scope "test"]
                  [org.clojure/clojure "1.9.0-alpha13"]
                  [org.clojure/clojurescript "1.9.227"]
                  [ring "1.4.0"]
                  [eval-soup "1.0.0"]]
  :repositories (conj (get-env :repositories)
                  ["clojars" {:url "https://clojars.org/repo/"
                              :username (System/getenv "CLOJARS_USER")
                              :password (System/getenv "CLOJARS_PASS")}]))

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]])

(task-options!
  pom {:project 'nightlight
       :version "1.0.0-SNAPSHOT"
       :description "An embedded Clojure IDE"
       :url "https://github.com/oakes/Nightlight"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}}
  push {:repo "clojars"})

(deftask local []
  (comp (cljs :optimizations :simple) (pom) (jar) (install)))

(deftask deploy []
  (comp (cljs :optimizations :simple) (pom) (jar) (push)))

(deftask run []
  (comp
    (watch)
    (reload :asset-path "public")
    (cljs :source-map true :optimizations :none)
    (target)
    (with-pre-wrap fileset
      (require
        '[clojure.spec.test :refer [instrument]]
        '[nightlight.core :refer [dev-start]])
      ((resolve 'instrument))
      ((resolve 'dev-start) {:port 3000})
      fileset)))

