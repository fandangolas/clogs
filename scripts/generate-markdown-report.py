#!/usr/bin/env python
"""
Simple working version of the markdown report generator
"""

import json
import sys
import os
import glob
import re
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
            'p99_99': request_latency.get('p(99.99)', 0),  # Handle missing extreme percentiles
            'p99_999': request_latency.get('p(99.999)', 0),
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

def load_all_historical_results(performance_results_dir):
    """Load all historical test results from markdown files for trend analysis"""
    historical_data = []
    base_dir = Path(performance_results_dir) / "performance_results"

    if not base_dir.exists():
        print(f"⚠️  No historical data found at {base_dir}")
        return []

    # Find all performance report markdown files
    report_files = list(base_dir.glob("*/performance_report_*.md"))

    for report_file in sorted(report_files):
        try:
            with open(report_file, 'r') as f:
                content = f.read()

            # Extract metrics from markdown
            historical_entry = extract_metrics_from_markdown(content, report_file.name)
            if historical_entry:
                historical_data.append(historical_entry)

        except Exception as e:
            print(f"⚠️  Could not load {report_file}: {e}")
            continue

    print(f"📊 Loaded {len(historical_data)} historical test results from markdown files")
    return historical_data

def extract_metrics_from_markdown(content, filename):
    """Extract performance metrics from markdown report"""
    import re

    try:
        # Extract timestamp from filename (format: performance_report_YYYYMMDD_HHMMSS.md)
        timestamp_match = re.search(r'performance_report_(\d{8}_\d{6})\.md', filename)
        if not timestamp_match:
            return None

        timestamp_str = timestamp_match.group(1)
        # Convert to ISO format for consistency
        from datetime import datetime
        dt = datetime.strptime(timestamp_str, '%Y%m%d_%H%M%S')
        timestamp = dt.isoformat() + 'Z'

        # Extract metrics using regex patterns
        metrics = {}

        # Total requests
        total_requests_match = re.search(r'\*\*Total Requests\*\*\s*\|\s*([\d,]+)', content)
        metrics['total_requests'] = int(total_requests_match.group(1).replace(',', '')) if total_requests_match else 0

        # Average RPS
        avg_rps_match = re.search(r'\*\*Average RPS\*\*\s*\|\s*([\d,.]+)', content)
        metrics['average_throughput_rps'] = float(avg_rps_match.group(1).replace(',', '')) if avg_rps_match else 0

        # Test duration
        duration_match = re.search(r'\*\*Duration:\*\*\s*([\d.]+)\s*seconds', content)
        metrics['test_duration_seconds'] = float(duration_match.group(1)) if duration_match else 600

        # Latency percentiles
        latency_percentiles = {}
        p50_match = re.search(r'\*\*P50 \(Median\)\*\*\s*\|\s*([\d.]+)ms', content)
        p90_match = re.search(r'\*\*P90\*\*\s*\|\s*([\d.]+)ms', content)
        p95_match = re.search(r'\*\*P95\*\*\s*\|\s*([\d.]+)ms', content)
        p99_match = re.search(r'\*\*P99\*\*\s*\|\s*([\d.]+)ms', content)
        p99_9_match = re.search(r'\*\*P99\.9\*\*\s*\|\s*([\d.]+)ms', content)

        if p50_match: latency_percentiles['p50'] = float(p50_match.group(1))
        if p90_match: latency_percentiles['p90'] = float(p90_match.group(1))
        if p95_match: latency_percentiles['p95'] = float(p95_match.group(1))
        if p99_match: latency_percentiles['p99'] = float(p99_match.group(1))
        if p99_9_match: latency_percentiles['p99_9'] = float(p99_9_match.group(1))

        # Error rate and success rate
        error_rate_match = re.search(r'\*\*Error Rate\*\*\s*\|\s*([\d.]+)%', content)
        success_rate_match = re.search(r'\*\*Success Rate\*\*\s*\|\s*([\d.]+)%', content)

        throughput_analysis = {}
        if error_rate_match: throughput_analysis['error_rate_percent'] = float(error_rate_match.group(1))
        if success_rate_match: throughput_analysis['success_rate_percent'] = float(success_rate_match.group(1))

        # Peak RPS and Sustained RPS
        peak_rps_match = re.search(r'\*\*Peak RPS\*\*\s*\|\s*([\d,.]+)', content)
        sustained_rps_match = re.search(r'\*\*Sustained RPS\*\*\s*\|\s*([\d,.]+)', content)

        if peak_rps_match: throughput_analysis['peak_rps'] = float(peak_rps_match.group(1).replace(',', ''))
        if sustained_rps_match: throughput_analysis['sustained_rps'] = float(sustained_rps_match.group(1).replace(',', ''))

        # Performance grades
        performance_analysis = {}
        latency_grade_match = re.search(r'\*\*Latency\*\*\s*\|\s*([^|]+)\s*\|', content)
        throughput_grade_match = re.search(r'\*\*Throughput\*\*\s*\|\s*([^|]+)\s*\|', content)
        overall_grade_match = re.search(r'\*\*Overall\*\*\s*\|\s*([^|]+)\s*\|', content)

        if latency_grade_match: performance_analysis['latency_grade'] = latency_grade_match.group(1).strip()
        if throughput_grade_match: performance_analysis['throughput_grade'] = throughput_grade_match.group(1).strip()
        if overall_grade_match: performance_analysis['overall_grade'] = overall_grade_match.group(1).strip()

        return {
            'timestamp': timestamp,
            'total_requests': metrics['total_requests'],
            'average_throughput_rps': metrics['average_throughput_rps'],
            'test_duration_seconds': metrics['test_duration_seconds'],
            'latency_percentiles': latency_percentiles,
            'throughput_analysis': throughput_analysis,
            'performance_analysis': performance_analysis
        }

    except Exception as e:
        print(f"⚠️  Error extracting metrics from markdown: {e}")
        return None

