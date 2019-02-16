(require
  '[orchestra.spec.test :as st]
  '[figwheel.main :as figwheel]
  '[nightlight.core :as nightlight])

(st/instrument)
(nightlight/dev-start {:port 4000 :url "http://localhost:4000" :main 'nightlight.core})
(figwheel/-main "--build" "dev")
