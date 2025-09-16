(ns ingestion-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string]
            [io.pedestal.test :as pt]
            [clogs.database.adapters.file :as file-db]
            [aux]))

(def test-port 8080)

(defn create-unique-test-db
  "Creates a unique database file name for each test"
  []
  (str "test-" (java.util.UUID/randomUUID) ".edn"))

(defn with-isolated-database
  "Creates an isolated database for a test and cleans it up afterward"
  [test-fn]
  (let [db-file (create-unique-test-db)]
    (try
      (test-fn db-file)
      (finally
        (when (.exists (io/file db-file))
          (io/delete-file db-file))))))

(def log-entry-example
  {:level "INFO"
   :message "Test log message"
   :timestamp "2024-01-01T10:00:00Z"
   :service "test-service"})
(def log-entry-example-json
  (json/write-str log-entry-example))

(deftest test-logs-endpoint-json
  (testing "POST /logs with valid JSON log entry"
    (with-isolated-database
      (fn [db-file]
        (let [test-database (file-db/create-file-database db-file)
              servlet (aux/create-servlet {:database test-database :port test-port})
              response (pt/response-for servlet
                                        :post    "/logs"
                                        :headers {"Content-Type" "application/json"}
                                        :body    log-entry-example-json)]

          (is (= 201 (:status response)))
          (is (= "application/json" (get-in response [:headers "Content-Type"])))
          (is (= "Log entry stored successfully"
                 (-> (json/read-str (:body response) :key-fn keyword)
                     :message)))))))

  (testing "POST /logs with invalid JSON"
    (with-isolated-database
      (fn [db-file]
        (let [test-database (file-db/create-file-database db-file)
              servlet (aux/create-servlet {:database test-database :port test-port})
              response (pt/response-for servlet
                                        :post "/logs"
                                        :headers {"Content-Type" "application/json"}
                                        :body "invalid json")]

          (is (= 400 (:status response)))
          (let [body (json/read-str (:body response) :key-fn keyword)]
            (is (clojure.string/includes? (:error body) "Invalid request body")))))))

  (testing "POST /logs with unsupported content type"
    (with-isolated-database
      (fn [db-file]
        (let [test-database (file-db/create-file-database db-file)
              servlet (aux/create-servlet {:database test-database :port test-port})
              response (pt/response-for servlet
                                        :post "/logs"
                                        :headers {"Content-Type" "text/plain"}
                                        :body "some text")]

          (is (= 400 (:status response)))
          (let [body (json/read-str (:body response) :key-fn keyword)]
            (is (clojure.string/includes? (:error body) "unsupported content type"))))))))

(deftest test-logs-endpoint-edn
  (testing "POST /logs with valid EDN log entry"
    (with-isolated-database
      (fn [db-file]
        (let [test-database (file-db/create-file-database db-file)
              servlet (aux/create-servlet {:database test-database :port test-port})
              response (pt/response-for servlet
                                        :post "/logs"
                                        :headers {"Content-Type" "application/edn"}
                                        :body (pr-str log-entry-example))]

          (is (= 201 (:status response)))
          (is (= "Log entry stored successfully"
                 (-> (json/read-str (:body response) :key-fn keyword)
                     :message)))))))

  (testing "POST /logs with invalid EDN"
    (with-isolated-database
      (fn [db-file]
        (let [test-database (file-db/create-file-database db-file)
              servlet (aux/create-servlet {:database test-database :port test-port})
              response (pt/response-for servlet
                                        :post "/logs"
                                        :headers {"Content-Type" "application/edn"}
                                        :body "{invalid edn")]

          (is (= 400 (:status response)))
          (let [body (json/read-str (:body response) :key-fn keyword)]
            (is (clojure.string/includes? (:error body) "Invalid request body"))))))))

