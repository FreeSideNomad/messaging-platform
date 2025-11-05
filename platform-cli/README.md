# Messaging Platform CLI

A comprehensive command-line interface tool for managing and monitoring the messaging platform. Supports both interactive menu mode and direct CLI commands with JSON output.

## Features

- **Database Queries**: Query any table with pagination support
- **API Command Execution**: Execute commands via API with JSON payloads and automatic idempotency key generation
- **Message Queue Monitoring**: View RabbitMQ queue status and health
- **Kafka Topic Monitoring**: List topics, view event counts, and monitor consumer lag
- **Docker Management**: List containers, execute commands, view logs, and check resource usage

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Access to the messaging platform infrastructure (database, Kafka, RabbitMQ, Docker)

## Installation

### 1. Clone and Build

```bash
cd platform-cli
mvn clean package
```

This will create an executable JAR file: `target/platform-cli.jar`

### 2. Configure Environment

Copy the `.env.template` file to `.env` and configure your environment:

```bash
cp .env.template .env
```

Edit `.env` with your actual configuration:

```properties
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=reliable
DB_USER=postgres
DB_PASSWORD=postgres

# API Configuration
API_BASE_URL=http://localhost:8080

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# RabbitMQ Configuration
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

# Docker Configuration
DOCKER_HOST=unix:///var/run/docker.sock
```

## Usage

### Interactive Menu Mode

Run the CLI without any arguments to start the interactive menu:

```bash
java -jar target/platform-cli.jar
```

You'll see a menu-driven interface:

```
=== Messaging Platform CLI - Interactive Mode ===

--- Main Menu ---
1. Database Queries
2. API Commands
3. Message Queue Status
4. Kafka Topics
5. Docker Management
6. Exit

Select option:
```

### CLI Command Mode

Run specific commands directly with optional JSON output:

#### Database Commands

**List all tables:**
```bash
java -jar target/platform-cli.jar db list
java -jar target/platform-cli.jar db list --format json
```

**Query a table:**
```bash
# First page (default 20 records)
java -jar target/platform-cli.jar db query command

# Specific page
java -jar target/platform-cli.jar db query command --page 2

# Custom page size
java -jar target/platform-cli.jar db query command --page 1 --page-size 50

# JSON output
java -jar target/platform-cli.jar db query command --format json
```

**Get table info:**
```bash
java -jar target/platform-cli.jar db info command
java -jar target/platform-cli.jar db info command --format json
```

#### API Commands

**Execute a command:**

First, create a payload file (e.g., `payment-payload.json`):
```json
{
  "accountId": "ACC001",
  "amount": 1000.00,
  "currency": "USD"
}
```

Then execute:
```bash
java -jar target/platform-cli.jar api exec ProcessPayment payment-payload.json

# With custom idempotency prefix
java -jar target/platform-cli.jar api exec ProcessPayment payment-payload.json --idempotency-prefix test-run

# JSON output
java -jar target/platform-cli.jar api exec ProcessPayment payment-payload.json --format json
```

The CLI automatically generates an idempotency key with timestamp: `test-run-1699123456789`

**Make a GET request:**
```bash
java -jar target/platform-cli.jar api get /api/health
java -jar target/platform-cli.jar api get /api/commands/123 --format json
```

#### Message Queue Commands

**List all queues:**
```bash
java -jar target/platform-cli.jar mq list
java -jar target/platform-cli.jar mq list --format json
```

**Get queue status:**
```bash
java -jar target/platform-cli.jar mq status payment-commands
java -jar target/platform-cli.jar mq status payment-commands --format json
```

#### Kafka Commands

**List all topics:**
```bash
java -jar target/platform-cli.jar kafka topics
java -jar target/platform-cli.jar kafka topics --format json
```

**Get topic info:**
```bash
java -jar target/platform-cli.jar kafka topic payment-events
java -jar target/platform-cli.jar kafka topic payment-events --format json
```

**Show consumer lag:**
```bash
java -jar target/platform-cli.jar kafka lag payment-processor
java -jar target/platform-cli.jar kafka lag payment-processor --format json
```

#### Docker Commands

**List containers:**
```bash
# Running containers only
java -jar target/platform-cli.jar docker list

# All containers
java -jar target/platform-cli.jar docker list --all

# JSON output
java -jar target/platform-cli.jar docker list --format json
```

**Execute command in container:**
```bash
java -jar target/platform-cli.jar docker exec messaging-postgres "psql -U postgres -d reliable -c 'SELECT COUNT(*) FROM command;'"

# JSON output
java -jar target/platform-cli.jar docker exec payments-worker "ls -la" --format json
```

**View container logs:**
```bash
# Last 100 lines (default)
java -jar target/platform-cli.jar docker logs payments-worker

# Last 50 lines
java -jar target/platform-cli.jar docker logs payments-worker --tail 50

# JSON output
java -jar target/platform-cli.jar docker logs payments-worker --format json
```

**Container resource stats:**
```bash
java -jar target/platform-cli.jar docker stats payments-worker
java -jar target/platform-cli.jar docker stats messaging-postgres --format json
```

## Example Workflows

### 1. Check Database and Execute Command

```bash
# Check pending commands
java -jar target/platform-cli.jar db query command --format json | jq '.data[] | select(.status=="PENDING")'

# Execute a payment command
java -jar target/platform-cli.jar api exec ProcessPayment payment.json --idempotency-prefix manual-test
```

