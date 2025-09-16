# Clogs Performance Improvement Plan

## Overview

This document outlines the comprehensive performance improvement plan for Clogs, focusing on implementing async pipelines with Prometheus metrics integration to achieve higher throughput, better observability, and system resilience.

## Current Architecture Analysis

### Current Data Flow
```
HTTP Request → Parse JSON/EDN → Validate → store-entry → Channel → Async Writer → File
     ↑              ↑            ↑          ↑           ↑          ↑          ↑
  Jetty Thread   Blocking    Blocking   go-block    go-loop   Blocking I/O  Sequential
```

### Identified Bottlenecks

1. **Request Thread Blocking**
   - JSON parsing: `(slurp body)` + `(json/read-str body)` blocks Jetty threads
   - Schema validation: `(s/validate domain/LogEntry entry)` blocks request threads
   - These operations consume precious HTTP threads (200 max configured)

2. **Suboptimal async/thread Usage**
   - File I/O uses `go-loop` but should use `async/thread` for blocking operations
   - CPU-intensive operations (parsing, validation) run on request threads

3. **Missing Observability**
   - No real-time metrics for queue depths, processing times, or bottlenecks
   - No visibility into system performance under load

4. **No Backpressure Handling**
   - Channels can fill up with no feedback to clients
   - No circuit breaker when system is overwhelmed

## Target Architecture

### Enhanced Async Pipeline
```
HTTP Request → [Async Pipeline]
              ↓
         Parse Pool (async/thread) → Parse Queue
              ↓
        Validate Pool (async/thread) → Validate Queue
              ↓
         Write Pool (async/thread) → Write Queue
              ↓
            File I/O
```

### Key Design Principles

1. **Non-blocking Request Threads**: HTTP threads only handle request/response, never block
2. **Proper async/thread Usage**: Use `thread` for blocking I/O, `go` for coordination
3. **Backpressure Handling**: Monitor queue depths and reject requests when overloaded
4. **Comprehensive Metrics**: Prometheus-compatible metrics for all pipeline stages
5. **Ordered Writes**: Maintain log entry ordering through single-writer architecture

## Implementation Plan

### Phase 1: Foundation (30 minutes)

#### 1.1 Add Prometheus Dependency
```clojure
;; deps.edn
{:deps {io.prometheus/simpleclient {:mvn/version "0.16.0"}
        io.prometheus/simpleclient_hotspot {:mvn/version "0.16.0"}
        io.prometheus/simpleclient_common {:mvn/version "0.16.0"}}}
```

#### 1.2 Create Prometheus Metrics Registry
- Counter metrics for requests, errors, and processing stages
- Histogram metrics for latency percentiles
- Gauge metrics for queue depths and system state

#### 1.3 Fix File Database Thread Usage
- Replace `go-loop` with `thread` for blocking file I/O operations
- Maintain existing channel-based batching logic

### Phase 2: Async Pipeline Implementation (1 hour)

#### 2.1 Create Processing Stages
- **Parse Stage**: JSON/EDN parsing using `async/thread` pool
- **Validate Stage**: Schema validation using `async/thread` pool
- **Write Stage**: File persistence using `async/thread` pool

#### 2.2 Channel Architecture
```clojure
parse-chan (buffer 100) → validate-chan (buffer 100) → write-chan (buffer 100)
```

#### 2.3 Worker Pool Configuration
- Parse workers: 4 (CPU-bound, match core count)
- Validate workers: 2 (fast validation, fewer workers needed)
- Write workers: 1 (maintain ordering, single writer)

### Phase 3: Prometheus Integration (30 minutes)

#### 3.1 Metrics Design
```clojure
;; Counters
clogs_requests_total{status="received|processed|failed"}
clogs_parse_total{result="success|error"}
clogs_validation_total{result="success|error"}
clogs_writes_total{result="success|error"}

;; Histograms (for latency percentiles)
clogs_request_duration_seconds{phase="parse|validate|write"}
clogs_file_write_duration_seconds

;; Gauges (for real-time state)
clogs_queue_depth{stage="parse|validate|write"}
clogs_active_requests
clogs_file_buffer_size
```

#### 3.2 Metrics Endpoint
- Add `/metrics` endpoint for Prometheus scraping
- Expose metrics in Prometheus text format
- Include JVM metrics (heap, GC, threads)

### Phase 4: Enhanced Resilience (30 minutes)

#### 4.1 Backpressure Handling
- Monitor channel queue depths in real-time
- Return HTTP 503 when queues exceed thresholds
- Include queue depth in error responses

#### 4.2 Circuit Breaker Logic
- Track error rates per pipeline stage
- Open circuit breaker when error threshold exceeded
- Implement half-open state for recovery testing

#### 4.3 Request Timeout Handling
- 5-second timeout for request processing
- Return HTTP 504 for timeouts
- Track timeout metrics

