# Performance Testing with k6

This document describes how to run stress tests against the Clogs logging service using k6.

## Overview

The stress testing suite includes:
- **Orchestration Script**: `run-stress-tests.sh` - manages service lifecycle and test execution
- **k6 Test Scripts**: Located in `k6/` directory
- **System Monitoring**: Optional resource usage tracking during tests

## Prerequisites

### Install k6

**macOS (Homebrew):**
```bash
brew install k6
```

**Ubuntu/Debian:**
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

**Other platforms:** See [k6 installation docs](https://k6.io/docs/getting-started/installation/)

## Quick Start

### Basic Mixed Workload Test
```bash
./run-stress-tests.sh
```

### Enhanced Metrics with Visualizations ⭐
```bash
./run-stress-tests.sh enhanced -v
```

### All Tests with Monitoring, Reporting, and Charts
```bash
./run-stress-tests.sh all -m -r -v
```

### Individual Tests
```bash
# Just ingestion load testing
./run-stress-tests.sh ingestion

# Just query performance testing
./run-stress-tests.sh query

# Mixed realistic workload
./run-stress-tests.sh mixed
```

## Test Scripts

### 1. Ingestion Test (`k6/ingestion-test.js`)

**Purpose**: Stress test the `/logs` endpoint with high-volume log ingestion.

**Load Profile**:
- Warm up: 10 VUs for 30s
- Ramp up: 50 VUs for 1m
- Peak: 100 VUs for 2m
- Ramp down: 50 VUs for 1m
- Cool down: 0 VUs for 30s

**Features**:
- Realistic log data generation
- 80% JSON, 20% EDN format testing
- Custom metrics for ingestion latency
- Response validation

**Thresholds**:
- P95 latency < 500ms
- Error rate < 1%

### 2. Query Test (`k6/query-test.js`)

**Purpose**: Test query performance under load.

**Load Profile**:
- Lighter load (max 30 VUs) as queries are more expensive
- Simulates dashboard users with think time

**Query Types**:
- Simple field filters
- Complex nested queries with AND/OR
- Field selection queries
- Multi-service queries

**Thresholds**:
- P95 latency < 1000ms
- Error rate < 2%

### 3. Enhanced Metrics Test (`k6/enhanced-metrics.js`) ⭐

**Purpose**: Comprehensive performance analysis with detailed metrics collection.

**Load Profile**:
- Progressive load: 1 → 75 VUs over 10 minutes
- Extended peak testing for statistical significance
- Detailed percentile collection (P50, P90, P95, P99, P99.9)

**Advanced Features**:
- **Comprehensive Percentiles**: P50, P90, P95, P99, P99.9 tracking
- **Throughput Correlation**: RPS vs latency relationship analysis
- **Performance Grading**: Automatic A-F grade assignment
- **Custom Metrics**: Request latency, throughput rate, error tracking
- **Realistic Data**: Complex log entries with metadata

**Enhanced Thresholds**:
- P50 < 100ms, P90 < 300ms, P95 < 500ms
- **P99 < 1000ms, P99.9 < 2000ms** ⭐
- Error rate < 1%
- Minimum 5000 total requests

**Output**:
- Detailed console report with performance grades
- JSON summary with correlation analysis
- Visualization-ready data export

### 4. Mixed Workload (`k6/mixed-workload.js`)

**Purpose**: Realistic production simulation with multiple scenarios.

**Scenarios**:
- **Ingestion**: Heavy continuous logging (100 VUs peak)
- **Querying**: Moderate dashboard usage (20 VUs peak)
- **Burst**: Sudden traffic spikes (200 req/s burst)

**Features**:
- Concurrent scenario execution
- Realistic user behavior patterns
- Comprehensive reporting
- Service-specific metrics

## Script Options

### `run-stress-tests.sh` Options

```bash
Usage: ./run-stress-tests.sh [OPTIONS] [TEST]

TESTS:
  ingestion     Run ingestion-focused load test
  query         Run query-focused load test
  mixed         Run mixed workload test (default)
  enhanced      Run enhanced metrics test with detailed analysis ⭐
  all           Run all tests sequentially

OPTIONS:
  -h, --help           Show help message
  -s, --skip-service   Don't start/stop service (assume it's running)
  -m, --monitor        Start system monitoring during tests
  -r, --report         Generate detailed report after tests
  -v, --visualize      Generate performance graphs and charts ⭐
```

## 📊 Performance Visualization

The enhanced testing suite includes comprehensive visualization capabilities:

### Visualization Features

**Automatic Chart Generation**:
- **Latency Distribution**: Percentile breakdown with performance tiers
- **Throughput Correlation**: RPS vs latency relationship graphs
- **Performance Dashboard**: Comprehensive summary with grades
- **Trend Analysis**: Historical performance comparison

### Installation Requirements

```bash
# Install Python dependencies for visualizations
pip install matplotlib seaborn pandas numpy
```

### Generated Charts

1. **`latency_distribution.png`**:
   - Latency percentile bar chart (P50, P90, P95, P99, P99.9)
   - Performance tier pie chart (Excellent/Good/Acceptable/Poor)
   - Threshold reference lines

2. **`throughput_correlation.png`**:
   - Throughput vs latency scatter plot
   - Performance grade analysis
   - Historical trend simulation
   - Current test point highlighting

3. **`performance_dashboard.png`**:
   - Comprehensive metrics summary table
   - Threshold compliance status
   - Performance grade breakdown
   - Automated insights and recommendations

### Usage Examples

```bash
# Generate visualizations for enhanced metrics test
./run-stress-tests.sh enhanced -v

# Create charts for any existing results
python scripts/visualize-performance.py k6/results/enhanced-metrics-summary.json

# All tests with complete analysis
./run-stress-tests.sh all -m -r -v
```

### Examples

**Run against existing service:**
```bash
# Start your service manually first
clojure -M:run &

# Then run tests without service management
./run-stress-tests.sh mixed -s
```

**Full test suite with monitoring:**
```bash
./run-stress-tests.sh all -m -r
```

**Quick ingestion test:**
```bash
./run-stress-tests.sh ingestion
```

## Results and Monitoring

### Output Locations

- **Test Results**: `k6/results/` directory
  - `*-summary.json`: k6 summary metrics
  - `*-raw.json`: Detailed request data
  - `stress-test-report.md`: Comprehensive report (with `-r` flag)

- **Service Logs**: `logs/service.log`

- **System Monitoring** (with `-m` flag):
  - `logs/iostat.log`: Disk I/O metrics
  - `logs/vmstat.log`: Memory and CPU metrics

### Key Metrics to Watch

**Enhanced Latency Percentiles** ⭐:
- **P50 (Median)**: Typical user experience
- **P90**: Good performance baseline
- **P95**: Standard SLA threshold
- **P99**: Outlier detection - critical for user satisfaction
- **P99.9**: Extreme outliers - important for system stability

**HTTP Performance**:
- **Requests/second**: Overall throughput
- **Error Rate**: Failed request percentage
- **Max VUs**: Peak concurrent users

**Performance Correlation**:
- **Throughput vs Latency**: RPS impact on response times
- **Performance Grades**: A-F automatic classification
- **Threshold Compliance**: Pass/fail against targets

**Ingestion Specific**:
- Target: P99 < 500ms, P99.9 < 1000ms, < 1% errors
- Watch for file I/O bottlenecks

**Query Specific**:
- Target: P99 < 1000ms, P99.9 < 2000ms, < 2% errors
- Monitor memory usage for large result sets

### Sample Enhanced Output

```
🎯 ENHANCED PERFORMANCE METRICS REPORT
=====================================

📊 LATENCY ANALYSIS
  P50 (median): 45.23ms
  P90:          78.45ms
  P95:          124.56ms
  P99:          287.89ms  ⭐
  P99.9:        456.78ms ⭐
  Average:      52.34ms
  Min:          12.45ms
  Max:          789.12ms

🚀 THROUGHPUT ANALYSIS
  Total Requests:    25,420
  Test Duration:     600.2s
  Average RPS:       42.3 req/s
  Peak Throughput:   50.8 req/s (estimated)
  Success Rate:      99.68%

📈 PERFORMANCE GRADES
  Latency Grade:     A (Very Good)
  Throughput Grade:  C (Basic Performance)
  Overall Grade:     B (Good)

✅ THRESHOLD STATUS
  All Thresholds:    ✅ PASSED
  P99 < 1000ms:      ✅
  P99.9 < 2000ms:    ✅
  Error Rate < 1%:   ✅

💡 PERFORMANCE INSIGHTS
  🚀 Excellent throughput performance!
  ✨ Outstanding reliability!
  🎯 Optimal latency/throughput balance achieved
```

## Performance Targets

Based on the roadmap targets:

| Metric | Target | Test Verification |
|--------|---------|------------------|
| Ingestion Rate | 10K logs/second | k6 ingestion test |
| Query Latency | <500ms simple, <2s complex | k6 query test |
| Error Rate | <1% under load | All test thresholds |
| Concurrent Users | 100+ VUs | Mixed workload test |

## Troubleshooting

### Common Issues

**Service won't start:**
```bash
# Check if port is already in use
lsof -i :8080

# View service logs
tail -f logs/service.log
```

**k6 not found:**
```bash
# Install k6 first
brew install k6  # macOS
# or follow installation docs
```

**Tests failing:**
```bash
# Run with verbose output
k6 run --verbose k6/ingestion-test.js

# Check service health manually
curl -v http://localhost:8080/logs
```

### Performance Tuning

**If ingestion is slow:**
- Check disk I/O in `logs/iostat.log`
- Monitor JVM garbage collection
- Consider file buffering optimizations

**If queries are slow:**
- Profile memory usage during file scanning
- Consider query result caching
- Monitor file size growth

**If service crashes:**
- Check JVM heap settings
- Review memory usage patterns
- Consider connection pooling

## Continuous Integration

### GitHub Actions Example

```yaml
name: Performance Tests

on:
  push:
    branches: [ main ]
  schedule:
    - cron: '0 2 * * 1'  # Weekly on Monday 2 AM

jobs:
  performance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Install k6
        run: |
          curl https://github.com/grafana/k6/releases/download/v0.47.0/k6-v0.47.0-linux-amd64.tar.gz -L | tar xvz --strip-components 1
          sudo cp k6 /usr/local/bin/

      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run Performance Tests
        run: ./run-stress-tests.sh mixed -r

      - name: Upload Results
        uses: actions/upload-artifact@v3
        with:
          name: performance-results
          path: k6/results/
```

## Advanced Usage

### Custom Test Development

To create custom tests, use the existing scripts as templates:

```javascript
import { check } from 'k6';
import http from 'k6/http';

export let options = {
  stages: [
    { duration: '1m', target: 10 },
    { duration: '2m', target: 20 },
    { duration: '1m', target: 0 },
  ],
};

export default function() {
  // Your custom test logic
  const response = http.get('http://localhost:8080/health');

  check(response, {
    'status is 200': (r) => r.status === 200,
  });
}
```

### Integration with Monitoring

For production monitoring, consider integrating with:
- **Grafana + InfluxDB**: Real-time dashboards
- **Prometheus**: Metrics collection
- **DataDog/New Relic**: APM integration

The k6 output formats support various monitoring backends:
```bash
k6 run --out influxdb=http://localhost:8086/k6 test.js
k6 run --out prometheus test.js
```

This comprehensive testing suite ensures Clogs can handle production-level loads while maintaining performance targets.