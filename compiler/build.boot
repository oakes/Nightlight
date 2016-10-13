(set-env!
  :source-paths #{"src"}
  :dependencies '[[adzerk/boot-cljs "1.7.228-1" :scope "test"]
                  ; project deps
                  [org.clojure/clojurescript "1.9.225"]
                  [eval-soup "1.0.2"]])

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[clojure.java.io :as io])

(deftask build []
  (set-env! :source-paths #{"src"})
  (comp
    (cljs)
    (target)
    (with-pass-thru _
      (let [from-clj (io/file "target/nightlight/clojure-compiler.js")
            to-clj (io/file "../resources/nightlight-public/clojure-compiler.js")
            from-cljs (io/file "target/nightlight/clojurescript-compiler.js")
            to-cljs (io/file "../resources/nightlight-public/clojurescript-compiler.js")]
        (.renameTo from-clj to-clj)
        (.renameTo from-cljs to-cljs)))))

