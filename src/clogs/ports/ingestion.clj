(ns clogs.ports.ingestion
  "HTTP ingestion port for receiving log entries"
  (:require [schema.core :as s]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clogs.query.engine :as engine]
            [clogs.query.domain :as domain]))

(defn parse-body
  "Parses request body based on content type"
  [content-type body]
  (try
    (case content-type
      "application/json" (json/read-str body :key-fn keyword)
      "application/edn"  (edn/read-string body)
      nil)
    (catch Exception _
      nil)))

(defn validate-log-entry
  "Validates a log entry using domain schema"
  [entry]
  (try
    (s/validate domain/LogEntry entry)
    {:valid? true}
    (catch Exception e
      {:valid? false
       :error (str "Invalid log entry: " (.getMessage e))})))

(def logs-handler
  "Handler interceptor for POST /logs endpoint"
  {:name ::logs-handler
   :enter
   (fn [context]
     (let [request      (:request context)
           database     (:database context)
           content-type (or (:content-type request)
                            (get-in request [:headers "content-type"]))
           body-str     (if-let [body (:body request)]
                          (slurp body)
                          "")
           parsed-entry (parse-body content-type body-str)]

       (cond
         (nil? parsed-entry)
         (assoc context :response
                {:status 400
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str {:error "Invalid request body or unsupported content type"})})

         :else
         (let [validation (validate-log-entry parsed-entry)]
           (if (:valid? validation)
             (let [result (engine/store-entry database parsed-entry)]
               (if (:success? result)
                 (assoc context :response
                        {:status 201
                         :headers {"Content-Type" "application/json"}
                         :body (json/write-str {:message "Log entry stored successfully"})})
                 (assoc context :response
                        {:status 500
                         :headers {"Content-Type" "application/json"}
                         :body (json/write-str {:error (:error result)})})))
             (assoc context :response
                    {:status 400
                     :headers {"Content-Type" "application/json"}
                     :body (json/write-str {:error (:error validation)})}))))))})

(defn database-interceptor
  "Interceptor that injects database into context"
  [database]
  {:name ::database-interceptor
   :enter (fn [context]
            (assoc context :database database))})

(defn create-routes
  "Creates routes for the ingestion port"
  [database]
  #{["/logs" :post [(database-interceptor database) logs-handler] :route-name :logs]})