# Clogs

**A powerful log query engine built with Clojure**

Clogs is a data-first logging platform designed for flexible log storage and querying using EDN (Extensible Data Notation). Built with ports and adapters architecture, it allows seamless switching between different storage backends while maintaining a clean, functional domain model.

## 🎯 **Purpose**

- **Store logs** in various formats (currently file-based, future Kafka/Elasticsearch support)
- **Query logs** using EDN query syntax with powerful filtering capabilities
- **Validate queries** with schema-based validation using Plumatic Schema
- **Scale storage** by swapping database adapters without changing business logic

## 🏗️ **Architecture**

Clogs follows **Ports and Adapters** pattern:

- **Domain**: Pure functional query logic (`clogs.query.domain`)
- **Engine**: Orchestrates database + domain (`clogs.query.engine`)
- **Database Port**: Interface for storage (`clogs.database.port`)
- **File Adapter**: EDN file implementation (`clogs.database.adapters.file`)
- **Future Adapters**: Kafka, Elasticsearch, etc.

## 🚀 **Quick Start**

### Prerequisites
- Java 11+
- Clojure CLI tools

### Available Scripts

#### Testing
```bash
# Run all tests
clojure -X:test

# Run unit tests only
clojure -X:test-unit

# Run integration tests only
clojure -X:test-integration
```

#### Code Quality
```bash
# Check code for linting issues
clojure -M:lint

# Auto-fix linting issues
clojure -M:lint-fix

# Check code formatting
clojure -M:format

# Auto-fix formatting
clojure -M:format-fix
```

#### Development
```bash
# Start REPL for development
clojure -M:repl

# Check for outdated dependencies
clojure -M:outdated
```

### Quality Checklist

Before committing:
```bash
clojure -M:lint        # ✅ Check linting
clojure -M:format-fix  # ✅ Fix formatting
clojure -X:test        # ✅ Run all tests
```

## 📖 **Basic Usage**

### Creating a Query Engine
```clojure
(require '[clogs.query.engine :as engine])

;; Create file-based query engine
(def db-engine (engine/create-file-engine "/path/to/logs.edn"))
```

### Storing Log Entries
```clojure
;; Store single entry
(engine/store-entry db-engine
  {:timestamp "2024-01-01T10:00:00Z"
   :level "error"
   :service "auth"
   :message "Login failed"})

;; Store multiple entries
(engine/store-entries db-engine
  [{:timestamp "2024-01-01T10:01:00Z" :level "info" :service "auth" :message "User logged in"}
   {:timestamp "2024-01-01T10:02:00Z" :level "error" :service "db" :message "Connection timeout"}])
```

### Querying Logs
```clojure
;; Simple equality query
(engine/execute-query db-engine
  {:where {:field :level :operator :eq :value "error"}})

;; Complex logical operations
(engine/execute-query db-engine
  {:find [:timestamp :message]
   :where {:and [{:field :level :operator :eq :value "error"}
                 {:field :service :operator :ne :value "db"}]}})

;; String operations
(engine/execute-query db-engine
  {:where {:field :message :operator :contains :value "Login"}})
```

### Supported Query Operations

**Comparison**: `:eq`, `:ne`, `:gt`, `:gte`, `:lt`, `:lte`, `:in`
**String**: `:contains`, `:starts-with`, `:ends-with`
**Logical**: `:and`, `:or`, `:not`

## 📁 **Project Structure**

```
clogs/
├── src/clogs/
│   ├── database/
│   │   ├── port.clj              # Database interface
│   │   └── adapters/file.clj     # File storage implementation
│   └── query/
│       ├── domain.clj            # Pure domain logic
│       └── engine.clj            # Query engine
├── test/
│   ├── unit/                     # Fast, isolated tests
│   └── integration/              # End-to-end tests
└── deps.edn                      # Dependencies & scripts
```

## 🔧 **Development**

See [DEVELOPMENT.md](./DEVELOPMENT.md) for detailed development guidelines, testing strategies, and contribution instructions.

## 🎯 **Roadmap**

- [x] File-based storage adapter
- [x] EDN query engine with logical operations
- [x] Comprehensive test suite
- [x] Ports and adapters architecture
- [ ] Kafka storage adapter
- [ ] Elasticsearch storage adapter
- [ ] Query optimization
- [ ] Performance benchmarks

## 📄 **License**

MIT License - see LICENSE file for details.