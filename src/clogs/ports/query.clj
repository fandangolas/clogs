(ns clogs.ports.query
  "HTTP query port for executing queries against stored logs"
  (:require [schema.core :as s]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clogs.query.engine :as engine]
            [clogs.query.domain :as domain]))

(defn parse-body
  "Parses request body based on content type"
  [content-type body]
  (try
    (case content-type
      "application/json" (json/read-str body :key-fn keyword)
      "application/edn"  (edn/read-string body)
      nil)
    (catch Exception _
      nil)))

(defn validate-query
  "Validates a query using domain schema"
  [query]
  (try
    (s/validate domain/Query query)
    {:valid? true}
    (catch Exception e
      {:valid? false
       :error (str "Invalid query: " (.getMessage e))})))

(defn format-response
  "Formats response data based on content type"
  [content-type data]
  (case content-type
    "application/json" (json/write-str data)
    "application/edn"  (pr-str data)
    (json/write-str data)))

(defn convert-json-query-to-edn
  "Converts JSON query format to EDN format expected by domain"
  [query]
  (cond
    ;; Convert simple condition
    (and (map? query) (:field query) (:operator query))
    (assoc query
           :field (keyword (:field query))
           :operator (keyword (:operator query)))

    ;; Convert WHERE clause recursively
    (and (map? query) (:where query))
    (assoc query :where (convert-json-query-to-edn (:where query)))

    ;; Convert AND/OR clauses
    (and (map? query) (:and query))
    {:and (mapv convert-json-query-to-edn (:and query))}

    (and (map? query) (:or query))
    {:or (mapv convert-json-query-to-edn (:or query))}

    ;; Convert NOT clause
    (and (map? query) (:not query))
    {:not (convert-json-query-to-edn (:not query))}

    ;; Convert FIND clause
    (and (map? query) (:find query))
    (assoc query :find (mapv keyword (:find query)))

    ;; Handle full query with both find and where
    (map? query)
    (cond-> query
      (:find query) (assoc :find (mapv keyword (:find query)))
      (:where query) (assoc :where (convert-json-query-to-edn (:where query))))

    :else query))

(def query-handler
  "Handler interceptor for POST /query endpoint"
  {:name ::query-handler
   :enter
   (fn [context]
     (let [request      (:request context)
           database     (:database context)
           content-type (or (:content-type request)
                            (get-in request [:headers "content-type"]))
           body-str     (if-let [body (:body request)]
                          (slurp body)
                          "")
           parsed-query (parse-body content-type body-str)]

       (cond
         (nil? parsed-query)
         (assoc context :response
                {:status 400
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str {:error "Invalid request body or unsupported content type"})})

         :else
         (let [normalized-query (if (= content-type "application/json")
                                  (convert-json-query-to-edn parsed-query)
                                  parsed-query)
               validation (validate-query normalized-query)]
           (if (:valid? validation)
             (let [result (engine/execute-query database normalized-query)]
               (if (:success? result)
                 (assoc context :response
                        {:status 200
                         :headers {"Content-Type" content-type}
                         :body (format-response content-type {:success true
                                                              :data (:data result)
                                                              :count (:count result)})})
                 (assoc context :response
                        {:status 500
                         :headers {"Content-Type" content-type}
                         :body (format-response content-type {:success false
                                                              :error (:error result)})})))
             (assoc context :response
                    {:status 400
                     :headers {"Content-Type" content-type}
                     :body (format-response content-type {:success false
                                                          :error (:error validation)})}))))))})

(defn database-interceptor
  "Interceptor that injects database into context"
  [database]
  {:name ::database-interceptor
   :enter (fn [context]
            (assoc context :database database))})

(defn create-routes
  "Creates routes for the query port"
  [database]
  #{["/query" :post [(database-interceptor database) query-handler] :route-name :query]})
