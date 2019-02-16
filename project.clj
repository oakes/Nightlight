(defproject nightlight "2.4.1-SNAPSHOT"
  :description "An embedded Clojure editor"
  :url "https://github.com/oakes/Nightlight"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :plugins [[lein-tools-deps "0.4.3"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :sign-releases false}]]
  :profiles {:dev {:main nightlight.core}})
