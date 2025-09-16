(ns clogs.query.domain
  "Core domain functions for query engine using pure functional programming with Plumatic Schema"
  (:require [schema.core :as s]
            [clojure.string :as str]))

(def Operator
  "Valid query operators"
  (s/enum :eq :ne :gt :gte :lt :lte :in :contains :starts-with :ends-with))

(def Condition
  "Schema for basic field conditions"
  {:field s/Keyword
   :operator Operator
   :value s/Any})

(s/defschema WhereClause
  "Schema for WHERE clauses supporting logical operations"
  (s/conditional
   #(contains? % :field) Condition
   #(contains? % :and)   {:and [(s/recursive #'WhereClause)]}
   #(contains? % :or)    {:or  [(s/recursive #'WhereClause)]}
   #(contains? % :not)   {:not (s/recursive #'WhereClause)}))

(def Query
  "Schema for EDN queries"
  {(s/optional-key :find) [s/Keyword]
   (s/optional-key :where) WhereClause})

(def LogEntry
  "Schema for log entries"
  {s/Keyword s/Any})

(def ValidationResult
  "Schema for validation results"
  {:valid? s/Bool
   (s/optional-key :errors) s/Any})

(s/defn validate-edn-query :- ValidationResult
  "Validates an EDN query and returns validation result"
  [edn-query :- s/Any]
  (try (s/validate Query edn-query)
       {:valid? true}

       (catch Exception e
         {:valid? false
          :errors (str "Invalid query: " (.getMessage e))})))

(s/defn evaluate-condition :- s/Bool
  "Evaluates a single condition against a log entry"
  [condition :- Condition
   log-entry :- LogEntry]
  (let [{:keys [field operator value]} condition
        entry-value (get log-entry field)]
    (case operator
      :eq          (= entry-value value)
      :ne          (not= entry-value value)
      :gt          (> entry-value value)
      :gte         (>= entry-value value)
      :lt          (< entry-value value)
      :lte         (<= entry-value value)
      :in          (contains? (set value) entry-value)
      :contains    (and (string? entry-value) (str/includes? entry-value value))
      :starts-with (and (string? entry-value) (str/starts-with? entry-value value))
      :ends-with   (and (string? entry-value) (str/ends-with? entry-value value))
      false)))

(s/defn evaluate-where-clause :- s/Bool
  "Evaluates any where clause (condition or logical operation) against a log entry"
  [where-clause :- WhereClause
   log-entry :- LogEntry]
  (cond
    ;; Simple condition
    (and (:field where-clause) (:operator where-clause))
    (evaluate-condition where-clause log-entry)

    ;; AND operation - all conditions must be true
    (:and where-clause)
    (every? #(evaluate-where-clause % log-entry) (:and where-clause))

    ;; OR operation - at least one condition must be true
    (:or where-clause)
    (some #(evaluate-where-clause % log-entry) (:or where-clause))

    ;; NOT operation - condition must be false
    (:not where-clause)
    (not (evaluate-where-clause (:not where-clause) log-entry))

    :else false))

(s/defn apply-where-clause :- [LogEntry]
  "Filters log entries based on where conditions"
  [where-clause :- (s/maybe WhereClause)
   log-entries :- [LogEntry]]

  (if (nil? where-clause)
    log-entries
    (-> #(evaluate-where-clause where-clause %)
        (filter log-entries)
        vec)))
