#!/bin/bash

# Payments Load Test Runner
# Simple wrapper script for generating test data and running load tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PAYMENTS_DIR="$PROJECT_DIR/msg-platform-payments-worker"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default config file
CONFIG_FILE="${1:-$SCRIPT_DIR/load-test.config}"

# Load configuration
if [ -f "$CONFIG_FILE" ]; then
    echo -e "${GREEN}📋 Loading configuration from: $CONFIG_FILE${NC}"
    source "$CONFIG_FILE"
else
    echo -e "${RED}❌ Configuration file not found: $CONFIG_FILE${NC}"
    echo ""
    echo "Usage: $0 [config-file]"
    echo ""
    echo "Example: $0 scripts/load-test.config"
    echo ""
    exit 1
fi

# Set defaults if not in config
SCENARIO=${SCENARIO:-small}
RATE=${RATE:-10}
DURATION=${DURATION:-60s}
OUTPUT_DIR=${OUTPUT_DIR:-./test-data}
API_URL=${API_URL:-http://localhost:8080}

echo ""
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}  Payments Load Test Runner${NC}"
echo -e "${BLUE}=========================================${NC}"
echo -e "Scenario:     ${GREEN}$SCENARIO${NC}"
echo -e "Rate:         ${GREEN}$RATE req/s${NC}"
echo -e "Duration:     ${GREEN}$DURATION${NC}"
echo -e "Output:       ${GREEN}$OUTPUT_DIR${NC}"
echo -e "API URL:      ${GREEN}$API_URL${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# Step 1: Check if services are running
echo -e "${YELLOW}🔍 Step 1: Checking services...${NC}"
if ! curl -s -f "$API_URL/health" > /dev/null 2>&1; then
    echo -e "${RED}❌ API is not reachable at $API_URL${NC}"
    echo -e "${YELLOW}💡 Hint: Run 'docker-compose up -d' to start services${NC}"
    exit 1
fi
echo -e "${GREEN}✅ API is healthy${NC}"
echo ""

# Step 2: Compile test classes
echo -e "${YELLOW}🔨 Step 2: Compiling test classes...${NC}"
cd "$PAYMENTS_DIR"
mvn test-compile -q
echo -e "${GREEN}✅ Compilation complete${NC}"
echo ""

# Step 3: Generate test data
echo -e "${YELLOW}📊 Step 3: Generating test data...${NC}"
echo -e "Running: E2ETestRunner generate $SCENARIO $OUTPUT_DIR"
mvn exec:java \
  -Dexec.mainClass="com.acme.payments.e2e.E2ETestRunner" \
  -Dexec.classpathScope=test \
  -Dexec.args="generate $SCENARIO $OUTPUT_DIR" \
  -q

echo -e "${GREEN}✅ Test data generated${NC}"
echo ""

# Step 4: Check if vegeta is installed
if ! command -v vegeta &> /dev/null; then
    echo -e "${RED}❌ Vegeta not found${NC}"
    echo -e "${YELLOW}Install with:${NC}"
    echo "  brew install vegeta  # macOS"
    echo "  or download from: https://github.com/tsenart/vegeta"
    exit 1
fi

# Step 5: Run load test
echo -e "${YELLOW}🚀 Step 4: Running load test...${NC}"
VEGETA_DIR="$OUTPUT_DIR/vegeta"
chmod +x src/test/resources/e2e/scripts/run-vegeta-test.sh
./src/test/resources/e2e/scripts/run-vegeta-test.sh "$VEGETA_DIR" "$RATE" "$DURATION"

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}  Load Test Complete!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""

# Step 6: Show next steps
echo -e "${BLUE}📈 View Results:${NC}"
echo "  HTML Plot:  open $VEGETA_DIR/results/plot.html"
echo "  Text Report: cat $VEGETA_DIR/results/combined-report.txt"
echo ""
echo -e "${BLUE}🗄️  Inspect Database:${NC}"
echo "  docker exec -it messaging-postgres psql -U postgres -d payments"
echo ""
echo -e "${BLUE}📊 Quick Queries:${NC}"
echo "  Accounts:    SELECT COUNT(*) FROM account;"
echo "  Payments:    SELECT status, COUNT(*) FROM payment GROUP BY status;"
echo "  Balances:    SELECT account_number, available_balance FROM account ORDER BY created_at DESC LIMIT 10;"
echo ""
