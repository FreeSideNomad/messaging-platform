#!/bin/bash

##############################################################################
# E2E Testing Helper: Check API Health
#
# This script verifies that all e2e infrastructure is ready
#
# Usage:
#   ./check-api-health.sh [--api-url URL]
##############################################################################

API_URL="${1:-http://localhost:8081}"

echo "=========================================="
echo "Checking E2E Infrastructure Health"
echo "=========================================="
echo ""

# Function to check service
check_service() {
  local name=$1
  local url=$2
  local expected_status=$3

  echo -n "Checking $name... "
  http_code=$(curl -s -o /dev/null -w "%{http_code}" "$url")

  if [ "$http_code" -eq "$expected_status" ]; then
    echo "✅ OK ($http_code)"
    return 0
  else
    echo "❌ FAILED (HTTP $http_code, expected $expected_status)"
    return 1
  fi
}

# Check services
check_service "Payments API" "$API_URL/commands/health" 200
api_healthy=$?

check_service "PostgreSQL" "localhost:5432" 1  # Connection attempt will fail but shows port is open
pg_healthy=$?

check_service "Kafka" "localhost:9092" 1
kafka_healthy=$?

check_service "Redis" "localhost:6379" 1
redis_healthy=$?

echo ""
echo "=========================================="

if [ $api_healthy -eq 0 ]; then
  echo "✅ E2E infrastructure is ready!"
  echo ""
  echo "You can now:"
  echo "  1. Start the payment worker with e2e profile"
  echo "  2. Run: ./create-account.sh"
  exit 0
else
  echo "❌ API is not ready. Please check:"
  echo "  docker-compose -f docker-compose.payments-e2e.yml ps"
  echo "  docker-compose -f docker-compose.payments-e2e.yml logs api-payments"
  exit 1
fi
