(defproject nightlight/lein-nightlight "1.2.0"
  :description "A conveninent Nightlight launcher for Leiningen projects"
  :url "https://github.com/oakes/Nightlight"
  :license {:name "Public domain"
            :url "http://unlicense.org/UNLICENSE"}
  :dependencies [[nightlight "1.2.0" :exclusions [org.clojure/core.async]]
                 [leinjacker "0.4.2"]
                 [org.clojure/tools.cli "0.3.5"]]
  :eval-in-leiningen true)

