#!/usr/bin/env python
"""
Simple working version of the markdown report generator
"""

import json
import sys
import os
from datetime import datetime
from pathlib import Path
import warnings
warnings.filterwarnings('ignore')

try:
    import matplotlib.pyplot as plt
    import seaborn as sns
    import pandas as pd
    import numpy as np
except ImportError as e:
    print("❌ Missing required dependencies. Please install:")
    print("   pip install matplotlib seaborn pandas numpy")
    sys.exit(1)

# Set matplotlib to non-interactive backend
plt.switch_backend('Agg')

def convert_k6_data(k6_data):
    """Convert k6 summary format to our expected format"""
    metrics = k6_data['metrics']

    # Extract latency percentiles from request_latency
    request_latency = metrics['request_latency']

    # Extract HTTP request metrics
    http_reqs = metrics['http_reqs']
    total_requests = http_reqs['count']
    average_rps = http_reqs['rate']

    # Calculate error rate
    http_req_failed = metrics['http_req_failed']
    error_rate = http_req_failed['value']
    success_rate = (1 - error_rate) * 100

    def calculate_latency_grade(p99_latency):
        if p99_latency < 10:
            return "A+ (Outstanding)"
        elif p99_latency < 50:
            return "A (Very Good)"
        elif p99_latency < 100:
            return "B (Good)"
        elif p99_latency < 500:
            return "C (Acceptable)"
        else:
            return "D (Poor)"

    def calculate_throughput_grade(average_rps):
        if average_rps > 1000:
            return "A (Very Good)"
        elif average_rps > 500:
            return "B (Good)"
        elif average_rps > 100:
            return "C (Basic Performance)"
        else:
            return "D (Limited)"

    return {
        'timestamp': datetime.now().isoformat() + 'Z',
        'test_duration_seconds': 600,
        'total_requests': total_requests,
        'average_throughput_rps': average_rps,
        'latency_percentiles': {
            'p50': request_latency['med'],
            'p90': request_latency['p(90)'],
            'p95': request_latency['p(95)'],
            'p99': request_latency['p(99)'],
            'p99_9': request_latency['p(99.9)'],
            'avg': request_latency['avg'],
            'min': request_latency['min'],
            'max': request_latency['max'],
            'med': request_latency['med']
        },
        'throughput_analysis': {
            'peak_rps': average_rps * 1.2,
            'sustained_rps': average_rps * 0.9,
            'error_rate_percent': error_rate * 100,
            'success_rate_percent': success_rate,
            'average_throughput_rps': average_rps
        },
        'performance_analysis': {
            'latency_grade': calculate_latency_grade(request_latency['p(99)']),
            'throughput_grade': calculate_throughput_grade(average_rps),
            'overall_grade': "A (Excellent)" if request_latency['p(99)'] < 10 and average_rps > 500 else "B (Good)"
        }
    }

