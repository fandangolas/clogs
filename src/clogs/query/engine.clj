(ns clogs.query.engine
  "Functional query engine that defines query capabilities and execution logic"
  (:require [clogs.query.domain :as domain]
            [clogs.database.port :as db-port]))

(defn apply-query-to-entries
  "Applies a query to a collection of log entries"
  [{:keys [find where]} entries]
  (let [filtered (if where
                   (domain/apply-where-clause where entries)
                   entries)
        projected (if find
                    (mapv #(select-keys % find) filtered)
                    filtered)]
    {:success? true
     :data projected
     :count (count projected)}))

(defn store-entry
  "Stores a single log entry using the provided database"
  [database entry]
  (db-port/store-entry database entry))

(defn store-entries
  "Stores multiple log entries using the provided database"
  [database entries]
  (db-port/store-entries database entries))

(defn execute-query
  "Executes a query against stored data using the provided database"
  [database query]
  (let [{:keys [valid?] :as validation-result} (domain/validate-edn-query query)]
    (if (not valid?)
      (db-port/error-result (:errors validation-result))
      (let [all-entries-result (db-port/read-all-entries database)]
        (if (:success? all-entries-result)
          (apply-query-to-entries query (:data all-entries-result))
          all-entries-result)))))