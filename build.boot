(set-env!
  :source-paths #{"src/clj" "src/cljs"}
  :resource-paths #{"src/clj" "resources"}
  :dependencies '[[adzerk/boot-cljs "1.7.228-1" :scope "test"]
                  [adzerk/boot-reload "0.4.12" :scope "test"]
                  [org.clojure/test.check "0.9.0" :scope "test"]
                  [org.clojure/clojure "1.9.0-alpha13"]
                  [org.clojure/clojurescript "1.9.227"]
                  [ring "1.4.0"]
                  [compliment "0.3.1"]
                  [eval-soup "1.0.0"]
                  [paren-soup "2.6.3"]
                  [cljsjs/bootstrap "3.3.6-1"]
                  [cljsjs/bootstrap-toggle "2.2.2-0"]
                  [cljsjs/bootstrap-treeview "1.2.0-1"]
                  [cljsjs/codemirror "5.19.0-0"]]
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
  (comp (cljs :optimizations :advanced) (pom) (jar) (install)))

(deftask deploy []
  (comp (cljs :optimizations :advanced) (pom) (jar) (push)))

(deftask run []
  (comp
    (watch)
    (reload :asset-path "nightlight-public")
    (cljs :source-map true :optimizations :none)
    (target)
    (with-pre-wrap fileset
      (require
        '[clojure.spec.test :refer [instrument]]
        '[nightlight.core :refer [dev-start]])
      ((resolve 'instrument))
      ((resolve 'dev-start) {:port 3000})
      fileset)))

