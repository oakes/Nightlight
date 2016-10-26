(set-env!
  :source-paths #{"src/clj" "src/cljs"}
  :resource-paths #{"src/clj" "resources"}
  :dependencies '[[adzerk/boot-cljs "1.7.228-1" :scope "test"]
                  [adzerk/boot-reload "0.4.12" :scope "test"]
                  [org.clojure/test.check "0.9.0" :scope "test"]
                  ; cljs deps
                  [org.clojure/clojurescript "1.9.227" :scope "test"]
                  [paren-soup "2.6.8" :scope "test"]
                  [cljsjs/bootstrap "3.3.6-1" :scope "test"]
                  [cljsjs/bootstrap-toggle "2.2.2-1" :scope "test"]
                  [cljsjs/bootstrap-treeview "1.2.0-1" :scope "test"]
                  [cljsjs/codemirror "5.19.0-0" :scope "test"]
                  ; clj deps
                  [org.clojure/clojure "1.8.0"]
                  [ring "1.5.0"]
                  [http-kit "2.2.0"]
                  [compliment "0.3.1"]
                  [eval-soup "1.0.2" :exclusions [org.clojure/core.async]]]
  :repositories (conj (get-env :repositories)
                  ["clojars" {:url "https://clojars.org/repo/"
                              :username (System/getenv "CLOJARS_USER")
                              :password (System/getenv "CLOJARS_PASS")}]))

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  '[nightlight.core :refer [dev-start]])

(task-options!
  pom {:project 'nightlight
       :version "1.0.2-SNAPSHOT"
       :description "An embedded Clojure editor"
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
    (with-pass-thru _
      (dev-start {:port 3000}))
    (target)))

