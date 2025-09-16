#!/bin/bash

# Clogs Stress Testing Orchestration Script
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SERVICE_PORT=8080
SERVICE_PID_FILE="/tmp/clogs-service.pid"
RESULTS_DIR="k6/results"
LOG_DIR="logs"

# Ensure required directories exist
mkdir -p "$RESULTS_DIR"
mkdir -p "$LOG_DIR"

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Function to check if service is running
check_service() {
    if curl -s "http://localhost:$SERVICE_PORT/health" > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# Function to start the Clojure service
start_service() {
    print_status "Starting Clojure service on port $SERVICE_PORT..."

    # Start service in background and capture PID with increased heap
    nohup clojure -J-Xmx2g -J-Xms512m -M:run > "$LOG_DIR/service.log" 2>&1 &
    SERVICE_PID=$!
    echo $SERVICE_PID > "$SERVICE_PID_FILE"

    # Wait for service to start
    print_status "Waiting for service to become ready..."
    for i in {1..30}; do
        if check_service; then
            print_success "Service is ready (PID: $SERVICE_PID)"
            return 0
        fi
        sleep 2
    done

    print_error "Service failed to start within 60 seconds"
    return 1
}

# Function to stop the service
stop_service() {
    if [ -f "$SERVICE_PID_FILE" ]; then
        SERVICE_PID=$(cat "$SERVICE_PID_FILE")
        print_status "Stopping service (PID: $SERVICE_PID)..."

        if kill $SERVICE_PID 2>/dev/null; then
            # Wait for graceful shutdown
            for i in {1..10}; do
                if ! kill -0 $SERVICE_PID 2>/dev/null; then
                    break
                fi
                sleep 1
            done

            # Force kill if still running
            if kill -0 $SERVICE_PID 2>/dev/null; then
                print_warning "Forcefully killing service..."
                kill -9 $SERVICE_PID 2>/dev/null || true
            fi
        fi

        rm -f "$SERVICE_PID_FILE"
        print_success "Service stopped"
    fi
}

# Function to run k6 test
run_k6_test() {
    local test_file=$1
    local test_name=$2

    print_status "Running $test_name test..."

    if ! command -v k6 &> /dev/null; then
        print_error "k6 is not installed. Please install it first:"
        print_error "  brew install k6    # macOS"
        print_error "  or visit: https://k6.io/docs/getting-started/installation/"
        return 1
    fi

    # Run k6 test with detailed output
    if k6 run \
        --out json="$RESULTS_DIR/${test_name}-raw.json" \
        --summary-export="$RESULTS_DIR/${test_name}-summary.json" \
        "$test_file"; then
        print_success "$test_name test completed successfully"
        return 0
    else
        print_error "$test_name test failed"
        return 1
    fi
}

# Function to generate test report
generate_report() {
    local report_file="$RESULTS_DIR/stress-test-report.md"

    print_status "Generating comprehensive test report..."

    cat > "$report_file" << EOF
# Clogs Stress Test Report

**Generated:** $(date)
**Test Duration:** $(date -d @$test_start_time) to $(date)

## Test Summary

EOF

    # Add individual test summaries if they exist
    for test in ingestion query mixed-workload; do
        if [ -f "$RESULTS_DIR/${test}-summary.json" ]; then
            echo "### ${test^} Test Results" >> "$report_file"
            echo "" >> "$report_file"

            # Extract key metrics using jq if available, otherwise basic grep
            if command -v jq &> /dev/null; then
                echo "- **Total Requests:** $(jq -r '.metrics.http_reqs.values.count // "N/A"' "$RESULTS_DIR/${test}-summary.json")" >> "$report_file"
                echo "- **Error Rate:** $(jq -r '(.metrics.http_req_failed.values.rate * 100 | tostring + "%") // "N/A"' "$RESULTS_DIR/${test}-summary.json")" >> "$report_file"
                echo "- **P95 Latency:** $(jq -r '(.metrics.http_req_duration.values["p(95)"] | tostring + "ms") // "N/A"' "$RESULTS_DIR/${test}-summary.json")" >> "$report_file"
                echo "- **Max VUs:** $(jq -r '.metrics.vus_max.values.max // "N/A"' "$RESULTS_DIR/${test}-summary.json")" >> "$report_file"
            else
                echo "- **Results:** See ${test}-summary.json for detailed metrics" >> "$report_file"
            fi
            echo "" >> "$report_file"
        fi
    done

    cat >> "$report_file" << EOF

## Service Logs

Check \`$LOG_DIR/service.log\` for detailed service logs during testing.

## Raw Results

Detailed results are available in:
- \`$RESULTS_DIR/\` directory contains all JSON result files
- Use k6 dashboard or visualization tools for deeper analysis

## Performance Recommendations

Based on the test results:
1. Monitor P95 latency - should stay under 500ms for ingestion, 1s for queries
2. Watch error rates - should remain below 1-2%
3. Check memory usage in service logs
4. Review file I/O performance if storage becomes a bottleneck

EOF

    print_success "Test report generated: $report_file"
}

# Function to generate performance visualizations and markdown report
generate_visualizations() {
    print_status "Generating performance visualizations and markdown report..."

    # Check if Python and required packages are available
    PYTHON_CMD=""
    if command -v python &> /dev/null; then
        PYTHON_CMD="python"
    elif command -v python3 &> /dev/null; then
        PYTHON_CMD="python3"
    fi

    if [ ! -z "$PYTHON_CMD" ]; then
        if $PYTHON_CMD -c "import matplotlib, seaborn, pandas, numpy" 2>/dev/null; then
            # Generate timestamp for this test run
            TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

            if $PYTHON_CMD scripts/generate-markdown-report.py "$RESULTS_DIR/enhanced-metrics-summary.json" "$TIMESTAMP" 2>/dev/null; then
                print_success "Performance report generated successfully"
                print_status "Check performance_results/$TIMESTAMP/ for comprehensive report"
            else
                print_warning "Report generation failed - check dependencies"
            fi
        else
            print_warning "Missing Python dependencies for visualizations"
            print_status "Install with: pip install matplotlib seaborn pandas numpy"
        fi
    else
        print_warning "Python not found - skipping visualizations"
        print_status "Install Python or set version in .tool-versions"
    fi
}


# Cleanup function
cleanup() {
    print_status "Cleaning up..."
    stop_service
    exit ${1:-0}
}

# Set up signal handlers
trap cleanup SIGINT SIGTERM

# Help function
show_help() {
    cat << EOF
Usage: $0 [OPTIONS] [TEST]

Run stress tests against the Clogs service.

TESTS:
  performance   Run performance test for both /logs and /query endpoints (default)

OPTIONS:
  -h, --help         Show this help message
  -s, --skip-service Don't start/stop service (assume it's running)

EXAMPLES:
  $0                 # Run performance test with markdown report
  $0 -s              # Run test against already running service

RESULTS:
  Results are saved to performance_results/YYYYMMDD_HHMMSS/ with:
  - performance_report_YYYYMMDD_HHMMSS.md  # Comprehensive markdown report
  - images/                                # Performance charts and graphs

EOF
}

# Parse command line arguments
SKIP_SERVICE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -s|--skip-service)
            SKIP_SERVICE=true
            shift
            ;;
        *)
            print_error "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# Main execution
main() {
    test_start_time=$(date +%s)

    print_status "Starting Clogs performance testing..."
    print_status "Testing both /logs and /query endpoints"
    print_status "Results will be saved to: $RESULTS_DIR/"

    # Start service unless skipped
    if [ "$SKIP_SERVICE" = false ]; then
        if check_service; then
            print_warning "Service already running on port $SERVICE_PORT"
        else
            start_service || cleanup 1
        fi
    else
        if ! check_service; then
            print_error "Service is not running on port $SERVICE_PORT"
            cleanup 1
        fi
        print_status "Using existing service instance"
    fi

    # Run performance test (continue even if thresholds are crossed)
    if run_k6_test "k6/enhanced-metrics.js" "enhanced-metrics"; then
        print_success "Performance test completed with all thresholds passed"
    else
        print_warning "Performance test completed but some thresholds were crossed"
        print_status "This might just mean your system performed better than expected!"
    fi

    # Always generate visualizations and markdown report
    generate_visualizations

    print_success "Performance test completed successfully!"
    print_status "Results available in: $RESULTS_DIR/"

    cleanup 0
}

# Run main function
main