### 2. Monitor System Health

```bash
# Check message queues
java -jar target/platform-cli.jar mq list

# Check Kafka topics
java -jar target/platform-cli.jar kafka topics

# Check container health
java -jar target/platform-cli.jar docker list
java -jar target/platform-cli.jar docker stats payments-worker
```

### 3. Debug Issues

```bash
# View application logs
java -jar target/platform-cli.jar docker logs payments-worker --tail 100

# Execute SQL directly
java -jar target/platform-cli.jar docker exec messaging-postgres "psql -U postgres -d reliable -c 'SELECT status, COUNT(*) FROM command GROUP BY status;'"

# Check consumer lag
java -jar target/platform-cli.jar kafka lag payment-processor
```

## Output Formats

### Table Format (Default)

Human-readable formatted output with boxes and columns:

```
+--------+----------+--------+
| ID     | Status   | Amount |
+--------+----------+--------+
| 1      | PENDING  | 100.00 |
| 2      | COMPLETE | 250.00 |
+--------+----------+--------+

Page 1 of 3 (Total records: 45)
```

### JSON Format

Machine-readable JSON output for scripting:

```json
{
  "data": [
    {"id": 1, "status": "PENDING", "amount": 100.00},
    {"id": 2, "status": "COMPLETE", "amount": 250.00}
  ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "totalRecords": 45,
    "totalPages": 3
  }
}
```

## Help

Get help for any command using `--help`:

```bash
java -jar target/platform-cli.jar --help
java -jar target/platform-cli.jar db --help
java -jar target/platform-cli.jar db query --help
java -jar target/platform-cli.jar api --help
java -jar target/platform-cli.jar mq --help
java -jar target/platform-cli.jar kafka --help
java -jar target/platform-cli.jar docker --help
```

## Configuration Reference

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | Database host | localhost |
| `DB_PORT` | Database port | 5432 |
| `DB_NAME` | Database name | reliable |
| `DB_USER` | Database username | postgres |
| `DB_PASSWORD` | Database password | postgres |
| `DB_MAX_POOL_SIZE` | Connection pool size | 10 |
| `API_BASE_URL` | API base URL | http://localhost:8080 |
| `API_TIMEOUT_SECONDS` | API request timeout | 30 |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | localhost:9092 |
| `KAFKA_CONSUMER_GROUP` | Consumer group ID | platform-cli |
| `RABBITMQ_HOST` | RabbitMQ host | localhost |
| `RABBITMQ_PORT` | RabbitMQ port | 5672 |
| `RABBITMQ_USER` | RabbitMQ username | guest |
| `RABBITMQ_PASSWORD` | RabbitMQ password | guest |
| `RABBITMQ_VHOST` | RabbitMQ virtual host | / |
| `DOCKER_HOST` | Docker daemon socket | unix:///var/run/docker.sock |
| `CLI_PAGE_SIZE` | Default pagination size | 20 |
| `CLI_DEFAULT_IDEMPOTENCY_PREFIX` | Default idempotency prefix | cli-request |

## Logging

Logs are written to:
- Console: INFO level and above
- File: `platform-cli.log` (rotated daily, kept for 7 days)

## Troubleshooting

### Connection Issues

**Database connection failed:**
- Verify database is running and accessible
- Check `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD` in `.env`
- Test with: `psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME`

**Kafka connection failed:**
- Verify Kafka is running
- Check `KAFKA_BOOTSTRAP_SERVERS` in `.env`
- Test with: `kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS --list`

**RabbitMQ connection failed:**
- Verify RabbitMQ is running
- Check `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` in `.env`
- Test with RabbitMQ management UI at http://localhost:15672

**Docker connection failed:**
- Verify Docker daemon is running
- Check `DOCKER_HOST` in `.env`
- On Mac/Linux: Ensure Docker socket is accessible
- On Windows: Use `DOCKER_HOST=npipe:////./pipe/docker_engine`

### Performance

**Queries are slow:**
- Reduce page size: `--page-size 10`
- Add database indexes on frequently queried columns
- Check database connection pool size: `DB_MAX_POOL_SIZE`

**Topic event count takes long:**
- This is normal for large topics with many partitions
- Event count is calculated by reading offsets from all partitions

## Development

### Project Structure

```
platform-cli/
├── src/main/java/com/acme/platform/cli/
│   ├── CliApplication.java          # Main entry point
│   ├── commands/                    # CLI command classes
│   │   ├── DatabaseCommands.java
│   │   ├── ApiCommands.java
│   │   ├── MqCommands.java
│   │   ├── KafkaCommands.java
│   │   └── DockerCommands.java
│   ├── service/                     # Business logic
│   │   ├── DatabaseService.java
│   │   ├── ApiService.java
│   │   ├── MqService.java
│   │   ├── KafkaService.java
│   │   └── DockerService.java
│   ├── ui/                          # Interactive UI
│   │   └── InteractiveMenu.java
│   ├── config/                      # Configuration
│   │   └── CliConfiguration.java
│   └── model/                       # Data models
│       └── PaginatedResult.java
└── src/main/resources/
    └── logback.xml                  # Logging configuration
```

### Build Commands

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package without tests
mvn package -DskipTests

# Create executable JAR
mvn clean package
```

## License

Internal tool for Acme Messaging Platform.
