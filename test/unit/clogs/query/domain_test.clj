(ns clogs.query.domain-test
  (:require
   [clogs.query.domain :as domain]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(def sample-logs
  [{:timestamp "2024-01-01T10:00:00Z" :level "error" :service "auth" :message "Login failed"}
   {:timestamp "2024-01-01T10:01:00Z" :level "info" :service "auth" :message "User logged in"}
   {:timestamp "2024-01-01T10:02:00Z" :level "error" :service "db" :message "Connection timeout"}
   {:timestamp "2024-01-01T10:03:00Z" :level "warn" :service "api" :message "Slow response"}])

(deftest test-query-validation
  (testing "Validate simple valid query"
    (let [valid-query {:find [:timestamp] :where {:field :level :operator :eq :value "error"}}
          result (domain/validate-edn-query valid-query)]
      (is (:valid? result))))

  (testing "Validate empty query"
    (let [result (domain/validate-edn-query {})]
      (is (:valid? result))))

  (testing "Reject invalid query - bad find"
    (let [invalid-query {:find "not-a-vector"}
          result (domain/validate-edn-query invalid-query)]
      (is (not (:valid? result)))
      (is (some? (:errors result)))))

  (testing "Reject invalid query - bad where"
    (let [invalid-query {:where {:field :level :value "error"}} ; missing operator
          result (domain/validate-edn-query invalid-query)]
      (is (not (:valid? result)))))

  (testing "Reject invalid logical operation"
    (let [invalid-query {:where {:and "not-a-vector"}}
          result (domain/validate-edn-query invalid-query)]
      (is (not (:valid? result))))))

(deftest test-where-filtering
  (testing "Filter by equality"
    (let [condition {:field :level :operator :eq :value "error"}
          results (domain/apply-where-clause condition sample-logs)]
      (is (= 2 (count results)))
      (is (every? #(= "error" (:level %)) results))))

  (testing "Filter by inequality"
    (let [condition {:field :level :operator :ne :value "error"}
          results (domain/apply-where-clause condition sample-logs)]
      (is (= 2 (count results)))
      (is (every? #(not= "error" (:level %)) results))))

  (testing "Filter by contains"
    (let [condition {:field :message :operator :contains :value "Login"}
          results (domain/apply-where-clause condition sample-logs)]
      (is (> (count results) 0))
      (is (every? #(str/includes? (:message %) "Login") results))))

  (testing "Filter with AND logical operation"
    (let [condition {:and [{:field :level :operator :eq :value "error"}
                           {:field :service :operator :eq :value "auth"}]}
          results (domain/apply-where-clause condition sample-logs)]
      (is (= 1 (count results)))
      (is (every? #(and (= "error" (:level %))
                        (= "auth" (:service %))) results))))

  (testing "Filter with OR logical operation"
    (let [condition {:or [{:field :level :operator :eq :value "error"}
                          {:field :level :operator :eq :value "warn"}]}
          results (domain/apply-where-clause condition sample-logs)]
      (is (= 3 (count results)))
      (is (every? #(contains? #{"error" "warn"} (:level %)) results))))

  (testing "Filter with NOT logical operation"
    (let [condition {:not {:field :level :operator :eq :value "error"}}
          results (domain/apply-where-clause condition sample-logs)]
      (is (= 2 (count results)))
      (is (every? #(not= "error" (:level %)) results))))

  (testing "Filter with nil where clause returns all entries"
    (let [results (domain/apply-where-clause nil sample-logs)]
      (is (= 4 (count results)))
      (is (= sample-logs results)))))
