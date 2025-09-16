(ns ingestion-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string]
            [io.pedestal.test :as pt]
            [clogs.ingestion.service :as service]
            [clogs.query.engine :as engine]))

(def test-db-file "test-logs-ingestion.edn")
(def test-port 8080)

(defn cleanup-test-files [f]
  (try
    (f)
    (finally
      (when (.exists (io/file test-db-file))
        (io/delete-file test-db-file)))))

(use-fixtures :each cleanup-test-files)

(deftest test-logs-endpoint-json
  (testing "POST /logs with valid JSON log entry"
    (let [test-engine (engine/create-file-engine test-db-file)
          servlet (service/create-servlet {:engine test-engine :port test-port})
          log-entry {:level "INFO"
                     :message "Test log message"
                     :timestamp "2024-01-01T10:00:00Z"
                     :service "test-service"}
          response (pt/response-for servlet
                                    :post "/logs"
                                    :headers {"Content-Type" "application/json"}
                                    :body (json/write-str log-entry))]

      (is (= 201 (:status response)))
      (is (= "application/json" (get-in response [:headers "Content-Type"])))
      (let [body (json/read-str (:body response) :key-fn keyword)]
        (is (= "Log entry stored successfully" (:message body))))))

  (testing "POST /logs with invalid JSON"
    (let [test-engine (engine/create-file-engine test-db-file)
          servlet (service/create-servlet {:engine test-engine :port test-port})
          response (pt/response-for servlet
                                    :post "/logs"
                                    :headers {"Content-Type" "application/json"}
                                    :body "invalid json")]

      (is (= 400 (:status response)))
      (let [body (json/read-str (:body response) :key-fn keyword)]
        (is (clojure.string/includes? (:error body) "Invalid request body")))))

  (testing "POST /logs with unsupported content type"
    (let [test-engine (engine/create-file-engine test-db-file)
          servlet (service/create-servlet {:engine test-engine :port test-port})
          response (pt/response-for servlet
                                    :post "/logs"
                                    :headers {"Content-Type" "text/plain"}
                                    :body "some text")]

      (is (= 400 (:status response)))
      (let [body (json/read-str (:body response) :key-fn keyword)]
        (is (clojure.string/includes? (:error body) "unsupported content type"))))))

(deftest test-logs-endpoint-edn
  (testing "POST /logs with valid EDN log entry"
    (let [test-engine (engine/create-file-engine test-db-file)
          servlet (service/create-servlet {:engine test-engine :port test-port})
          log-entry {:level "ERROR"
                     :message "Test error message"
                     :timestamp "2024-01-01T11:00:00Z"
                     :service "error-service"}
          response (pt/response-for servlet
                                    :post "/logs"
                                    :headers {"Content-Type" "application/edn"}
                                    :body (pr-str log-entry))]

      (is (= 201 (:status response)))
      (let [body (json/read-str (:body response) :key-fn keyword)]
        (is (= "Log entry stored successfully" (:message body))))))

  (testing "POST /logs with invalid EDN"
    (let [test-engine (engine/create-file-engine test-db-file)
          servlet (service/create-servlet {:engine test-engine :port test-port})
          response (pt/response-for servlet
                                    :post "/logs"
                                    :headers {"Content-Type" "application/edn"}
                                    :body "{invalid edn")]

      (is (= 400 (:status response)))
      (let [body (json/read-str (:body response) :key-fn keyword)]
        (is (clojure.string/includes? (:error body) "Invalid request body"))))))

(deftest test-logs-persistence
  (testing "Logs are persisted and can be queried"
    (let [test-engine (engine/create-file-engine test-db-file)
          servlet (service/create-servlet {:engine test-engine :port test-port})
          log-entry {:level "DEBUG"
                     :message "Persistence test"
                     :timestamp "2024-01-01T12:00:00Z"
                     :service "persistence-service"}]

      ;; Store log via HTTP endpoint
      (pt/response-for servlet
                       :post "/logs"
                       :headers {"Content-Type" "application/json"}
                       :body (json/write-str log-entry))

      ;; Verify log was persisted by querying directly
      (let [query-result (engine/execute-query test-engine {:where {:field :level
                                                                    :operator :eq
                                                                    :value "DEBUG"}})]
        (is (:success? query-result))
        (is (= 1 (count (:data query-result))))
        (is (= "Persistence test" (-> query-result :data first :message)))))))
