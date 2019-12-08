(defproject nightlight/lein-nightlight "2.4.3"
  :description "A convenient Nightlight launcher for Leiningen projects"
  :url "https://github.com/oakes/Nightlight"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :dependencies [[nightlight "2.4.3" :exclusions [org.clojure/core.async]]
                 [leinjacker "0.4.3"]
                 [org.clojure/tools.cli "0.3.5"]]
  :repositories [["clojars" {:url "https://clojars.org/repo"
                             :sign-releases false}]]
  :eval-in-leiningen true)

