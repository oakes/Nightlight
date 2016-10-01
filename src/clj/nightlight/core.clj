(ns nightlight.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.util.response :refer [redirect]]
            [ring.util.request :refer [body-string]]
            [clojure.spec :as s :refer [fdef]]
            [eval-soup.core :as es])
  (:import [java.io File FilenameFilter]))

(defonce web-server (atom nil))

(defn form->serializable [form]
  (if (instance? Exception form)
    [(.getMessage form)]
    (pr-str form)))

(defn file-node [^File file]
  (let [children (->> (reify FilenameFilter
                        (accept [this dir filename]
                          (not (.startsWith filename "."))))
                      (.listFiles file)
                      (mapv file-node))
        node {:text (.getName file)
              :path (.getCanonicalPath file)
              :file (.isFile file)}]
    (if (seq children)
      (assoc node :nodes children)
      node)))

(defn handler [request]
  (case (:uri request)
    "/" (redirect "/index.html")
    "/eval" {:status 200
             :headers {"Content-Type" "text/plain"}
             :body (->> request
                        body-string
                        edn/read-string
                        es/code->results
                        (mapv form->serializable)
                        pr-str)}
    "/tree" {:status 200
             :headers {"Content-Type" "text/plain"}
             :body (-> (io/file ".") file-node :nodes (or []) pr-str)}
    "/file" {:status 200
             :headers {"Content-Type" "text/plain"}
             :body (-> request body-string slurp)}
    nil))

(defn start
  ([opts]
   (start (wrap-resource handler "public") opts))
  ([app opts]
   (->> (merge {:port 0 :join? false} opts)
        (run-jetty (wrap-content-type app))
        (reset! web-server))))

(defn dev-start [opts]
  (when-not @web-server
    (.mkdirs (io/file "target" "public"))
    (start (wrap-file handler "target/public") opts)))

