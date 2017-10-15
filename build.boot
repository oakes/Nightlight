(set-env!
  :source-paths #{"src/clj" "src/cljs"}
  :dependencies '[[adzerk/boot-cljs "2.1.4" :scope "test"]
                  [adzerk/boot-reload "0.5.2" :scope "test"]
                  [org.clojure/test.check "0.9.0" :scope "test"]
                  ; cljs deps
                  [org.clojure/clojurescript "1.9.946" :scope "test"]
                  [paren-soup "2.9.0" :scope "test"]
                  [mistakes-were-made "1.7.3" :scope "test"]
                  [cljsjs/codemirror "5.24.0-1" :scope "test"]
                  [reagent "0.7.0" :exclusions [cljsjs/react cljsjs/react-dom] :scope "test"]
                  [cljs-react-material-ui "0.2.34" :scope "test"]
                  ; clj deps
                  [org.clojure/clojure "1.8.0" :scope "provided"]
                  [ring "1.5.1"]
                  [ring-basic-authentication "1.0.5"]
                  [http-kit "2.2.0"]
                  [javax.xml.bind/jaxb-api "2.3.0"] ; necessary for Java 9 compatibility
                  [compliment "0.3.1"]
                  [eval-soup "1.2.2"]]
  :repositories (conj (get-env :repositories)
                  ["clojars" {:url "https://clojars.org/repo/"
                              :username (System/getenv "CLOJARS_USER")
                              :password (System/getenv "CLOJARS_PASS")}]))

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  '[nightlight.core :refer [dev-start]]
  '[clojure.spec.test.alpha :refer [instrument]])

(task-options!
  pom {:project 'nightlight
       :version "1.9.3-SNAPSHOT"
       :description "An embedded Clojure editor"
       :url "https://github.com/oakes/Nightlight"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}}
  push {:repo "clojars"}
  sift {:include #{#"nightlight-public/main.out"}
        :invert true})

(deftask local []
  (set-env! :resource-paths #{"src/clj" "src/cljs" "resources" "prod-resources"})
  (comp (cljs :optimizations :advanced) (sift) (pom) (jar) (install)))

(deftask deploy []
  (set-env! :resource-paths #{"src/clj" "src/cljs" "resources" "prod-resources"})
  (comp (cljs :optimizations :advanced) (sift) (pom) (jar) (push)))

(deftask run []
  (set-env! :resource-paths #{"src/clj" "src/cljs" "resources" "dev-resources"})
  (comp
    (watch)
    (reload :asset-path "nightlight-public")
    (cljs :source-map true :optimizations :none :compiler-options {:asset-path "main.out"})
    (with-pass-thru _
      (instrument)
      (dev-start {:port 4000 :url "http://localhost:4000"}))
    (target)))

