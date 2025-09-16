(ns clogs.database.adapters.file
  "File-based database adapter - implements DatabasePort using EDN files"
  (:require [schema.core :as s]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clogs.database.port :as port]))

;; File database configuration
(s/defschema FileDbConfig
  "Configuration for file-based database"
  {:file-path s/Str
   (s/optional-key :encoding) s/Str})

;; File database record that implements the DatabasePort protocol
(defrecord FileDatabase [config]
  port/DatabasePort

  (store-entry [_this entry]
               (try (with-open [writer (io/writer (:file-path config)
                                                  :append true
                                                  :encoding (:encoding config "UTF-8"))]
                      (.write writer (pr-str entry))
                      (.write writer "\n"))
                    (port/success-result)

                    (catch Exception e
                      (port/error-result (str "Failed to store entry: " (.getMessage e))))))

  (store-entries [_this entries]
                 (try
                   (with-open [writer (io/writer (:file-path config)
                                                 :append true
                                                 :encoding (:encoding config "UTF-8"))]
                     (doseq [entry entries]
                       (.write writer (pr-str entry))
                       (.write writer "\n")))
                   (port/success-result)
                   (catch Exception e
                     (port/error-result (str "Failed to store entries: " (.getMessage e))))))

  (read-all-entries [_this]
                    (try (let [file    (io/file (:file-path config))
                               exists? (.exists file)]

                           (if exists?
                             (with-open [reader (io/reader file :encoding (:encoding config "UTF-8"))]
                               (->> (line-seq reader)
                                    (remove empty?)
                                    (map edn/read-string)
                                    vec
                                    port/success-result))

                             (port/success-result [])))

                         (catch Exception e
                           (port/error-result (str "Failed to read entries: " (.getMessage e))))))

  (clear-data [_this]
              (try (let [file (io/file (:file-path config))]
                     (when (.exists file) (io/delete-file file)))
                   (port/success-result)

                   (catch Exception e
                     (port/error-result (str "Failed to clear data: " (.getMessage e))))))

  (health-check [_this]
                (try
                  (let [file (io/file (:file-path config))
                        parent-dir (.getParentFile file)]
                    (cond
                      (and parent-dir (not (.exists parent-dir)))
                      (port/error-result "Parent directory does not exist")

                      (and (.exists file) (not (.canRead file)))
                      (port/error-result "Cannot read database file")

                      (and (.exists file) (not (.canWrite file)))
                      (port/error-result "Cannot write to database file")

                      :else
                      (port/success-result {:status "healthy"
                                            :file-exists (.exists file)
                                            :file-path (:file-path config)})))
                  (catch Exception e
                    (port/error-result (str "Health check failed: " (.getMessage e)))))))

;; Factory functions
(s/defn create-file-database :- FileDatabase
  "Creates a new file-based database instance"
  [file-path :- s/Str]
  (->FileDatabase {:file-path file-path
                   :encoding "UTF-8"}))
