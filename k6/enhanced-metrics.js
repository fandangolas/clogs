import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Enhanced custom metrics
export let errorRate = new Rate('errors');
export let requestLatency = new Trend('request_latency', true);
export let throughputRate = new Rate('throughput_rate');
export let requestsPerSecond = new Counter('requests_per_second');

// Enhanced test configuration with comprehensive thresholds
export let options = {
  scenarios: {
    // Load testing with detailed metrics collection
    load_test: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '1m', target: 10 },   // Warm up
        { duration: '2m', target: 25 },   // Ramp up
        { duration: '3m', target: 50 },   // Peak load
        { duration: '2m', target: 75 },   // High load
        { duration: '1m', target: 25 },   // Ramp down
        { duration: '1m', target: 0 },    // Cool down
      ],
    },
  },

  // Comprehensive thresholds including p99 and p99.9
  thresholds: {
    // Overall HTTP metrics
    'http_req_duration': ['p(50)<100', 'p(90)<300', 'p(95)<500', 'p(99)<1000', 'p(99.9)<2000'],
    'http_req_failed': ['rate<0.01'], // < 1% error rate

    // Custom metrics
    'request_latency': ['p(50)<100', 'p(90)<300', 'p(95)<500', 'p(99)<1000', 'p(99.9)<2000'],
    'errors': ['rate<0.01'],

    // Performance targets
    'requests_per_second': ['count>5000'], // Minimum 5K total requests
  },

  // Detailed summary export
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)', 'count'],
};

// Performance tracking variables
let testStartTime;
let requestTimestamps = [];
let throughputData = [];

export function setup() {
  testStartTime = Date.now();
  console.log('🚀 Starting enhanced metrics collection...');
}

function generateRealisticLogEntry() {
  const levels = ['DEBUG', 'INFO', 'WARN', 'ERROR', 'TRACE'];
  const services = [
    'api-gateway', 'user-service', 'order-service', 'payment-service',
    'notification-service', 'inventory-service', 'auth-service', 'analytics-service'
  ];
  const operations = [
    'user-login', 'order-created', 'payment-processed', 'inventory-updated',
    'notification-sent', 'cache-invalidated', 'db-query-executed', 'api-call-made'
  ];

  const level = levels[Math.floor(Math.random() * levels.length)];
  const service = services[Math.floor(Math.random() * services.length)];
  const operation = operations[Math.floor(Math.random() * operations.length)];

  return {
    level,
    message: `${operation} - ${service} - ${Math.random().toString(36).substring(7)}`,
    timestamp: new Date().toISOString(),
    service,
    operation,
    requestId: `req-${Math.random().toString(36).substring(2, 10)}`,
    userId: Math.floor(Math.random() * 10000),
    duration: Math.floor(Math.random() * 500), // Processing time in ms
    traceId: `trace-${Math.random().toString(36).substring(2, 15)}`,
    metadata: {
      region: 'us-east-1',
      version: '1.2.3',
      environment: 'load-test'
    }
  };
}

export default function() {
  const iterationStart = Date.now();
  const logEntry = generateRealisticLogEntry();

  // Test ingestion with detailed timing
  const response = http.post('http://localhost:8080/logs', JSON.stringify(logEntry), {
    headers: { 'Content-Type': 'application/json' },
    tags: {
      operation: 'ingestion',
      service: logEntry.service,
      level: logEntry.level
    },
  });

  const responseTime = response.timings.duration;

  // Record detailed metrics
  requestLatency.add(responseTime);
  requestsPerSecond.add(1);

  // Track throughput correlation data
  requestTimestamps.push({
    timestamp: iterationStart,
    latency: responseTime,
    status: response.status
  });

  // Comprehensive checks
  const success = check(response, {
    'HTTP status is 201': (r) => r.status === 201,
    'Response time < 1s': (r) => r.timings.duration < 1000,
    'Response time < 500ms': (r) => r.timings.duration < 500,
    'Response time < 100ms': (r) => r.timings.duration < 100,
    'Has success message': (r) => r.body && r.body.includes('stored successfully'),
    'Content-Type is JSON': (r) => r.headers['Content-Type'] === 'application/json',
  }, {
    operation: 'ingestion',
    service: logEntry.service
  });

  if (!success) {
    errorRate.add(1);
  } else {
    throughputRate.add(1);
  }

  // Variable think time based on load
  const currentVUs = __ENV.K6_VUS || 1;
  const thinkTime = currentVUs > 50 ? 0.05 : 0.1; // Faster under high load
  sleep(Math.random() * thinkTime);
}

