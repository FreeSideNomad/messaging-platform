# Quick Start Guide

Get up and running with the Messaging Platform CLI in 5 minutes.

## 1. Build the Application

```bash
cd platform-cli
mvn clean package -DskipTests
```

This creates: `target/platform-cli.jar`

## 2. Configure Environment

Copy and edit the `.env` file:

```bash
cp .env.template .env
vi .env
```

Minimal configuration for local development:
```properties
DB_HOST=localhost
DB_PORT=5432
DB_NAME=reliable
DB_USER=postgres
DB_PASSWORD=postgres

API_BASE_URL=http://localhost:8080
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
RABBITMQ_HOST=localhost
DOCKER_HOST=unix:///var/run/docker.sock
```

## 3. Test the CLI

### Interactive Mode

```bash
java -jar target/platform-cli.jar
```

### Quick Commands

**List database tables:**
```bash
java -jar target/platform-cli.jar db list
```

**Query a table:**
```bash
java -jar target/platform-cli.jar db query command
```

**List Docker containers:**
```bash
java -jar target/platform-cli.jar docker list
```

**List Kafka topics:**
```bash
java -jar target/platform-cli.jar kafka topics
```

**List RabbitMQ queues:**
```bash
java -jar target/platform-cli.jar mq list
```

## 4. Execute an API Command

Create a payload file (`examples/payment-payload.json`):
```json
{
  "accountId": "ACC001",
  "amount": 1000.00,
  "currency": "USD",
  "description": "Test payment"
}
```

Execute the command:
```bash
java -jar target/platform-cli.jar api exec ProcessPayment examples/payment-payload.json
```

## 5. JSON Output Mode

All commands support `--format json` for programmatic use:

```bash
# Get command data as JSON
java -jar target/platform-cli.jar db query command --format json

# List topics with event counts
java -jar target/platform-cli.jar kafka topics --format json

# Check container stats
java -jar target/platform-cli.jar docker stats payments-worker --format json
```

## 6. Common Workflows

### Monitor System
```bash
# Check queues
java -jar target/platform-cli.jar mq list

# Check Kafka lag
java -jar target/platform-cli.jar kafka lag payment-processor

# Check containers
java -jar target/platform-cli.jar docker list
```

### Database Operations
```bash
# List tables
java -jar target/platform-cli.jar db list

# Query with pagination
java -jar target/platform-cli.jar db query command --page 1
java -jar target/platform-cli.jar db query command --page 2

# Get table info
java -jar target/platform-cli.jar db info command
```

### Docker Operations
```bash
# Execute SQL in database container
java -jar target/platform-cli.jar docker exec messaging-postgres "psql -U postgres -d reliable -c 'SELECT COUNT(*) FROM command;'"

# View logs
java -jar target/platform-cli.jar docker logs payments-worker --tail 50

# Check resource usage
java -jar target/platform-cli.jar docker stats payments-worker
```

## 7. Getting Help

```bash
# General help
java -jar target/platform-cli.jar --help

# Command-specific help
java -jar target/platform-cli.jar db --help
java -jar target/platform-cli.jar db query --help
```

## Troubleshooting

**Connection refused errors:**
- Ensure services (PostgreSQL, Kafka, RabbitMQ, Docker) are running
- Verify hostnames and ports in `.env`
- Check firewall settings

**Permission denied (Docker):**
- Ensure your user has Docker access
- On Mac/Linux: Add user to `docker` group
- On Windows: Ensure Docker Desktop is running

**Table not found:**
- Verify database name in `.env`
- Check if you're connected to the right database
- Use `java -jar target/platform-cli.jar db list` to see available tables

## Next Steps

- Read the full [README.md](README.md) for detailed usage
- Check example payloads in the `examples/` directory
- Configure `.env` for your environment
- Try the interactive menu mode for guided operation
