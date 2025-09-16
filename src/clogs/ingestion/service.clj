(ns clogs.ingestion.service
  "HTTP service that composes ingestion and query ports"
  (:require [schema.core :as s]
            [clojure.set :as set]
            [io.pedestal.http :as http]
            [clogs.ports.ingestion :as ingestion-port]
            [clogs.ports.query :as query-port]))

(s/defschema ServiceConfig
  "Configuration for the service"
  {:database s/Any
   :port s/Int})

(defn create-routes
  "Creates routes by combining all port routes"
  [database]
  (set/union (ingestion-port/create-routes database)
             (query-port/create-routes database)))

(s/defn create-service :- s/Any
  "Creates a Pedestal service configuration"
  [config :- ServiceConfig]
  (-> {::http/routes (create-routes (:database config))
       ::http/type   :jetty
       ::http/port   (:port config)
       ::http/join?  false}
      http/default-interceptors
      http/dev-interceptors))

(s/defn start-server :- s/Any
  "Starts the server"
  [config :- ServiceConfig]
  (-> (create-service config)
      http/create-server
      http/start))

(s/defn stop-server :- s/Any
  "Stops the server"
  [server]
  (http/stop server))
