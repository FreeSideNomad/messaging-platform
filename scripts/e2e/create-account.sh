#!/bin/bash

##############################################################################
# E2E Testing Script: CreateAccount Command
#
# This script posts a CreateAccount command to the Payments API
#
# Usage:
#   ./create-account.sh [--api-url URL] [--customer-id UUID] [--currency CURRENCY] \
#                       [--account-type TYPE]
#
# Examples:
#   ./create-account.sh                              # Use all defaults
#   ./create-account.sh --api-url http://localhost:8081
#   ./create-account.sh --customer-id 550e8400-e29b-41d4-a716-446655440000
#   ./create-account.sh --currency EUR --account-type SAVINGS
##############################################################################

set -e

# Default values
API_URL="${API_URL:-http://localhost:8081}"
CUSTOMER_ID="${CUSTOMER_ID:-$(uuidgen)}"
CURRENCY_CODE="${CURRENCY_CODE:-USD}"
TRANSIT_NUMBER="${TRANSIT_NUMBER:-001}"
ACCOUNT_TYPE="${ACCOUNT_TYPE:-CHECKING}"
IDEMPOTENCY_KEY="$(uuidgen)"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --api-url)
      API_URL="$2"
      shift 2
      ;;
    --customer-id)
      CUSTOMER_ID="$2"
      shift 2
      ;;
    --currency)
      CURRENCY_CODE="$2"
      shift 2
      ;;
    --transit-number)
      TRANSIT_NUMBER="$2"
      shift 2
      ;;
    --account-type)
      ACCOUNT_TYPE="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# Validate account type
case "$ACCOUNT_TYPE" in
  CHECKING|SAVINGS|CREDIT_CARD|LINE_OF_CREDIT)
    ;;
  *)
    echo "Invalid account type: $ACCOUNT_TYPE"
    echo "Valid types: CHECKING, SAVINGS, CREDIT_CARD, LINE_OF_CREDIT"
    exit 1
    ;;
esac

# Build the JSON payload
PAYLOAD=$(cat <<EOF
{
  "customerId": "$CUSTOMER_ID",
  "currencyCode": "$CURRENCY_CODE",
  "transitNumber": "$TRANSIT_NUMBER",
  "accountType": "$ACCOUNT_TYPE"
}
EOF
)

# Display request details
echo "=========================================="
echo "Creating Account via E2E API"
echo "=========================================="
echo "API URL:        $API_URL"
echo "Idempotency Key: $IDEMPOTENCY_KEY"
echo "Customer ID:    $CUSTOMER_ID"
echo "Currency:       $CURRENCY_CODE"
echo "Account Type:   $ACCOUNT_TYPE"
echo ""
echo "Request Payload:"
echo "$PAYLOAD" | jq '.' 2>/dev/null || echo "$PAYLOAD"
echo ""
echo "=========================================="
echo "Sending request..."
echo "=========================================="
echo ""

# Send the request and capture response + headers
HTTP_CODE=$(curl -s -o /tmp/response_body.txt -w "%{http_code}" \
  -X POST "$API_URL/commands/CreateAccountCommand" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$PAYLOAD")

# Read response body
BODY=$(cat /tmp/response_body.txt)
rm -f /tmp/response_body.txt

echo "HTTP Status Code: $HTTP_CODE"
echo ""
echo "Response:"
echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
echo ""

# Extract command ID from headers (if available)
if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 202 ]; then
  echo "=========================================="
  echo "✅ Command accepted successfully!"
  echo "=========================================="
  echo "Customer ID: $CUSTOMER_ID"
  echo "Idempotency Key: $IDEMPOTENCY_KEY"
  echo ""
  echo "The account creation command has been submitted."
  echo "Monitor the payment worker logs for processing status."
  exit 0
else
  echo "=========================================="
  echo "❌ Request failed with status $HTTP_CODE"
  echo "=========================================="
  exit 1
fi