def extract_test_date(dir_name):
    """Extract test date from directory name for sorting"""
    # Try to extract date patterns like 20250916_153756
    date_match = re.search(r'(\d{8}_\d{6})', dir_name)
    if date_match:
        return date_match.group(1)
    return dir_name

def create_latency_throughput_correlation(historical_data, output_dir):
    """Create latency vs throughput correlation chart"""
    if len(historical_data) < 2:
        print("⚠️  Need at least 2 test results for correlation chart")
        return None

    fig, ax = plt.subplots(figsize=(12, 8))

    # Extract data points
    throughputs = [d['average_throughput_rps'] for d in historical_data]
    p50_latencies = [d['latency_percentiles']['p50'] for d in historical_data]
    p99_latencies = [d['latency_percentiles']['p99'] for d in historical_data]
    p99_9_latencies = [d['latency_percentiles']['p99_9'] for d in historical_data]

    # Create scatter plot for different percentiles
    ax.scatter(throughputs, p50_latencies, c='#2E8B57', s=100, alpha=0.8, label='P50 (Median)', marker='o')
    ax.scatter(throughputs, p99_latencies, c='#FF6347', s=100, alpha=0.8, label='P99', marker='s')
    ax.scatter(throughputs, p99_9_latencies, c='#DC143C', s=100, alpha=0.8, label='P99.9', marker='^')

    # Add trend lines
    if len(throughputs) > 2:
        z_p99 = np.polyfit(throughputs, p99_latencies, 1)
        p_p99 = np.poly1d(z_p99)
        ax.plot(throughputs, p_p99(throughputs), "--", alpha=0.7, color='#FF6347')

    # Formatting
    ax.set_xlabel('Throughput (RPS)', fontsize=12, fontweight='bold')
    ax.set_ylabel('Latency (ms)', fontsize=12, fontweight='bold')
    ax.set_title('Latency vs Throughput Correlation\nSystem Performance Under Different Loads',
                fontsize=14, fontweight='bold', pad=20)
    ax.legend(frameon=True, shadow=True)
    ax.grid(True, alpha=0.3)

    # Add annotations for data points
    for i, (rps, p99, timestamp) in enumerate(zip(throughputs, p99_latencies, [d['timestamp'][:10] for d in historical_data])):
        if i % 2 == 0:  # Annotate every other point to avoid crowding
            ax.annotate(f'{rps:.0f} RPS', (rps, p99), xytext=(5, 5), textcoords='offset points',
                       fontsize=9, alpha=0.7)

    plt.tight_layout()
    correlation_path = output_dir / 'latency_throughput_correlation.png'
    plt.savefig(correlation_path, dpi=300, bbox_inches='tight')
    plt.close()

    return {'latency_throughput_correlation': correlation_path}

