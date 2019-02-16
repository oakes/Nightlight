(require
  '[orchestra.spec.test :as st]
  '[expound.alpha :as expound]
  '[clojure.spec.alpha :as s]
  '[figwheel.main :as figwheel]
  '[nightlight.core :as nightlight])

(st/instrument)
(alter-var-root #'s/*explain-out* (constantly expound/printer))
(nightlight/dev-start {:port 4000 :url "http://localhost:4000" :main 'nightlight.core})
(figwheel/-main "--build" "dev")