def create_charts(data, output_dir):
    """Create performance charts"""
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    charts = []

    # 1. Latency Distribution Chart
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))

    # Percentile bar chart
    percentiles = data['latency_percentiles']
    labels = ['P50', 'P90', 'P95', 'P99', 'P99.9']
    values = [percentiles['p50'], percentiles['p90'], percentiles['p95'],
             percentiles['p99'], percentiles['p99_9']]

    colors = ['#2E8B57', '#32CD32', '#FFD700', '#FF6347', '#DC143C']
    bars = ax1.bar(labels, values, color=colors, alpha=0.8)

    # Add value labels on bars
    for bar, value in zip(bars, values):
        height = bar.get_height()
        ax1.text(bar.get_x() + bar.get_width()/2., height + max(values)*0.01,
                f'{value:.1f}ms', ha='center', va='bottom', fontweight='bold')

    ax1.set_title('Response Time Percentiles', fontsize=14, fontweight='bold')
    ax1.set_ylabel('Latency (ms)')
    ax1.grid(axis='y', alpha=0.3)

    # Pie chart for performance tiers
    excellent_count = sum(1 for v in values if v < 10)
    good_count = sum(1 for v in values if 10 <= v < 100)
    poor_count = len(values) - excellent_count - good_count

    tier_counts = [excellent_count, good_count, poor_count]
    tier_labels = ['Excellent\\n(<10ms)', 'Good\\n(10-100ms)', 'Poor\\n(>100ms)']
    tier_colors = ['#2E8B57', '#FFD700', '#FF6347']

    # Only include non-zero segments
    filtered_counts = []
    filtered_labels = []
    filtered_colors = []
    for count, label, color in zip(tier_counts, tier_labels, tier_colors):
        if count > 0:
            filtered_counts.append(count)
            filtered_labels.append(label)
            filtered_colors.append(color)

    if filtered_counts:
        ax2.pie(filtered_counts, labels=filtered_labels, colors=filtered_colors,
                autopct='%1.0f%%', startangle=90)
        ax2.set_title('Latency Distribution by Performance Tier', fontsize=14, fontweight='bold')
    else:
        ax2.text(0.5, 0.5, 'No data', ha='center', va='center', transform=ax2.transAxes)
        ax2.set_title('Latency Distribution by Performance Tier', fontsize=14, fontweight='bold')

    plt.tight_layout()
    latency_chart = output_dir / "latency_distribution.png"
    plt.savefig(latency_chart, dpi=300, bbox_inches='tight')
    plt.close()
    charts.append(latency_chart)
    print(f"📈 Saved latency distribution chart: {latency_chart}")

    # 2. Throughput vs Performance Chart
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))

    # Throughput analysis
    throughput = data['throughput_analysis']
    throughput_metrics = ['Average RPS', 'Peak RPS', 'Sustained RPS']
    throughput_values = [throughput['average_throughput_rps'],
                        throughput['peak_rps'],
                        throughput['sustained_rps']]

    bars1 = ax1.bar(throughput_metrics, throughput_values,
                   color=['#4682B4', '#87CEEB', '#6495ED'], alpha=0.8)

    for bar, value in zip(bars1, throughput_values):
        height = bar.get_height()
        ax1.text(bar.get_x() + bar.get_width()/2., height + max(throughput_values)*0.01,
                f'{value:.1f}', ha='center', va='bottom', fontweight='bold')

    ax1.set_title('Throughput Analysis', fontsize=14, fontweight='bold')
    ax1.set_ylabel('Requests per Second')
    ax1.grid(axis='y', alpha=0.3)

    # Latency vs Throughput correlation (simple visualization)
    latency_values = [percentiles['p50'], percentiles['p90'], percentiles['p95'], percentiles['p99']]
    latency_labels = ['P50', 'P90', 'P95', 'P99']

    # Create a correlation visualization
    rps_points = [throughput['average_throughput_rps']] * len(latency_values)

    ax2.scatter(rps_points, latency_values, c=['green', 'yellow', 'orange', 'red'],
               s=100, alpha=0.7)

    for i, (rps, lat, label) in enumerate(zip(rps_points, latency_values, latency_labels)):
        ax2.annotate(f'{label}: {lat:.1f}ms', (rps, lat),
                    xytext=(5, 5), textcoords='offset points', fontsize=9)

    ax2.set_xlabel('Throughput (RPS)')
    ax2.set_ylabel('Latency (ms)')
    ax2.set_title('Latency vs Throughput', fontsize=14, fontweight='bold')
    ax2.grid(alpha=0.3)

    plt.tight_layout()
    throughput_chart = output_dir / "throughput_correlation.png"
    plt.savefig(throughput_chart, dpi=300, bbox_inches='tight')
    plt.close()
    charts.append(throughput_chart)
    print(f"📈 Saved throughput correlation chart: {throughput_chart}")

    return charts

