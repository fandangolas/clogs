(ns clogs.ports.health
  "Health check port for service monitoring"
  (:require [clojure.data.json :as json]))

(def health-handler
  "Handler for health check endpoint"
  {:name ::health-handler
   :enter
   (fn [context]
     (assoc context :response
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body (json/write-str {:status "ok"
                                   :service "clogs"
                                   :timestamp (.toString (java.time.Instant/now))})}))})

(defn create-routes
  "Creates routes for the health port"
  []
  #{["/health" :get [health-handler] :route-name :health]})