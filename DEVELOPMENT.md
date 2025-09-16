# Clogs Development Guide

## Code Quality Tools

### Linting with clj-kondo

```bash
# Check for linting issues
clojure -M:lint

# Auto-fix linting issues (when possible)
clojure -M:lint-fix
```

### Formatting with cljfmt

```bash
# Check formatting
clojure -M:format

# Auto-fix formatting
clojure -M:format-fix
```

### Dependency Management

```bash
# Check for outdated dependencies
clojure -M:outdated
```

## Testing

### Run All Tests
```bash
clojure -X:test
```

### Run Unit Tests Only
```bash
clojure -X:test-unit
```

### Run Integration Tests Only
```bash
clojure -X:test-integration
```

## Quality Checklist

Before committing code, run:

```bash
# 1. Lint code
clojure -M:lint

# 2. Format code
clojure -M:format-fix

# 3. Run all tests
clojure -X:test
```

### Maintenance

```bash
# Check for outdated dependencies (run periodically)
clojure -M:outdated
```

## Project Structure

```
clogs/
├── src/
│   └── clogs/
│       ├── database/
│       │   ├── port.clj                    # Database port (interface)
│       │   └── adapters/
│       │       └── file.clj                # File-based database adapter
│       └── query/
│           ├── domain.clj                  # Pure domain logic
│           └── engine.clj                  # Query engine (ports & adapters)
├── test/
│   ├── unit/
│   │   └── clogs/
│   │       └── query/
│   │           └── domain_test.clj         # Unit tests
│   └── integration/
│       └── integration_test.clj            # Integration tests
├── .clj-kondo/
│   └── config.edn                          # Linting configuration
└── deps.edn                               # Dependencies and tool aliases
```

## Architecture

Clogs uses **Ports and Adapters** architecture:

- **Ports**: Interfaces/protocols (e.g., `DatabasePort`)
- **Adapters**: Implementations (e.g., `FileDatabase`, future `KafkaDatabase`)
- **Domain**: Pure business logic (query validation, filtering)
- **Engine**: Orchestrates domain + database interactions

This makes it easy to swap database implementations without changing business logic.