export function teardown(data) {
  console.log('📊 Test completed - processing metrics...');
}

export function handleSummary(data) {
  // Calculate additional throughput metrics
  const testDuration = (Date.now() - testStartTime) / 1000; // in seconds
  const totalRequests = data.metrics.http_reqs.values.count;
  const avgThroughput = totalRequests / testDuration;

  // Extract detailed latency percentiles
  const latencyMetrics = data.metrics.http_req_duration.values;
  const customLatencyMetrics = data.metrics.request_latency?.values || {};

  // Throughput vs Latency correlation data
  const correlationData = {
    timestamp: new Date().toISOString(),
    test_duration_seconds: testDuration,
    total_requests: totalRequests,
    average_throughput_rps: avgThroughput,

    // Comprehensive latency breakdown
    latency_percentiles: {
      p50: latencyMetrics['p(50)'] || 0,
      p90: latencyMetrics['p(90)'] || 0,
      p95: latencyMetrics['p(95)'] || 0,
      p99: latencyMetrics['p(99)'] || 0,
      p99_9: latencyMetrics['p(99.9)'] || 0,
      avg: latencyMetrics.avg || 0,
      min: latencyMetrics.min || 0,
      max: latencyMetrics.max || 0,
      med: latencyMetrics.med || 0
    },

    // Throughput analysis
    throughput_analysis: {
      peak_rps: avgThroughput, // This would need real-time calculation for true peak
      sustained_rps: avgThroughput * 0.9, // Estimate 90% of average as sustained
      error_rate_percent: (data.metrics.http_req_failed.values.rate * 100),
      success_rate_percent: ((1 - data.metrics.http_req_failed.values.rate) * 100)
    },

    // Performance classification
    performance_analysis: {
      latency_grade: classifyLatencyGrade(latencyMetrics['p(99)'] || 0),
      throughput_grade: classifyThroughputGrade(avgThroughput),
      overall_grade: calculateOverallGrade(latencyMetrics, avgThroughput, data.metrics.http_req_failed.values.rate)
    },

    // Threshold results
    thresholds_status: Object.fromEntries(
      Object.entries(data.thresholds).map(([key, value]) => [key, value.ok])
    ),
    all_thresholds_passed: Object.values(data.thresholds).every(t => t.ok)
  };

  // Console output with enhanced metrics
  const report = `
🎯 ENHANCED PERFORMANCE METRICS REPORT
=====================================

📊 LATENCY ANALYSIS
  P50 (median): ${latencyMetrics['p(50)']?.toFixed(2) || 0}ms
  P90:          ${latencyMetrics['p(90)']?.toFixed(2) || 0}ms
  P95:          ${latencyMetrics['p(95)']?.toFixed(2) || 0}ms
  P99:          ${latencyMetrics['p(99)']?.toFixed(2) || 0}ms  ⭐
  P99.9:        ${latencyMetrics['p(99.9)']?.toFixed(2) || 0}ms ⭐
  Average:      ${latencyMetrics.avg?.toFixed(2) || 0}ms
  Min:          ${latencyMetrics.min?.toFixed(2) || 0}ms
  Max:          ${latencyMetrics.max?.toFixed(2) || 0}ms

🚀 THROUGHPUT ANALYSIS
  Total Requests:    ${totalRequests.toLocaleString()}
  Test Duration:     ${testDuration.toFixed(2)}s
  Average RPS:       ${avgThroughput.toFixed(2)} req/s
  Peak Throughput:   ${(avgThroughput * 1.2).toFixed(2)} req/s (estimated)
  Success Rate:      ${correlationData.throughput_analysis.success_rate_percent.toFixed(2)}%

📈 PERFORMANCE GRADES
  Latency Grade:     ${correlationData.performance_analysis.latency_grade}
  Throughput Grade:  ${correlationData.performance_analysis.throughput_grade}
  Overall Grade:     ${correlationData.performance_analysis.overall_grade}

✅ THRESHOLD STATUS
  All Thresholds:    ${correlationData.all_thresholds_passed ? '✅ PASSED' : '❌ FAILED'}
  P99 < 1000ms:      ${(latencyMetrics['p(99)'] || 0) < 1000 ? '✅' : '❌'}
  P99.9 < 2000ms:    ${(latencyMetrics['p(99.9)'] || 0) < 2000 ? '✅' : '❌'}
  Error Rate < 1%:   ${correlationData.throughput_analysis.error_rate_percent < 1 ? '✅' : '❌'}

💡 PERFORMANCE INSIGHTS
${generatePerformanceInsights(correlationData)}
`;

  return {
    'k6/results/enhanced-metrics-summary.json': JSON.stringify(correlationData, null, 2),
    'k6/results/enhanced-metrics-full.json': JSON.stringify(data, null, 2),
    'stdout': report,
  };
}

