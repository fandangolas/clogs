# Clogs Query Engine

## Overview

The Clogs Query Engine is a comprehensive, data-first query system built following ports and adapters architecture. It allows querying log data using EDN (Extensible Data Notation) queries with support for filtering, grouping, ordering, and pagination.

## Architecture

### Domain Model (`clogs.query.domain`)
- **Query Record**: Represents parsed queries with find, where, group-by, order-by, limit, and offset fields
- **Condition Record**: Represents individual filter conditions with field, operator, and value
- **LogicalOperation Record**: Represents AND, OR, and NOT operations
- **QueryResult Record**: Contains query results with data, count, and total-count
- **Protocols**: QueryParser, QueryExecutor, and FilterEngine define the core interfaces

### Parser (`clogs.query.parser`)
- **EdnQueryParser**: Implements QueryParser protocol
- Validates EDN queries against Malli schemas
- Transforms EDN queries into domain objects
- Provides detailed error reporting for invalid queries

### Filter Engine (`clogs.query.filters`)
- **LogFilterEngine**: Implements FilterEngine protocol for applying filters
- **LogQueryExecutor**: Implements QueryExecutor protocol for complete query execution
- Supports all query operators: eq, ne, gt, gte, lt, lte, in, contains, starts-with, ends-with
- Handles complex logical operations (AND, OR, NOT)
- Provides grouping, ordering, and pagination capabilities

### Main Engine (`clogs.query.engine`)
- **QueryEngine**: High-level orchestration of parsing and execution
- Convenience functions for common operations
- Query explanation and validation
- Builder helpers for programmatic query construction

## Supported Query Features

### Basic Structure
```edn
{:find [:field1 :field2]           ; Fields to return (optional)
 :where {...}                      ; Filter conditions (optional)
 :group-by [:field1]               ; Grouping fields (optional)
 :order-by [[:field1 :asc]]        ; Sorting (optional)
 :limit 100                        ; Result limit (optional)
 :offset 10}                       ; Result offset (optional)
```

### Where Conditions

#### Simple Conditions
```edn
{:field :level :operator :eq :value "error"}
{:field :timestamp :operator :gte :value "2024-01-01T00:00:00Z"}
{:field :service :operator :in :value ["auth-service" "api-service"]}
{:field :message :operator :contains :value "user"}
```

#### Logical Operations
```edn
;; AND operation
{:where {:and [{:field :level :operator :eq :value "error"}
               {:field :service :operator :eq :value "auth-service"}]}}

;; OR operation
{:where {:or [{:field :level :operator :eq :value "error"}
              {:field :level :operator :eq :value "warn"}]}}

;; NOT operation
{:where {:not {:field :level :operator :eq :value "debug"}}}

;; Nested operations
{:where {:and [{:field :level :operator :in :value ["error" "warn"]}
               {:or [{:field :service :operator :eq :value "auth-service"}
                     {:field :service :operator :eq :value "db-service"}]}]}}
```

### Supported Operators
- `:eq` - Equality
- `:ne` - Not equal
- `:gt` - Greater than
- `:gte` - Greater than or equal
- `:lt` - Less than
- `:lte` - Less than or equal
- `:in` - Value in collection
- `:contains` - String contains or collection contains
- `:starts-with` - String starts with
- `:ends-with` - String ends with

### Grouping and Aggregation
```edn
;; Group by service
{:group-by [:service]}

;; Group by multiple fields
{:group-by [:service :level]}

;; Grouped results include :count and :entries fields
```

### Ordering
```edn
;; Single field ascending (default)
{:order-by [:timestamp]}

;; Single field descending
{:order-by [[:timestamp :desc]]}

;; Multiple fields
{:order-by [[:service :asc] [:timestamp :desc]]}
```

## Usage Examples

### Basic Usage
```clojure
(require '[clogs.query.engine :as engine])

;; Create engine
(def query-engine (engine/create-query-engine))

;; Sample log data
(def logs [{:timestamp #inst "2024-01-01T10:00:00Z"
            :level "error"
            :service "auth-service"
            :message "Login failed"}
           {:timestamp #inst "2024-01-01T10:01:00Z"
            :level "info"
            :service "auth-service"
            :message "User logged in"}])

;; Execute query
(def result (engine/query query-engine
                         {:find [:service :message]
                          :where {:field :level :operator :eq :value "error"}}
                         logs))

;; Access results
(:count result)      ; Number of results
(:total-count result) ; Total before pagination
(:data result)       ; Actual result data
```

### Complex Queries
```clojure
;; Multi-condition query with grouping
(engine/query query-engine
              {:find [:service :level :count]
               :where {:and [{:field :level :operator :in :value ["error" "warn"]}
                            {:field :service :operator :contains :value "service"}]}
               :group-by [:service :level]
               :order-by [[:service :asc] [:count :desc]]
               :limit 10}
              logs)
```

### Query Validation
```clojure
;; Validate query structure
(engine/validate query-engine {:find [:timestamp] :where {:invalid "query"}})
;; => {:valid? false :errors {...}}

;; Explain query
(engine/explain-query {:find [:service] :where {...} :group-by [:service]})
;; => {:valid? true :explanation {...}}
```

### Convenience Functions
```clojure
;; Direct execution without creating engine
(engine/execute-edn-query {:find [:level]} logs)

;; Direct validation
(engine/validate-edn-query {:find [:level]})

;; Query builders
(def query (engine/build-simple-query
             :find [:service :message]
             :where (engine/build-and-condition
                      (engine/build-condition :level :eq "error")
                      (engine/build-condition :service :contains "auth"))
             :limit 10))
```

## Testing

The query engine includes comprehensive unit tests covering:
- Domain model validation
- Query parsing and transformation
- Filter engine functionality
- Complex query execution
- Edge cases and error handling
- Performance benchmarking

### Test Structure
- `test/clogs/query/fixtures.clj` - Test data and sample queries
- `test/clogs/query/domain_test.clj` - Domain model tests
- `test/clogs/query/parser_test.clj` - Parser functionality tests
- `test/clogs/query/filters_test.clj` - Filter engine tests
- `test/clogs/query/engine_test.clj` - Integration tests

## Integration with Clogs

The query engine integrates seamlessly with the existing Clogs log validation and formatting:

```clojure
(require '[clogs.core :as clogs]
         '[clogs.query.engine :as query])

;; Create logs using Clogs core functions
(def logs [(clogs/create-log-entry "error" "Auth failed" "auth-service")
           (clogs/create-log-entry "info" "User login" "auth-service")])

;; Query the logs
(def results (query/execute-edn-query
               {:find [:service :message]
                :where {:field :level :operator :eq :value "error"}}
               logs))
```

## Performance Characteristics

- Efficient filtering using Clojure's built-in sequence operations
- Memory-conscious with lazy evaluation where possible
- Pagination support to handle large datasets
- Benchmark helpers included for performance testing
- Tested with datasets up to 10,000 entries

## Future Enhancements

The current MVP provides a solid foundation for:
- Integration with storage backends (Phase 2 of roadmap)
- HTTP API endpoints for remote querying
- Real-time streaming query capabilities
- Advanced aggregation functions (sum, avg, min, max)
- Time-window based queries
- Full-text search integration
- Query optimization and caching