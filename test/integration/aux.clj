(ns aux
  "Auxiliary functions for integration tests"
  (:require [schema.core :as s]
            [io.pedestal.http :as http]
            [clogs.ingestion.service :as service]))

(s/defn create-servlet :- s/Any
  "Creates a servlet function for testing"
  [config :- service/ServiceConfig]
  (-> (service/create-service config)
      http/create-servlet
      ::http/service-fn))