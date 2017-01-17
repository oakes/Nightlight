(defproject nightlight/lein-nightlight "1.4.5"
  :description "A conveninent Nightlight launcher for Leiningen projects"
  :url "https://github.com/oakes/Nightlight"
  :license {:name "Public Domain"
            :url "http://unlicense.org/UNLICENSE"}
  :dependencies [[nightlight "1.4.5" :exclusions [org.clojure/core.async]]
                 [leinjacker "0.4.2"]
                 [org.clojure/tools.cli "0.3.5"]]
  :eval-in-leiningen true)