// Helper functions for performance analysis
function classifyLatencyGrade(p99Latency) {
  if (p99Latency < 100) return 'A+ (Excellent)';
  if (p99Latency < 300) return 'A (Very Good)';
  if (p99Latency < 500) return 'B (Good)';
  if (p99Latency < 1000) return 'C (Acceptable)';
  if (p99Latency < 2000) return 'D (Poor)';
  return 'F (Unacceptable)';
}

function classifyThroughputGrade(rps) {
  if (rps > 5000) return 'A+ (High Performance)';
  if (rps > 2000) return 'A (Good Performance)';
  if (rps > 1000) return 'B (Moderate Performance)';
  if (rps > 500) return 'C (Basic Performance)';
  if (rps > 100) return 'D (Low Performance)';
  return 'F (Very Low Performance)';
}

function calculateOverallGrade(latencyMetrics, throughput, errorRate) {
  const p99 = latencyMetrics['p(99)'] || 0;
  const errorPercent = errorRate * 100;

  // Scoring system
  let score = 100;

  // Latency penalties
  if (p99 > 100) score -= 10;
  if (p99 > 300) score -= 20;
  if (p99 > 500) score -= 30;
  if (p99 > 1000) score -= 40;

  // Throughput penalties
  if (throughput < 100) score -= 40;
  if (throughput < 500) score -= 30;
  if (throughput < 1000) score -= 20;
  if (throughput < 2000) score -= 10;

  // Error rate penalties
  if (errorPercent > 0.1) score -= 10;
  if (errorPercent > 1) score -= 30;
  if (errorPercent > 5) score -= 50;

  if (score >= 90) return 'A+ (Outstanding)';
  if (score >= 80) return 'A (Excellent)';
  if (score >= 70) return 'B (Good)';
  if (score >= 60) return 'C (Acceptable)';
  if (score >= 50) return 'D (Poor)';
  return 'F (Unacceptable)';
}

function generatePerformanceInsights(data) {
  const insights = [];
  const latency = data.latency_percentiles;
  const throughput = data.throughput_analysis;

  // Latency insights
  if (latency.p99 > 1000) {
    insights.push('  ⚠️  P99 latency exceeds 1s - investigate slow requests');
  }
  if (latency.p99_9 > 2000) {
    insights.push('  ⚠️  P99.9 latency exceeds 2s - check for outliers');
  }
  if (latency.p99 / latency.p50 > 10) {
    insights.push('  📊 High latency variance detected - investigate consistency');
  }

  // Throughput insights
  if (throughput.average_throughput_rps > 1000) {
    insights.push('  🚀 Excellent throughput performance!');
  }
  if (throughput.error_rate_percent > 1) {
    insights.push('  ❌ Error rate too high - check system capacity');
  }
  if (throughput.success_rate_percent > 99.9) {
    insights.push('  ✨ Outstanding reliability!');
  }

  // Correlation insights
  if (latency.p99 < 500 && throughput.average_throughput_rps > 1000) {
    insights.push('  🎯 Optimal latency/throughput balance achieved');
  }

  return insights.length > 0 ? insights.join('\n') : '  📈 System performing within expected parameters';
}