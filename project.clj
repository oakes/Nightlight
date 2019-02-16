(defproject nightlight "2.4.2-SNAPSHOT"
  :description "An embedded Clojure editor"
  :url "https://github.com/oakes/Nightlight"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :sign-releases false}]]
  :profiles {:dev {:main nightlight.core}})