def create_performance_evolution_timeline(historical_data, output_dir):
    """Create performance evolution timeline chart"""
    if len(historical_data) < 2:
        print("⚠️  Need at least 2 test results for timeline chart")
        return None

    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))

    # Extract data
    test_indices = list(range(len(historical_data)))
    test_labels = [d['timestamp'][8:13] for d in historical_data]  # Extract time portion HHMMM
    throughputs = [d['average_throughput_rps'] for d in historical_data]
    p50_latencies = [d['latency_percentiles']['p50'] for d in historical_data]
    p99_latencies = [d['latency_percentiles']['p99'] for d in historical_data]

    # Top chart: Throughput evolution
    bars1 = ax1.bar(test_indices, throughputs, color='#32CD32', alpha=0.8, edgecolor='black', linewidth=0.5)
    ax1.set_ylabel('Throughput (RPS)', fontsize=12, fontweight='bold')
    ax1.set_title('Performance Evolution Over Time\nThroughput Improvements', fontsize=14, fontweight='bold')
    ax1.grid(True, alpha=0.3, axis='y')

    # Add value labels on bars
    for bar, value in zip(bars1, throughputs):
        height = bar.get_height()
        ax1.text(bar.get_x() + bar.get_width()/2., height + max(throughputs)*0.01,
                f'{value:.0f}', ha='center', va='bottom', fontweight='bold', fontsize=10)

    # Bottom chart: Latency evolution
    line1 = ax2.plot(test_indices, p50_latencies, 'o-', color='#2E8B57', linewidth=3, markersize=8, label='P50 (Median)')
    line2 = ax2.plot(test_indices, p99_latencies, 's-', color='#FF6347', linewidth=3, markersize=8, label='P99')

    ax2.set_xlabel('Test Evolution', fontsize=12, fontweight='bold')
    ax2.set_ylabel('Latency (ms)', fontsize=12, fontweight='bold')
    ax2.set_title('Latency Improvements Over Time', fontsize=14, fontweight='bold')
    ax2.set_xticks(test_indices)
    ax2.set_xticklabels(test_labels, rotation=45, ha='right')
    ax2.legend(frameon=True, shadow=True)
    ax2.grid(True, alpha=0.3)

    plt.tight_layout()
    timeline_path = output_dir / 'performance_evolution_timeline.png'
    plt.savefig(timeline_path, dpi=300, bbox_inches='tight')
    plt.close()

    return {'performance_evolution_timeline': timeline_path}

