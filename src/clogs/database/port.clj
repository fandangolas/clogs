(ns clogs.database.port
  "Database port - defines the interface for database operations"
  (:require [schema.core :as s]))

;; Database operation result schema
(s/defschema DatabaseResult
  "Result of database operations"
  {:success? s/Bool
   (s/optional-key :data) s/Any
   (s/optional-key :error) s/Str
   (s/optional-key :count) s/Int})

;; Database protocol - defines the contract for database implementations
(defprotocol DatabasePort
  "Protocol defining database operations for log storage and retrieval"

  (store-entry [this entry]
               "Stores a single log entry. Returns DatabaseResult.")

  (store-entries [this entries]
                 "Stores multiple log entries. Returns DatabaseResult.")

  (read-all-entries [this]
                    "Reads all log entries. Returns DatabaseResult with :data containing entries.")

  (clear-data [this]
              "Clears all data (for testing). Returns DatabaseResult.")

  (health-check [this]
                "Checks if the database is healthy/available. Returns DatabaseResult."))

;; Helper functions for working with DatabaseResult
(s/defn success-result :- DatabaseResult
  "Creates a successful database result"
  ([data :- s/Any]
   {:success? true
    :data data
    :count (if (sequential? data) (count data) 1)})
  ([]
   {:success? true}))

(s/defn error-result :- DatabaseResult
  "Creates an error database result"
  [error-message :- s/Str]
  {:success? false
   :error error-message})
