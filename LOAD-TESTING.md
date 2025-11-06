# Load Testing Guide

Simple guide for running payment load tests against the live Docker Compose stack.

## Quick Start

### 1. Start Services
```bash
docker-compose up -d
```

### 2. Run Load Test
```bash
# Default test (small scenario, 10 req/s, 60s)
./scripts/run-payments-load-test.sh

# Smoke test (quick validation)
./scripts/run-payments-load-test.sh scripts/load-test-smoke.config

# Stress test (heavy load)
./scripts/run-payments-load-test.sh scripts/load-test-stress.config
```

### 3. View Results
```bash
# Open HTML plot
open test-data/vegeta/results/plot.html

# View summary
cat test-data/vegeta/results/combined-report.txt
```

### 4. Inspect Database
```bash
docker exec -it messaging-postgres psql -U postgres -d payments
```

## Configuration Files

### Default Config (`scripts/load-test.config`)
```bash
SCENARIO=small      # 100 accounts
RATE=10            # 10 requests per second
DURATION=60s       # Run for 60 seconds
```

### Smoke Test (`scripts/load-test-smoke.config`)
```bash
SCENARIO=smoke     # 10 accounts (fast)
RATE=5            # 5 req/s
DURATION=30s      # 30 seconds
```

### Stress Test (`scripts/load-test-stress.config`)
```bash
SCENARIO=medium    # 1000 accounts
RATE=100          # 100 req/s
DURATION=120s     # 2 minutes
```

## Creating Custom Config

Create your own config file:
```bash
cat > scripts/my-test.config << 'EOF'
SCENARIO=small
RATE=50
DURATION=90s
OUTPUT_DIR=./my-test-results
API_URL=http://localhost:8080
EOF

# Run with custom config
./scripts/run-payments-load-test.sh scripts/my-test.config
```

## Scenario Sizes

| Scenario | Accounts | Use Case |
|----------|----------|----------|
| smoke    | 10       | Quick sanity check |
| small    | 100      | Development testing |
| medium   | 1,000    | Load testing |
| large    | 10,000   | Stress testing |

## Rate Guidelines

| Rate (req/s) | Load Type | Use Case |
|--------------|-----------|----------|
| 5-10         | Light     | Development |
| 50           | Moderate  | Typical load |
| 100          | Heavy     | Peak load |
| 500+         | Extreme   | Stress testing |

## Database Inspection

### Quick Queries

**Connect to database:**
```bash
docker exec -it messaging-postgres psql -U postgres -d payments
```

**Useful queries:**
```sql
-- Count entities
SELECT 'accounts' as entity, COUNT(*) FROM account
UNION ALL
SELECT 'transactions', COUNT(*) FROM transaction
UNION ALL
SELECT 'payments', COUNT(*) FROM payment;

-- Payment statuses
SELECT status, COUNT(*),
       ROUND(AVG(debit_amount), 2) as avg_amount
FROM payment
GROUP BY status;

-- Account balances
SELECT account_number,
       available_balance,
       currency_code,
       (SELECT COUNT(*) FROM transaction t WHERE t.account_id = a.account_id) as txn_count
FROM account a
ORDER BY created_at DESC
LIMIT 20;

-- Recent transactions
SELECT a.account_number,
       t.transaction_type,
       t.amount,
       t.balance,
       t.transaction_date
FROM transaction t
JOIN account a ON t.account_id = a.account_id
ORDER BY t.transaction_date DESC
LIMIT 20;

-- Failed payments (if any)
SELECT payment_id,
       debit_amount,
       debit_currency_code,
       status,
       created_at
FROM payment
WHERE status = 'FAILED'
ORDER BY created_at DESC;
```

## Load Test Phases

The test runs in 3 phases:

### Phase 1: Create Accounts
- Creates customer accounts
- Sets up account infrastructure
- Waits for accounts to be ready

### Phase 2: Opening Credits
- Adds initial funding to accounts
- Establishes starting balances

### Phase 3: Concurrent Activity
- Funding transactions (background)
- Payment submissions (background)
- Runs both simultaneously to simulate real load

