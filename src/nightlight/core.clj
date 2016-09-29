(ns nightlight.core
  (:require [clojure.edn :as edn]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.util.response :refer [redirect]]
            [ring.util.request :refer [body-string]]
            [clojure.spec :as s :refer [fdef]]
            [eval-soup.core :as es]))

(defn form->serializable [form]
  (if (instance? Exception form)
    [(.getMessage form)]
    (pr-str form)))

(defn handler [request]
  (case (:uri request)
    "/" (redirect "/paren-soup.html")
    "/eval" {:status 200
             :headers {"Content-Type" "text/plain"}
             :body (->> request
                        body-string
                        edn/read-string
                        es/code->results
                        (mapv form->serializable)
                        pr-str)}
    nil))

(defn start [opts]
  (-> handler
      (wrap-resource "public")
      (wrap-content-type)
      (run-jetty (merge {:port 0 :join? false} opts))
      .getConnectors
      (aget 0)
      .getLocalPort))

