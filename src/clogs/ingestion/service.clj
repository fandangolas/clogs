(ns clogs.ingestion.service
  "HTTP ingestion service using Pedestal for receiving log entries"
  (:require [schema.core :as s]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [io.pedestal.http :as http]
            [clogs.query.engine :as engine]
            [clogs.query.domain :as domain]))

(s/defschema ServiceConfig
  "Configuration for the ingestion service"
  {:engine s/Any
   :port s/Int})

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
           engine       (:engine context)
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
             (let [result (engine/store-entry engine parsed-entry)]
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

(defn engine-interceptor
  "Interceptor that injects engine into context"
  [engine]
  {:name ::engine-interceptor
   :enter (fn [context]
            (assoc context :engine engine))})

(defn create-routes [engine]
  #{["/logs" :post [(engine-interceptor engine) logs-handler] :route-name :logs]})

(s/defn create-service :- s/Any
  "Creates a Pedestal service configuration"
  [config :- ServiceConfig]
  (-> {::http/routes (create-routes (:engine config))
       ::http/type   :jetty
       ::http/port   (:port config)
       ::http/join?  false}
      http/default-interceptors
      http/dev-interceptors))

(s/defn create-servlet :- s/Any
  "Creates a servlet function for testing"
  [config :- ServiceConfig]
  (-> (create-service config)
      http/create-servlet
      ::http/service-fn))

(s/defn start-server :- s/Any
  "Starts the ingestion server"
  [config :- ServiceConfig]
  (-> (create-service config)
      http/create-server
      http/start))

(s/defn stop-server :- s/Any
  "Stops the ingestion server"
  [server]
  (http/stop server))