## Output Files

After running a test, you'll find:

```
test-data/vegeta/
├── 01-accounts.txt              # Vegeta targets (accounts)
├── 02-opening-credits.txt       # Vegeta targets (credits)
├── 03-funding-txns.txt          # Vegeta targets (funding)
├── 04-payments.txt              # Vegeta targets (payments)
└── results/
    ├── results-accounts.bin     # Binary results (accounts)
    ├── results-credits.bin      # Binary results (credits)
    ├── results-funding.bin      # Binary results (funding)
    ├── results-payments.bin     # Binary results (payments)
    ├── combined-report.txt      # Summary report
    ├── plot.html                # Visual plot (open in browser)
    └── report.json              # Detailed JSON report
```

## Prerequisites

### Required Tools
- **Docker & Docker Compose** - For running services
- **Maven** - For compiling test code
- **Vegeta** - For load testing

### Install Vegeta
```bash
# macOS
brew install vegeta

# Linux
wget https://github.com/tsenart/vegeta/releases/download/v12.8.4/vegeta_12.8.4_linux_amd64.tar.gz
tar xzf vegeta_12.8.4_linux_amd64.tar.gz
sudo mv vegeta /usr/local/bin/

# Verify
vegeta --version
```

## Troubleshooting

### Services Not Running
```bash
# Check if services are up
docker-compose ps

# Start if needed
docker-compose up -d

# Check API health
curl http://localhost:8080/health
```

### Test Data Already Exists
```bash
# Clean up old test data
rm -rf test-data/

# Or use different output directory in config
OUTPUT_DIR=./test-data-$(date +%Y%m%d-%H%M%S)
```

### High Error Rate
- Reduce RATE in config
- Increase DURATION to spread load
- Check service logs: `docker-compose logs -f`
- Monitor resource usage: `docker stats`

### Database Full
```bash
# Connect and clean up
docker exec -it messaging-postgres psql -U postgres -d payments

# Delete test data
DELETE FROM payment;
DELETE FROM transaction;
DELETE FROM account;
```

## Advanced Usage

### Running Multiple Tests
```bash
# Run tests with different configs back-to-back
for config in scripts/load-test-*.config; do
    echo "Running $config"
    ./scripts/run-payments-load-test.sh "$config"
    sleep 60  # Cool down between tests
done
```

### Monitoring During Test
```bash
# Terminal 1: Run load test
./scripts/run-payments-load-test.sh

# Terminal 2: Watch database
watch -n 2 'docker exec messaging-postgres psql -U postgres -d payments -c "SELECT status, COUNT(*) FROM payment GROUP BY status"'

# Terminal 3: Monitor containers
docker stats

# Terminal 4: Follow logs
docker-compose logs -f payments-worker
```

### Comparing Scenarios
```bash
# Run small test
./scripts/run-payments-load-test.sh scripts/load-test.config
mv test-data test-data-small

# Run medium test
sed 's/SCENARIO=small/SCENARIO=medium/' scripts/load-test.config > /tmp/medium.config
./scripts/run-payments-load-test.sh /tmp/medium.config
mv test-data test-data-medium

# Compare results
echo "Small:"; cat test-data-small/vegeta/results/combined-report.txt | grep "Success ratio"
echo "Medium:"; cat test-data-medium/vegeta/results/combined-report.txt | grep "Success ratio"
```

## Performance Metrics

Key metrics from Vegeta report:

- **Success ratio**: Should be 100% under normal load
- **Mean latency**: Average response time
- **99th percentile**: Response time for slowest 1%
- **Throughput**: Actual req/s achieved
- **Errors**: Any HTTP errors or timeouts

## Integration with CI/CD

Run load tests in CI/CD:
```yaml
- name: Load Test
  run: |
    docker-compose up -d
    ./scripts/run-payments-load-test.sh scripts/load-test-smoke.config
    # Check for errors
    grep "Success ratio.*100%" test-data-smoke/vegeta/results/combined-report.txt
```
