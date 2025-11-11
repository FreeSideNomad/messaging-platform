# E2E Testing Scripts

This directory contains shell scripts for end-to-end testing of the payment worker against the docker-compose
infrastructure.

## Prerequisites

- Docker Compose stack running: `docker-compose -f docker-compose.payments-e2e.yml up -d`
- Payment worker running with e2e profile
- `curl` command-line tool
- `jq` for JSON formatting (optional but recommended)
- `uuidgen` for generating UUIDs

## Quick Start

```bash
# 1. Start docker-compose (from project root)
docker-compose -f docker-compose.payments-e2e.yml up -d

# 2. Start payment worker in IntelliJ with "Payments Worker E2E Debug" configuration

# 3. Check infrastructure is ready
cd scripts/e2e
./check-api-health.sh

# 4. Create test account
./create-account.sh

# 5. Watch payment worker console for processing logs
```

## Scripts

### check-api-health.sh

Verifies that all e2e infrastructure services are running and accessible.

#### Usage

```bash
./check-api-health.sh [--api-url URL]
```

#### Output

```
Checking Payments API... ✅ OK (200)
Checking PostgreSQL... (port check)
Checking Kafka... (port check)
Checking Redis... (port check)

✅ E2E infrastructure is ready!
```

### create-account.sh

Creates a new account via the Payments API using the `CreateAccountCommand` command.

#### Usage

```bash
./create-account.sh [OPTIONS]
```

#### Options

| Option             | Value  | Default                 | Description                                                  |
|--------------------|--------|-------------------------|--------------------------------------------------------------|
| `--api-url`        | URL    | `http://localhost:8081` | Payments API endpoint                                        |
| `--customer-id`    | UUID   | Random                  | Customer ID for the account                                  |
| `--currency`       | Code   | `USD`                   | ISO 4217 currency code                                       |
| `--transit-number` | String | `001`                   | Bank transit/routing number                                  |
| `--account-type`   | Type   | `CHECKING`              | Account type: CHECKING, SAVINGS, CREDIT_CARD, LINE_OF_CREDIT |

#### Examples

**Create a CHECKING account (USD):**

```bash
./create-account.sh
```

**Create a SAVINGS account in EUR:**

```bash
./create-account.sh --currency EUR --account-type SAVINGS
```

**Create a CREDIT_CARD account for a specific customer:**

```bash
./create-account.sh \
  --customer-id 550e8400-e29b-41d4-a716-446655440000 \
  --currency GBP \
  --account-type CREDIT_CARD
```

**Create multiple accounts for testing:**

```bash
for i in {1..5}; do
  ./create-account.sh --currency USD --account-type CHECKING
  sleep 2
done
```

#### Response

Success (HTTP 200/202):

```json
{
  "message": "Command accepted, processing asynchronously"
}
```

Failure (HTTP 4xx/5xx):

```json
{
  "error": "Error message",
  "details": "..."
}
```

## Monitoring

After creating an account, monitor the payment worker logs:

```bash
# Using IntelliJ - check the console output in Debug tab
# Or via logs:
tail -f path/to/worker/logs
```

Look for:

- `CreateAccountCommand processing started`
- `Account created successfully`
- Any validation or error messages

## Testing Workflow

1. **Start Docker Compose:**
   ```bash
   docker-compose -f docker-compose.payments-e2e.yml up -d
   ```

2. **Start Payment Worker in IntelliJ Debug mode**

3. **Create test accounts:**
   ```bash
   ./create-account.sh --account-type CHECKING
   ./create-account.sh --currency EUR --account-type SAVINGS --no-limits
   ./create-account.sh --currency GBP --account-type CREDIT_CARD
   ```

4. **Monitor payment worker console** for processing logs

5. **Verify in database** (optional):
   ```bash
   psql -h localhost -U postgres -d payments -c "SELECT * FROM accounts ORDER BY created_at DESC LIMIT 5;"
   ```

## Troubleshooting

### API connection refused

- Verify docker-compose is running: `docker-compose -f docker-compose.payments-e2e.yml ps`
- Check API is healthy: `curl http://localhost:8081/commands/health`

### Command rejected

- Check Payment API logs: `docker logs messaging-api-payments-e2e`
- Verify JSON payload syntax (should be valid after `|jq .`)

### Account not processing

- Verify payment worker is running and has `MICRONAUT_ENVIRONMENTS=e2e`
- Check worker debug console for errors
- Verify IBM MQ and Kafka are connected
- Check database connectivity

### UUID generation issues (macOS)

If `uuidgen` is not available:

```bash
# Install via Homebrew
brew install ossp-uuid

# Or use Python
python3 -c "import uuid; print(uuid.uuid4())"
```