(deftest test-query-endpoint-json
  (testing "POST /query with valid JSON query"
    (with-isolated-database
      (fn [db-file]
        (let [test-database (file-db/create-file-database db-file)
              servlet (aux/create-servlet {:database test-database :port test-port})
              log-entry {:level "WARN" :message "Query test" :timestamp "2024-01-01T13:00:00Z" :service "query-service"}
              query {:where {:field :level :operator :eq :value "WARN"}}]

          ;; First ingest a log entry
          (pt/response-for servlet
                           :post "/logs"
                           :headers {"Content-Type" "application/json"}
                           :body (json/write-str log-entry))

          ;; Then query for it
          (let [response (pt/response-for servlet
                                          :post "/query"
                                          :headers {"Content-Type" "application/json"}
                                          :body (json/write-str query))]

            (is (= 200 (:status response)))
            (is (= "application/json" (get-in response [:headers "Content-Type"])))
            (let [body (json/read-str (:body response) :key-fn keyword)]
              (is (:success body))
              (is (= 1 (:count body)))
              (is (= 1 (count (:data body))))
              (is (= "Query test" (-> body :data first :message)))))))))

  (testing "POST /query with invalid JSON query"
    (with-isolated-database
      (fn [db-file]
        (let [test-database (file-db/create-file-database db-file)
              servlet (aux/create-servlet {:database test-database :port test-port})
              invalid-query {:where {:field "level" :operator "invalid-operator" :value "test"}}
              response (pt/response-for servlet
                                        :post "/query"
                                        :headers {"Content-Type" "application/json"}
                                        :body (json/write-str invalid-query))]

          (is (= 400 (:status response)))
          (let [body (json/read-str (:body response) :key-fn keyword)]
            (is (not (:success body)))
            (is (clojure.string/includes? (:error body) "Invalid query"))))))))

(testing "POST /query with malformed JSON"
  (with-isolated-database
    (fn [db-file]
      (let [test-database (file-db/create-file-database db-file)
            servlet (aux/create-servlet {:database test-database :port test-port})
            response (pt/response-for servlet
                                      :post "/query"
                                      :headers {"Content-Type" "application/json"}
                                      :body "invalid json")]

        (is (= 400 (:status response)))
        (let [body (json/read-str (:body response) :key-fn keyword)]
          (is (clojure.string/includes? (:error body) "Invalid request body")))))))

(deftest test-query-endpoint-edn
  (testing "POST /query with valid EDN query"
    (with-isolated-database
      (fn [db-file]
        (let [test-database (file-db/create-file-database db-file)
              servlet (aux/create-servlet {:database test-database :port test-port})
              log-entry {:level "TRACE" :message "EDN query test" :timestamp "2024-01-01T14:00:00Z" :service "edn-service"}
              query {:where {:field :level :operator :eq :value "TRACE"}}]

          ;; First ingest a log entry
          (pt/response-for servlet
                           :post "/logs"
                           :headers {"Content-Type" "application/json"}
                           :body (json/write-str log-entry))

          ;; Then query for it using EDN
          (let [response (pt/response-for servlet
                                          :post "/query"
                                          :headers {"Content-Type" "application/edn"}
                                          :body (pr-str query))]

            (is (= 200 (:status response)))
            (is (= "application/edn" (get-in response [:headers "Content-Type"])))
            (let [body (edn/read-string (:body response))]
              (is (:success body))
              (is (= 1 (:count body)))
              (is (= 1 (count (:data body))))
              (is (= "EDN query test" (-> body :data first :message)))))))))

  (testing "POST /query with complex query"
    (with-isolated-database
      (fn [db-file]
        (let [test-database (file-db/create-file-database db-file)
              servlet (aux/create-servlet {:database test-database :port test-port})
              log1 {:level "ERROR" :message "Critical error" :timestamp "2024-01-01T15:00:00Z" :service "critical-service"}
              log2 {:level "WARN" :message "Warning message" :timestamp "2024-01-01T15:01:00Z" :service "warn-service"}
              log3 {:level "INFO" :message "Info message" :timestamp "2024-01-01T15:02:00Z" :service "info-service"}
              query {:find [:level :message]
                     :where {:or [{:field :level :operator :eq :value "ERROR"}
                                  {:field :level :operator :eq :value "WARN"}]}}]

          ;; Ingest multiple log entries
          (doseq [log [log1 log2 log3]]
            (pt/response-for servlet
                             :post "/logs"
                             :headers {"Content-Type" "application/json"}
                             :body (json/write-str log)))

          ;; Query with complex WHERE clause
          (let [response (pt/response-for servlet
                                          :post "/query"
                                          :headers {"Content-Type" "application/edn"}
                                          :body (pr-str query))]

            (is (= 200 (:status response)))
            (let [body (edn/read-string (:body response))]
              (is (:success body))
              (is (= 2 (:count body)))
              (is (= 2 (count (:data body))))
              ;; Should only return ERROR and WARN entries, not INFO
              (is (every? #(contains? #{"ERROR" "WARN"} (:level %)) (:data body)))
              ;; Should only have :level and :message fields due to :find clause
              (is (every? #(= #{:level :message} (set (keys %))) (:data body))))))))))