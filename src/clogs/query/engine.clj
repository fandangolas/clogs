(ns clogs.query.engine
  "Query engine that integrates database with domain logic using ports and adapters"
  (:require [schema.core :as s]
            [clogs.query.domain :as domain]
            [clogs.database.port :as db-port]
            [clogs.database.adapters.file :as file-adapter]))

(s/defschema EngineConfig
  "Configuration for the query engine"
  {:database s/Any})

(def QueryResult db-port/DatabaseResult)

(s/defn create-engine
  "Creates a new query engine with a database implementation"
  [database :- db-port/DatabasePort]
  {:database database})

(s/defn create-file-engine
  "Creates a new query engine with file-based database"
  [file-path :- s/Str]
  (create-engine (file-adapter/create-file-database file-path)))

(s/defn store-entry :- db-port/DatabaseResult
  "Stores a single log entry"
  [engine :- EngineConfig
   entry :- domain/LogEntry]
  (db-port/store-entry (:database engine) entry))

(s/defn store-entries :- db-port/DatabaseResult
  "Stores multiple log entries"
  [engine :- EngineConfig
   entries :- [domain/LogEntry]]
  (db-port/store-entries (:database engine) entries))

(s/defn all-entries->filtered :- db-port/DatabaseResult
  [db-result :- db-port/DatabaseResult
   {:keys [find where]} :- s/Any]
  (let [{:keys [success? data]} db-result]
    (if (not success?)
      db-result

      (as-> (domain/apply-where-clause where data) $
        (-> (if (nil? find) $ (map #(select-keys % find) $))
            vec
            db-port/success-result)))))

(s/defn execute-query :- QueryResult
  "Executes an EDN query against the stored data"
  [engine :- EngineConfig
   edn-query :- s/Any]
  (let [{:keys [valid?] :as validation-result} (domain/validate-edn-query edn-query)]

    (if (not valid?)
      (db-port/error-result (:errors validation-result))

      (-> (db-port/read-all-entries (:database engine))
          (all-entries->filtered edn-query)))))
