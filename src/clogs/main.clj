(ns clogs.main
  "Main entry point for the Clogs application"
  (:require [schema.core :as s]
            [clogs.ingestion.service :as service]
            [clogs.database.adapters.file :as file-db])
  (:gen-class))

(def default-config
  {:port 8080
   :db-file "logs.edn"})

(defn create-app-config
  "Creates application configuration"
  [& {:keys [port db-file]
      :or {port (:port default-config)
           db-file (:db-file default-config)}}]
  {:port port
   :database (file-db/create-file-database db-file)})

(defn -main
  "Main entry point"
  [& args]
  (let [port (if (first args)
               (Integer/parseInt (first args))
               (:port default-config))
        db-file (or (second args) (:db-file default-config))
        config (create-app-config :port port :db-file db-file)]

    (println (str "Starting Clogs ingestion server on port " port))
    (println (str "Using database file: " db-file))

    (try
      (let [server (service/start-server config)]
        (println "Server started successfully")
        (println "Press Ctrl+C to stop")

        ;; Add shutdown hook
        (.addShutdownHook (Runtime/getRuntime)
                          (Thread. #(do (println "\nShutting down server...")
                                        (service/stop-server server)
                                        (println "Server stopped"))))

        ;; Keep main thread alive
        @(promise))

      (catch Exception e
        (println "Failed to start server:" (.getMessage e))
        (System/exit 1)))))