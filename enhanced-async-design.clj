;; Enhanced Async Design for Clogs
(ns clogs.enhanced-async
  (:require [clojure.core.async :as async :refer [go go-loop chan >! <! >!! <!! thread]]
            [clojure.core.async.impl.protocols :as impl]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [schema.core :as s]))

;; ============================================================================
;; METRICS COLLECTION
;; ============================================================================

(defn create-metrics-atom []
  "Creates metrics collection atom"
  (atom {:requests-received 0
         :requests-processed 0
         :requests-failed 0
         :parse-errors 0
         :validation-errors 0
         :write-errors 0
         :queue-depths {:parse 0 :validate 0 :write 0}
         :processing-times {:parse [] :validate [] :write []}
         :last-reset (System/currentTimeMillis)}))

(defn record-metric! [metrics-atom metric value]
  "Records a metric value"
  (swap! metrics-atom update metric (fnil inc 0)))

(defn record-timing! [metrics-atom phase duration-ms]
  "Records timing for a processing phase"
  (swap! metrics-atom update-in [:processing-times phase]
         #(take-last 100 (conj % duration-ms))))

(defn update-queue-depth! [metrics-atom phase depth]
  "Updates queue depth metric"
  (swap! metrics-atom assoc-in [:queue-depths phase] depth))

;; ============================================================================
;; BACKPRESSURE AND CIRCUIT BREAKER
;; ============================================================================

(defn create-backpressure-config []
  {:max-queue-depth 1000
   :circuit-breaker-threshold 10
   :circuit-breaker-timeout-ms 5000
   :slow-consumer-threshold-ms 1000})

(defn check-backpressure [channel max-depth]
  "Checks if channel is experiencing backpressure"
  (let [queue-depth (count (impl/read-buf channel))]
    (when (> queue-depth max-depth)
      {:backpressure? true
       :queue-depth queue-depth
       :max-depth max-depth})))

;; ============================================================================
;; ENHANCED ASYNC PIPELINE
;; ============================================================================

(defn create-parsing-pipeline [parse-chan validate-chan metrics-atom]
  "Creates async parsing pipeline using thread pool for blocking operations"
  (let [workers 4] ; CPU-bound, use CPU core count
    (dotimes [_ workers]
      (thread ; Use thread for blocking JSON/EDN parsing
        (loop []
          (when-let [{:keys [content-type body-str response-chan request-id]} (<!! parse-chan)]
            (let [start-time (System/nanoTime)]
              (try
                (record-metric! metrics-atom :parse-attempts)
                (let [parsed-entry (case content-type
                                     "application/json" (json/read-str body-str :key-fn keyword)
                                     "application/edn"  (edn/read-string body-str)
                                     nil)]
                  (if parsed-entry
                    (do
                      (record-metric! metrics-atom :parse-success)
                      (>!! validate-chan {:entry parsed-entry
                                          :response-chan response-chan
                                          :request-id request-id}))
                    (do
                      (record-metric! metrics-atom :parse-errors)
                      (>!! response-chan {:status 400
                                          :error "Invalid request body"}))))
                (catch Exception e
                  (record-metric! metrics-atom :parse-errors)
                  (>!! response-chan {:status 400
                                      :error (str "Parse error: " (.getMessage e))}))
                (finally
                  (record-timing! metrics-atom :parse
                                  (/ (- (System/nanoTime) start-time) 1000000)))))
            (recur)))))))

(defn create-validation-pipeline [validate-chan write-chan metrics-atom schema]
  "Creates async validation pipeline"
  (let [workers 2] ; Validation is faster, fewer workers needed
    (dotimes [_ workers]
      (thread
        (loop []
          (when-let [{:keys [entry response-chan request-id]} (<!! validate-chan)]
            (let [start-time (System/nanoTime)]
              (try
                (record-metric! metrics-atom :validation-attempts)
                (s/validate schema entry)
                (record-metric! metrics-atom :validation-success)
                (>!! write-chan {:entry entry
                                 :response-chan response-chan
                                 :request-id request-id})
                (catch Exception e
                  (record-metric! metrics-atom :validation-errors)
                  (>!! response-chan {:status 400
                                      :error (str "Validation error: " (.getMessage e))}))
                (finally
                  (record-timing! metrics-atom :validate
                                  (/ (- (System/nanoTime) start-time) 1000000)))))
            (recur)))))))

(defn create-writing-pipeline [write-chan database metrics-atom]
  "Creates async writing pipeline with proper thread pool for file I/O"
  (let [workers 1] ; Single writer to maintain order
    (dotimes [_ workers]
      (thread ; Use thread for blocking file I/O
        (loop []
          (when-let [{:keys [entry response-chan request-id]} (<!! write-chan)]
            (let [start-time (System/nanoTime)]
              (try
                (record-metric! metrics-atom :write-attempts)
                (let [result (store-entry database entry)]
                  (if (:success? result)
                    (do
                      (record-metric! metrics-atom :write-success)
                      (>!! response-chan {:status 201
                                          :message "Log entry stored successfully"}))
                    (do
                      (record-metric! metrics-atom :write-errors)
                      (>!! response-chan {:status 500
                                          :error (:error result)}))))
                (catch Exception e
                  (record-metric! metrics-atom :write-errors)
                  (>!! response-chan {:status 500
                                      :error (str "Write error: " (.getMessage e))}))
                (finally
                  (record-timing! metrics-atom :write
                                  (/ (- (System/nanoTime) start-time) 1000000)))))
            (recur)))))))

;; ============================================================================
;; NON-BLOCKING REQUEST HANDLER
;; ============================================================================

(defn create-async-handler [parse-chan metrics-atom backpressure-config]
  "Creates non-blocking request handler"
  {:name ::async-logs-handler
   :enter
   (fn [context]
     (let [request (:request context)
           request-id (str (java.util.UUID/randomUUID))
           content-type (or (:content-type request)
                            (get-in request [:headers "content-type"]))
           body-str (if-let [body (:body request)]
                      (slurp body) ; Still need to read body, but we'll optimize this
                      "")
           response-chan (chan 1)]

       ;; Record request received
       (record-metric! metrics-atom :requests-received)

       ;; Check backpressure before processing
       (if-let [bp (check-backpressure parse-chan (:max-queue-depth backpressure-config))]
         (do
           (record-metric! metrics-atom :requests-backpressure)
           (assoc context :response
                  {:status 503
                   :headers {"Content-Type" "application/json"
                             "Retry-After" "1"}
                   :body (json/write-str {:error "Service temporarily overloaded"
                                         :queue-depth (:queue-depth bp)})}))
         (do
           ;; Submit to async pipeline
           (>!! parse-chan {:content-type content-type
                           :body-str body-str
                           :response-chan response-chan
                           :request-id request-id})

           ;; Wait for response (with timeout)
           (let [timeout-chan (async/timeout 5000) ; 5 second timeout
                 [response port] (async/alts!! [response-chan timeout-chan])]
             (if (= port timeout-chan)
               (do
                 (record-metric! metrics-atom :requests-timeout)
                 (assoc context :response
                        {:status 504
                         :headers {"Content-Type" "application/json"}
                         :body (json/write-str {:error "Request timeout"})}))
               (do
                 (record-metric! metrics-atom :requests-processed)
                 (assoc context :response
                        {:status (:status response)
                         :headers {"Content-Type" "application/json"}
                         :body (json/write-str (select-keys response [:message :error]))}))))))))})

;; ============================================================================
;; METRICS ENDPOINT
;; ============================================================================

(defn create-metrics-handler [metrics-atom]
  "Creates metrics endpoint for real-time monitoring"
  {:name ::metrics-handler
   :enter
   (fn [context]
     (let [metrics @metrics-atom
           uptime-ms (- (System/currentTimeMillis) (:last-reset metrics))
           avg-times (fn [times]
                       (if (seq times)
                         (/ (reduce + times) (count times))
                         0))]
       (assoc context :response
              {:status 200
               :headers {"Content-Type" "application/json"}
               :body (json/write-str
                      {:uptime-ms uptime-ms
                       :requests metrics
                       :queue-depths (:queue-depths metrics)
                       :avg-processing-times
                       {:parse-ms (avg-times (get-in metrics [:processing-times :parse]))
                        :validate-ms (avg-times (get-in metrics [:processing-times :validate]))
                        :write-ms (avg-times (get-in metrics [:processing-times :write]))}
                       :timestamp (System/currentTimeMillis)})})))})

;; ============================================================================
;; SYSTEM INITIALIZATION
;; ============================================================================

(defn create-async-system [database schema]
  "Creates complete async processing system"
  (let [metrics-atom (create-metrics-atom)
        backpressure-config (create-backpressure-config)

        ;; Create channels with appropriate buffer sizes
        parse-chan (chan 100)      ; Parsing queue
        validate-chan (chan 100)   ; Validation queue
        write-chan (chan 100)      ; Writing queue

        ;; Start processing pipelines
        _ (create-parsing-pipeline parse-chan validate-chan metrics-atom)
        _ (create-validation-pipeline validate-chan write-chan metrics-atom schema)
        _ (create-writing-pipeline write-chan database metrics-atom)]

    {:parse-chan parse-chan
     :metrics-atom metrics-atom
     :backpressure-config backpressure-config
     :handler (create-async-handler parse-chan metrics-atom backpressure-config)
     :metrics-handler (create-metrics-handler metrics-atom)}))

;; ============================================================================
;; HEALTH MONITORING
;; ============================================================================

(defn monitor-system-health [metrics-atom]
  "Monitors system health and logs warnings"
  (go-loop []
    (async/<! (async/timeout 10000)) ; Check every 10 seconds
    (let [metrics @metrics-atom
          queue-depths (:queue-depths metrics)]

      ;; Check for queue buildup
      (doseq [[phase depth] queue-depths]
        (when (> depth 50)
          (println (format "WARNING: %s queue depth high: %d" phase depth))))

      ;; Check error rates
      (let [total-requests (:requests-received metrics 0)
            failed-requests (:requests-failed metrics 0)]
        (when (and (> total-requests 100)
                   (> (/ failed-requests total-requests) 0.05))
          (println (format "WARNING: High error rate: %.2f%%"
                           (* 100 (/ failed-requests total-requests))))))
    (recur)))