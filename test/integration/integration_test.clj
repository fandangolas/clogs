(ns integration-test
  "Integration tests for the complete EDN query system"
  (:require
   [clogs.query.engine :as engine]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]))

;; Test configuration
(def test-db-path "/tmp/clogs-test.edn")

;; Test fixture to clean up test file
(defn cleanup-test-db [f]
  (let [test-file (io/file test-db-path)]
    (when (.exists test-file)
      (.delete test-file)))
  (f)
  (let [test-file (io/file test-db-path)]
    (when (.exists test-file)
      (.delete test-file))))

(use-fixtures :each cleanup-test-db)

;; Sample test data
(def sample-logs
  [{:timestamp "2024-01-01T10:00:00Z" :level "error" :service "auth" :message "Login failed"}
   {:timestamp "2024-01-01T10:01:00Z" :level "info" :service "auth" :message "User logged in"}
   {:timestamp "2024-01-01T10:02:00Z" :level "error" :service "db" :message "Connection timeout"}
   {:timestamp "2024-01-01T10:03:00Z" :level "warn" :service "api" :message "Slow response"}
   {:timestamp "2024-01-01T10:04:00Z" :level "info" :service "auth" :message "Password reset"}
   {:timestamp "2024-01-01T10:05:00Z" :level "error" :service "api" :message "Rate limit exceeded"}])

(deftest test-engine-lifecycle
  (testing "Engine creation and basic operations"
    (let [engine (engine/create-file-engine test-db-path)]

      ;; Test engine creation
      (is (some? engine))
      (is (= test-db-path (get-in engine [:database :config :file-path])))

      ;; Test storing single entry
      (let [entry {:timestamp "2024-01-01T10:00:00Z" :level "info" :message "Test message"}
            result (engine/store-entry engine entry)]
        (is (:success? result))
        (is (nil? (:error result))))

      ;; Test storing multiple entries
      (let [result (engine/store-entries engine sample-logs)]
        (is (:success? result))
        (is (nil? (:error result)))))))

(deftest test-basic-queries
  (testing "Basic EDN query execution"
    (let [engine (engine/create-file-engine test-db-path)]

      ;; Store test data
      (engine/store-entries engine sample-logs)

      ;; Test query without where clause (return all)
      (let [query {}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 6 (:count result)))
        (is (= 6 (count (:data result)))))

      ;; Test query with find clause only
      (let [query {:find [:timestamp :level]}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 6 (:count result)))
        (is (every? #(= #{:timestamp :level} (set (keys %))) (:data result))))

      ;; Test simple where clause
      (let [query {:where {:field :level :operator :eq :value "error"}}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 3 (:count result)))
        (is (every? #(= "error" (:level %)) (:data result))))

      ;; Test where clause with find
      (let [query {:find [:service :message]
                   :where {:field :level :operator :eq :value "error"}}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 3 (:count result)))
        (is (every? #(= #{:service :message} (set (keys %))) (:data result)))))))

(deftest test-advanced-queries
  (testing "Advanced EDN queries with logical operations"
    (let [engine (engine/create-file-engine test-db-path)]

      ;; Store test data
      (engine/store-entries engine sample-logs)

      ;; Test AND operation
      (let [query {:where {:and [{:field :level :operator :eq :value "error"}
                                 {:field :service :operator :eq :value "auth"}]}}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 1 (:count result)))
        (is (every? #(and (= "error" (:level %))
                          (= "auth" (:service %))) (:data result))))

      ;; Test OR operation
      (let [query {:where {:or [{:field :level :operator :eq :value "error"}
                                {:field :level :operator :eq :value "warn"}]}}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 4 (:count result)))
        (is (every? #(contains? #{"error" "warn"} (:level %)) (:data result))))

      ;; Test NOT operation
      (let [query {:where {:not {:field :level :operator :eq :value "error"}}}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 3 (:count result)))
        (is (every? #(not= "error" (:level %)) (:data result))))

      ;; Test complex nested query
      (let [query {:find [:timestamp :service]
                   :where {:and [{:or [{:field :level :operator :eq :value "error"}
                                       {:field :level :operator :eq :value "warn"}]}
                                 {:field :service :operator :ne :value "db"}]}}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 3 (:count result)))
        (is (every? #(and (contains? #{"error" "warn"} (:level %))
                          (not= "db" (:service %)))
                    ;; Need to get full entries to check level since find only returns :timestamp and :service
                    (let [all-result (engine/execute-query engine {:where (:where query)})]
                      (:data all-result))))))))

(deftest test-string-operations
  (testing "String-based query operations"
    (let [engine (engine/create-file-engine test-db-path)]

      ;; Store test data
      (engine/store-entries engine sample-logs)

      ;; Test contains operation
      (let [query {:where {:field :message :operator :contains :value "Login"}}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 1 (:count result)))
        (is (every? #(str/includes? (:message %) "Login") (:data result))))

      ;; Test starts-with operation
      (let [query {:where {:field :message :operator :starts-with :value "User"}}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 1 (:count result)))
        (is (every? #(str/starts-with? (:message %) "User") (:data result))))

      ;; Test ends-with operation
      (let [query {:where {:field :message :operator :ends-with :value "failed"}}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 1 (:count result)))
        (is (every? #(str/ends-with? (:message %) "failed") (:data result)))))))

(deftest test-error-handling
  (testing "Error handling in queries and storage"
    (let [engine (engine/create-file-engine test-db-path)]

      ;; Test invalid query schema
      (let [query {:find "invalid-find"}  ; Should be a vector
            result (engine/execute-query engine query)]
        (is (not (:success? result)))
        (is (some? (:error result))))

      ;; Test invalid where clause
      (let [query {:where {:field :level :value "error"}}  ; Missing operator
            result (engine/execute-query engine query)]
        (is (not (:success? result)))
        (is (some? (:error result))))

      ;; Test query on empty storage
      (let [query {:where {:field :level :operator :eq :value "error"}}
            result (engine/execute-query engine query)]
        (is (:success? result))
        (is (= 0 (:count result)))
        (is (empty? (:data result)))))))

(deftest test-persistence
  (testing "Data persistence across engine instances"
    ;; First engine instance - store data
    (let [engine1 (engine/create-file-engine test-db-path)]
      (engine/store-entries engine1 sample-logs)

      ;; Verify data is stored
      (let [query {:where {:field :level :operator :eq :value "error"}}
            result (engine/execute-query engine1 query)]
        (is (:success? result))
        (is (= 3 (:count result)))))

    ;; Second engine instance - read persisted data
    (let [engine2 (engine/create-file-engine test-db-path)]
      ;; Should be able to read the same data
      (let [query {:where {:field :level :operator :eq :value "error"}}
            result (engine/execute-query engine2 query)]
        (is (:success? result))
        (is (= 3 (:count result)))
        (is (every? #(= "error" (:level %)) (:data result))))

      ;; Add more data
      (engine/store-entry engine2 {:timestamp "2024-01-01T10:06:00Z" :level "debug" :service "test" :message "Debug message"})

      ;; Verify total count increased
      (let [query {}
            result (engine/execute-query engine2 query)]
        (is (:success? result))
        (is (= 7 (:count result)))))))