## Expected Performance Improvements

### Throughput Gains
- **2-3x throughput improvement** through non-blocking request handling
- **Reduced latency variance** through proper async/thread usage
- **Better resource utilization** by not blocking HTTP threads

### Latency Improvements
- **20-30% P99 latency reduction** by eliminating request thread blocking
- **More consistent response times** through backpressure handling
- **Faster error responses** when system is overloaded

### Observability Enhancements
- **Real-time performance metrics** through Prometheus integration
- **Pipeline stage visibility** for bottleneck identification
- **Error rate tracking** by stage and type
- **Queue depth monitoring** for capacity planning

## Monitoring and Alerting

### Prometheus Queries

```promql
# Request rate
rate(clogs_requests_total[5m])

# Error rate
rate(clogs_requests_total{status="failed"}[5m]) / rate(clogs_requests_total[5m])

# Latency percentiles
histogram_quantile(0.99, rate(clogs_request_duration_seconds_bucket[5m]))

# Queue depths
clogs_queue_depth

# Circuit breaker state
clogs_circuit_breaker_state
```

### Recommended Alerts

1. **High Error Rate**: Error rate > 5% for 2 minutes
2. **High Latency**: P99 latency > 100ms for 5 minutes
3. **Queue Backlog**: Any queue depth > 50 for 1 minute
4. **Circuit Breaker Open**: Any circuit breaker open for 30 seconds

## Grafana Dashboard Design

### Panels Configuration

1. **Request Overview**
   - Request rate (requests/second)
   - Error rate percentage
   - Success rate percentage

2. **Latency Analysis**
   - P50, P90, P95, P99 latency over time
   - Latency heatmap by hour
   - Pipeline stage latency breakdown

3. **Pipeline Health**
   - Queue depths by stage
   - Processing times by stage
   - Worker utilization

4. **System Resources**
   - JVM heap usage
   - GC frequency and duration
   - Thread pool utilization

5. **Error Tracking**
   - Error rate by type
   - Failed requests by stage
   - Circuit breaker state

## Testing Strategy

### Load Testing with k6

Update existing k6 tests to include:
- Prometheus metrics validation
- Backpressure scenario testing
- Circuit breaker trigger testing
- Recovery time measurement

### Metrics Validation

```javascript
// k6 test for metrics endpoint
export default function() {
  // Send requests to /logs
  let response = http.post('http://localhost:8080/logs', payload);

  // Validate metrics endpoint
  let metrics = http.get('http://localhost:8080/metrics');
  check(metrics, {
    'metrics endpoint responds': (r) => r.status === 200,
    'contains request counter': (r) => r.body.includes('clogs_requests_total'),
    'contains latency histogram': (r) => r.body.includes('clogs_request_duration_seconds'),
  });
}
```

## Implementation Timeline

| Phase | Duration | Deliverables |
|-------|----------|-------------|
| **Phase 1** | 30 min | Prometheus dependency, metrics registry, thread fix |
| **Phase 2** | 1 hour | Async pipeline, worker pools, channel architecture |
| **Phase 3** | 30 min | Metrics endpoint, Prometheus integration |
| **Phase 4** | 30 min | Backpressure, circuit breaker, timeouts |
| **Testing** | 1 hour | Load testing, metrics validation, performance comparison |

**Total Estimated Time: 3.5 hours**

## Success Criteria

### Performance Metrics
- [ ] Achieve 2x throughput improvement in k6 tests
- [ ] Reduce P99 latency by 20-30%
- [ ] Maintain 100% success rate under normal load
- [ ] Handle backpressure gracefully with HTTP 503 responses

### Observability Metrics
- [ ] All pipeline stages instrumented with Prometheus metrics
- [ ] Metrics endpoint responds in < 10ms
- [ ] Queue depth metrics update in real-time
- [ ] Error tracking by stage and type

### Reliability Metrics
- [ ] Circuit breaker triggers correctly under error conditions
- [ ] System recovers automatically when errors subside
- [ ] Request timeouts handled gracefully
- [ ] Log ordering maintained under all conditions

## Future Enhancements

### Advanced Features (Post-Implementation)
1. **Adaptive Backpressure**: Dynamic queue thresholds based on processing speed
2. **Auto-scaling Workers**: Automatically adjust worker pool sizes based on load
3. **Intelligent Routing**: Route requests based on content complexity
4. **Predictive Scaling**: Use metrics to predict load spikes

### Additional Metrics
1. **Business Metrics**: Log ingestion rate by source, log level distribution
2. **Capacity Metrics**: Disk usage rate, projected time to disk full
3. **Quality Metrics**: Parse success rate by content type, schema compliance

---

*This plan represents a comprehensive approach to improving Clogs performance while maintaining reliability and adding enterprise-grade observability capabilities.*