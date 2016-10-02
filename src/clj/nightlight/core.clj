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

(def ^:const max-file-size (* 1024 1024 2))
(def ^:const pref-file ".nightlight.edn")

(defonce web-server (atom nil))

(defn form->serializable [form]
  (if (instance? Exception form)
    [(.getMessage form)]
    (pr-str form)))

(defn file-node
  ([^File file]
   (let [pref-state (edn/read-string (slurp pref-file))
         selection (:selection pref-state)]
     (assoc (file-node file pref-state)
       :path selection
       :file? (some-> selection io/file .isFile))))
  ([^File file {:keys [expansions selection] :as pref-state}]
   (let [path (.getCanonicalPath file)
         children (->> (reify FilenameFilter
                         (accept [this dir filename]
                           (not (.startsWith filename "."))))
                       (.listFiles file)
                       (mapv #(file-node % pref-state)))
         node {:text (.getName file)
               :path path
               :file (.isFile file)
               :state {:expanded (contains? expansions path)
                       :selected (= selection path)}}]
     (if (seq children)
       (assoc node :nodes children)
       node))))

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
             :body (pr-str (file-node (io/file ".")))}
    "/read-file" (let [path (body-string request)]
                   (if (-> path io/file .length (< max-file-size))
                     {:status 200
                      :headers {"Content-Type" "text/plain"}
                      :body (slurp path)}
                     {:status 400
                      :headers {}
                      :body "File too large."}))
    "/write-file" (when-let [{:keys [path content]} (-> request body-string edn/read-string)]
                    (spit path content))
    "/read-state" {:status 200
                   :headers {"Content-Type" "text/plain"}
                   :body (try (slurp pref-file)
                           (catch Exception _ "{}"))}
    "/write-state" {:status 200
                    :headers {"Content-Type" "text/plain"}
                    :body (spit pref-file (body-string request))}
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