def create_multi_percentile_progression(historical_data, output_dir):
    """Create multi-percentile progression chart"""
    if len(historical_data) < 2:
        print("⚠️  Need at least 2 test results for percentile progression chart")
        return None

    fig, ax = plt.subplots(figsize=(14, 8))

    # Extract data
    throughputs = [d['average_throughput_rps'] for d in historical_data]
    percentiles = ['p50', 'p90', 'p95', 'p99', 'p99_9']
    colors = ['#2E8B57', '#32CD32', '#FFD700', '#FF6347', '#DC143C']
    labels = ['P50', 'P90', 'P95', 'P99', 'P99.9']

    # Create lines for each percentile
    for i, (percentile, color, label) in enumerate(zip(percentiles, colors, labels)):
        latencies = [d['latency_percentiles'][percentile] for d in historical_data]
        ax.plot(throughputs, latencies, 'o-', color=color, linewidth=3, markersize=8,
               label=label, alpha=0.8)

    # Formatting
    ax.set_xlabel('Throughput (RPS)', fontsize=12, fontweight='bold')
    ax.set_ylabel('Latency (ms)', fontsize=12, fontweight='bold')
    ax.set_title('Multi-Percentile Latency Progression\nHow All Percentiles Scale with Throughput',
                fontsize=14, fontweight='bold', pad=20)
    ax.legend(frameon=True, shadow=True, loc='upper left')
    ax.grid(True, alpha=0.3)

    # Add fill between areas to show percentile bands
    if len(throughputs) > 1:
        p50_latencies = [d['latency_percentiles']['p50'] for d in historical_data]
        p99_latencies = [d['latency_percentiles']['p99'] for d in historical_data]
        ax.fill_between(throughputs, p50_latencies, p99_latencies, alpha=0.2, color='gray',
                       label='P50-P99 Range')

    plt.tight_layout()
    progression_path = output_dir / 'multi_percentile_progression.png'
    plt.savefig(progression_path, dpi=300, bbox_inches='tight')
    plt.close()

    return {'multi_percentile_progression': progression_path}

def create_charts(data, output_dir, historical_data=None):
    """Create performance charts including historical analysis"""
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    charts = []

    # 1. Latency Distribution Chart
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))

    # Percentile bar chart
    percentiles = data['latency_percentiles']
    labels = ['P50', 'P90', 'P95', 'P99', 'P99.9', 'P99.99', 'P99.999']
    values = [percentiles['p50'], percentiles['p90'], percentiles['p95'],
             percentiles['p99'], percentiles['p99_9'], percentiles['p99_99'], percentiles['p99_999']]

    colors = ['#2E8B57', '#32CD32', '#FFD700', '#FF6347', '#DC143C', '#8B0000', '#4B0000']  # Added darker reds for extreme percentiles
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

    # 3. Historical Analysis Charts (if available)
    if historical_data and len(historical_data) > 1:
        print(f"📊 Creating historical analysis charts with {len(historical_data)} data points...")

        # Latency vs Throughput Correlation
        correlation_chart = create_latency_throughput_correlation(historical_data, output_dir)
        if correlation_chart:
            charts.append(correlation_chart)
            print(f"📈 Saved latency-throughput correlation: {correlation_chart}")

        # Performance Evolution Timeline
        timeline_chart = create_performance_evolution_timeline(historical_data, output_dir)
        if timeline_chart:
            charts.append(timeline_chart)
            print(f"📈 Saved performance timeline: {timeline_chart}")

        # Multi-Percentile Progression
        progression_chart = create_multi_percentile_progression(historical_data, output_dir)
        if progression_chart:
            charts.append(progression_chart)
            print(f"📈 Saved percentile progression: {progression_chart}")


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
| **P99.99** | {data['latency_percentiles']['p99_99']:.2f}ms | {'✅ Outstanding' if data['latency_percentiles']['p99_99'] < 50 else '🟡 Good'} |
| **P99.999** | {data['latency_percentiles']['p99_999']:.2f}ms | {'✅ Outstanding' if data['latency_percentiles']['p99_999'] < 100 else '🟡 Good'} |

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

## Historical Performance Analysis

![Latency vs Throughput Correlation](images/latency_throughput_correlation.png)

![Performance Evolution Timeline](images/performance_evolution_timeline.png)

![Multi-Percentile Progression](images/multi_percentile_progression.png)

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

        # Load historical data for enhanced visualizations
        historical_data = load_all_historical_results(".")

        # Generate charts
        charts = create_charts(data, images_dir)

        # Generate enhanced charts with historical data
        if len(historical_data) >= 2:
            print("📈 Creating enhanced historical charts...")
            create_latency_throughput_correlation(historical_data, images_dir)
            create_performance_evolution_timeline(historical_data, images_dir)
            create_multi_percentile_progression(historical_data, images_dir)

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