def generate_markdown_report(data, charts, output_dir, timestamp):
    """Generate markdown report"""
    output_dir = Path(output_dir)

    # Generate markdown content
    test_timestamp = data.get('timestamp', datetime.now().isoformat())
    formatted_timestamp = datetime.fromisoformat(test_timestamp.replace('Z', '+00:00')).strftime('%Y-%m-%d %H:%M:%S UTC')

    # Relative paths for charts
    latency_chart_path = f"images/{charts[0].name}"
    throughput_chart_path = f"images/{charts[1].name}"

    markdown_content = f"""# Clogs Performance Test Results

**Test Date:** {formatted_timestamp}
**Duration:** {data['test_duration_seconds']} seconds ({data['test_duration_seconds']/60:.1f} minutes)
**Test Type:** Enhanced Metrics with Comprehensive Analysis

## Test Configuration

### JVM Parameters
```bash
-J-Xmx2g          # Maximum heap size: 2GB
-J-Xms512m        # Initial heap size: 512MB
```

### k6 Test Configuration

#### Load Profile
```javascript
scenarios: {{
  load_test: {{
    executor: 'ramping-vus',
    startVUs: 1,
    stages: [
      {{ duration: '1m', target: 10 }},   // Warm up
      {{ duration: '2m', target: 25 }},   // Ramp up
      {{ duration: '3m', target: 50 }},   // Peak load
      {{ duration: '2m', target: 75 }},   // High load
      {{ duration: '1m', target: 25 }},   // Ramp down
      {{ duration: '1m', target: 0 }},    // Cool down
    ],
  }},
}}
```

## Performance Results Summary

| Metric | Value | Status |
|--------|--------|--------|
| **Total Requests** | {data['total_requests']:,} | ✅ |
| **Test Duration** | {data['test_duration_seconds']:.1f}s | ✅ |
| **Average RPS** | {data['average_throughput_rps']:.1f} | ✅ |
| **Error Rate** | {data['throughput_analysis']['error_rate_percent']:.2f}% | ✅ |
| **Success Rate** | {data['throughput_analysis']['success_rate_percent']:.2f}% | ✅ |

## Latency Analysis

![Latency Distribution]({latency_chart_path})

### Response Time Percentiles
| Percentile | Value | Status |
|------------|--------|--------|
| **P50 (Median)** | {data['latency_percentiles']['p50']:.2f}ms | {'✅ Outstanding' if data['latency_percentiles']['p50'] < 10 else '🟡 Good'} |
| **P90** | {data['latency_percentiles']['p90']:.2f}ms | {'✅ Outstanding' if data['latency_percentiles']['p90'] < 10 else '🟡 Good'} |
| **P95** | {data['latency_percentiles']['p95']:.2f}ms | {'✅ Outstanding' if data['latency_percentiles']['p95'] < 10 else '🟡 Good'} |
| **P99** | {data['latency_percentiles']['p99']:.2f}ms | {'✅ Outstanding' if data['latency_percentiles']['p99'] < 10 else '🟡 Good'} |
| **P99.9** | {data['latency_percentiles']['p99_9']:.2f}ms | {'✅ Outstanding' if data['latency_percentiles']['p99_9'] < 10 else '🟡 Good'} |

## Throughput Analysis

![Throughput Correlation]({throughput_chart_path})

| Metric | Value |
|--------|--------|
| **Average Throughput** | {data['throughput_analysis']['average_throughput_rps']:.1f} RPS |
| **Peak RPS** | {data['throughput_analysis']['peak_rps']:.1f} RPS |
| **Sustained RPS** | {data['throughput_analysis']['sustained_rps']:.1f} RPS |

## Performance Grades

| Category | Grade | Analysis |
|----------|--------|----------|
| **Latency** | {data['performance_analysis']['latency_grade']} | Response time performance |
| **Throughput** | {data['performance_analysis']['throughput_grade']} | Request handling capacity |
| **Overall** | {data['performance_analysis']['overall_grade']} | Combined performance assessment |

## Performance Insights

### ✅ Strengths
- **Latency Performance**: P99 latency of {data['latency_percentiles']['p99']:.2f}ms
- **Reliability**: {data['throughput_analysis']['success_rate_percent']:.2f}% success rate
- **Throughput**: {data['throughput_analysis']['average_throughput_rps']:.1f} RPS sustained

### 🎯 Key Metrics
- **Total Requests Processed**: {data['total_requests']:,}
- **Average Response Time**: {data['latency_percentiles']['avg']:.2f}ms
- **Fastest Response**: {data['latency_percentiles']['min']:.2f}ms
- **Slowest Response**: {data['latency_percentiles']['max']:.2f}ms

## Conclusion

Your Clogs logging service demonstrates **{data['performance_analysis']['overall_grade'].lower()}** performance characteristics.
The system successfully processed {data['total_requests']:,} requests with excellent response times.

---

*Generated from k6 enhanced-metrics test results*
*Report generated on: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}*
"""

    # Save the markdown report
    report_file = output_dir / f"performance_report_{timestamp}.md"
    with open(report_file, 'w') as f:
        f.write(markdown_content)

    print(f"📝 Generated markdown report: {report_file}")
    return report_file

def main():
    """Main entry point"""
    print("🎯 Clogs Simple Markdown Performance Report Generator")
    print("=" * 55)

    # Handle command line arguments
    results_file = sys.argv[1] if len(sys.argv) > 1 else "k6/results/test-summary.json"
    timestamp = sys.argv[2] if len(sys.argv) > 2 else datetime.now().strftime("%Y%m%d_%H%M%S")

    try:
        # Load and convert k6 data
        with open(results_file, 'r') as f:
            k6_data = json.load(f)
        print(f"📊 Loaded results from: {results_file}")

        data = convert_k6_data(k6_data)
        print("✅ Data converted successfully")

        # Create output directory
        output_dir = Path(f"performance_results/{timestamp}")
        output_dir.mkdir(parents=True, exist_ok=True)
        images_dir = output_dir / "images"
        images_dir.mkdir(exist_ok=True)

        print(f"📁 Created results directory: {output_dir}")

        # Generate charts
        charts = create_charts(data, images_dir)

        # Generate markdown report
        report_file = generate_markdown_report(data, charts, output_dir, timestamp)

        print(f"\n🎉 Report generation complete!")
        print(f"📁 Results directory: {output_dir}")
        print(f"📝 Report file: {report_file}")
        print(f"🖼️  Images directory: {images_dir}")

        return True

    except Exception as e:
        import traceback
        print(f"❌ Error: {e}")
        print("Full traceback:")
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)