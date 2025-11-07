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

    # Calculate number of targets and adjust duration so we hit each once
    local target_count
    target_count=$(wc -l < "$targets")
    local calculated_duration
    if [[ "$rate" -le 0 ]]; then
        calculated_duration="1s"
    else
        calculated_duration=$(awk -v total="$target_count" -v r="$rate" 'BEGIN {
            if (r <= 0) {
                print "1s";
            } else {
                duration = total / r;
                if (duration < 0.001) {
                    duration = 0.001;
                }
                printf("%.3fs", duration);
            }
        }')
    fi

    echo ""
    echo "--- Phase: $phase_name ---"
    echo "Targets: $target_count requests"
    echo "Running: vegeta attack -format=json -targets=$targets -rate=$rate -duration=$calculated_duration"

    vegeta attack \
        -format=json \
        -targets="$targets" \
        -rate="$rate" \
        -duration="$calculated_duration" \
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

echo ""
echo "Skipping subsequent phases (opening credits, funding, payments)."

echo ""
echo "========================================="
echo "Load Test Complete!"
echo "========================================="
echo "Results directory: $RESULTS_DIR"
echo ""
