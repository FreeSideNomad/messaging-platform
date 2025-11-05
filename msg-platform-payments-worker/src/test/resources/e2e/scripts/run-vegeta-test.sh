#!/bin/bash
# Vegeta Load Test Execution Script
# Usage: ./run-vegeta-test.sh <scenario-dir> [rate] [duration]

set -e

SCENARIO_DIR=$1
RATE=${2:-10}        # requests per second (default: 10)
DURATION=${3:-60s}   # test duration (default: 60s)

if [ -z "$SCENARIO_DIR" ]; then
    echo "Usage: $0 <scenario-dir> [rate] [duration]"
    echo "Example: $0 ./test-data/vegeta 10 60s"
    exit 1
fi

if [ ! -d "$SCENARIO_DIR" ]; then
    echo "Error: Scenario directory not found: $SCENARIO_DIR"
    exit 1
fi

echo "========================================="
echo "Vegeta Load Test"
echo "========================================="
echo "Scenario: $SCENARIO_DIR"
echo "Rate: $RATE req/s"
echo "Duration: $DURATION"
echo "========================================="

# Create results directory
RESULTS_DIR="$SCENARIO_DIR/results"
mkdir -p "$RESULTS_DIR"

# Function to run a test phase
run_phase() {
    local targets=$1
    local rate=$2
    local duration=$3
    local output=$4
    local phase_name=$5

    if [ ! -f "$targets" ]; then
        echo "Warning: Target file not found: $targets (skipping)"
        return
    fi

    echo ""
    echo "--- Phase: $phase_name ---"
    echo "Running: vegeta attack -targets=$targets -rate=$rate -duration=$duration"

    vegeta attack \
        -targets="$targets" \
        -rate="$rate" \
        -duration="$duration" \
        -timeout=30s \
        > "$output"

    echo "Generating report..."
    vegeta report "$output"
}

# Phase 1: Create accounts
echo ""
echo "========================================="
echo "PHASE 1: Creating Accounts"
echo "========================================="
run_phase "$SCENARIO_DIR/01-accounts.txt" "$RATE" "$DURATION" "$RESULTS_DIR/results-accounts.bin" "Create Accounts"

# Wait for accounts to be created
echo ""
echo "Waiting 10 seconds for accounts to be created..."
sleep 10

# Phase 2: Opening credits
echo ""
echo "========================================="
echo "PHASE 2: Opening Credits"
echo "========================================="
run_phase "$SCENARIO_DIR/02-opening-credits.txt" "$RATE" "$DURATION" "$RESULTS_DIR/results-credits.bin" "Opening Credits"

# Wait for transactions to be processed
echo ""
echo "Waiting 5 seconds for transactions to be processed..."
sleep 5

# Phase 3: Concurrent funding and payments
echo ""
echo "========================================="
echo "PHASE 3: Funding & Payments (Concurrent)"
echo "========================================="

# Run funding in background
if [ -f "$SCENARIO_DIR/03-funding-txns.txt" ]; then
    echo "Starting funding transactions in background..."
    run_phase "$SCENARIO_DIR/03-funding-txns.txt" "$RATE" "$DURATION" "$RESULTS_DIR/results-funding.bin" "Funding Transactions" &
    FUNDING_PID=$!
fi

# Run payments in background
if [ -f "$SCENARIO_DIR/04-payments.txt" ]; then
    echo "Starting payments in background..."
    run_phase "$SCENARIO_DIR/04-payments.txt" "$RATE" "$DURATION" "$RESULTS_DIR/results-payments.bin" "Payments" &
    PAYMENTS_PID=$!
fi

# Wait for both to complete
wait

echo ""
echo "========================================="
echo "PHASE 3: Complete"
echo "========================================="

# Generate combined reports
echo ""
echo "========================================="
echo "Generating Combined Reports"
echo "========================================="

if ls "$RESULTS_DIR"/results-*.bin 1> /dev/null 2>&1; then
    echo "Text report:"
    vegeta report -type=text "$RESULTS_DIR"/results-*.bin | tee "$RESULTS_DIR/combined-report.txt"

    echo ""
    echo "Generating HTML plot..."
    vegeta plot "$RESULTS_DIR"/results-*.bin > "$RESULTS_DIR/plot.html"
    echo "HTML plot saved to: $RESULTS_DIR/plot.html"

    echo ""
    echo "Generating JSON report..."
    vegeta report -type=json "$RESULTS_DIR"/results-*.bin > "$RESULTS_DIR/report.json"
    echo "JSON report saved to: $RESULTS_DIR/report.json"
else
    echo "Warning: No result files found"
fi

echo ""
echo "========================================="
echo "Load Test Complete!"
echo "========================================="
echo "Results directory: $RESULTS_DIR"
echo ""
