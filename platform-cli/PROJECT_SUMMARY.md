# Platform CLI - Project Summary

## Overview

A comprehensive Java-based command-line interface tool for managing and monitoring the messaging platform. Supports both
interactive menu mode and direct CLI commands with JSON output.

## Project Status

✅ **COMPLETED** - Ready for use

## What Was Built

### Core Application

- **Technology Stack**: Java 17, Maven, Picocli, JLine3
- **Configuration**: `.env` file-based configuration using dotenv-java
- **Packaging**: Single executable JAR (platform-cli.jar) with all dependencies

### Features Implemented

#### 1. Database Query Tool ✅

- Query any table with pagination (default 20 records per page)
- Next/previous page navigation
- List all available tables
- View table information (columns, row count)
- Output formats: formatted table or JSON

#### 2. API Command Execution ✅

- Execute commands via HTTP API
- Load payloads from JSON files
- Automatic idempotency key generation with timestamp suffix
- Configurable idempotency key prefix
- Support for GET and POST requests
- Pretty-printed JSON responses

#### 3. Message Queue Monitoring (RabbitMQ) ✅

- List all queues with message counts
- Show consumer counts
- Health status indicators
- Get detailed queue status

#### 4. Kafka Topic Monitoring ✅

- List all topics with event counts
- Show partition information
- Display replication factors
- Consumer group lag monitoring
- Support for multiple consumer groups

#### 5. Docker Container Management ✅

- List running and stopped containers
- Execute commands inside containers
- View container logs with configurable tail
- Display resource usage statistics (CPU, memory)
- Container health monitoring

#### 6. Interactive Menu Mode ✅

- Menu-driven interface for all features
- Guided prompts and navigation
- Table browsing with pagination
- User-friendly error messages

## Project Structure

```
platform-cli/
├── pom.xml                          # Maven build configuration
├── .env.template                    # Environment configuration template
├── .env                             # Active environment configuration (gitignored)
├── README.md                        # Full documentation
├── QUICKSTART.md                    # Quick start guide
├── PROJECT_SUMMARY.md               # This file
├── examples/                        # Sample payload files
│   ├── payment-payload.json
│   └── account-payload.json
└── src/main/java/com/acme/platform/cli/
    ├── CliApplication.java          # Main entry point
    ├── commands/                    # CLI command classes (Picocli)
    │   ├── DatabaseCommands.java    # db list, query, info
    │   ├── ApiCommands.java         # api exec, get
    │   ├── MqCommands.java          # mq list, status
    │   ├── KafkaCommands.java       # kafka topics, topic, lag
    │   └── DockerCommands.java      # docker list, exec, logs, stats
    ├── service/                     # Business logic
    │   ├── DatabaseService.java     # HikariCP + JDBC operations
    │   ├── ApiService.java          # OkHttp API client
    │   ├── MqService.java           # RabbitMQ operations
    │   ├── KafkaService.java        # Kafka AdminClient operations
    │   └── DockerService.java       # Docker Java API operations
    ├── ui/                          # Interactive UI
    │   └── InteractiveMenu.java     # JLine3 menu interface
    ├── config/                      # Configuration
    │   └── CliConfiguration.java    # Dotenv-based config loader
    └── model/                       # Data models
        └── PaginatedResult.java     # Pagination wrapper
```

## Key Dependencies

| Dependency      | Version | Purpose                        |
|-----------------|---------|--------------------------------|
| Picocli         | 4.7.5   | CLI framework with annotations |
| JLine3          | 3.25.0  | Interactive terminal features  |
| PostgreSQL JDBC | 42.7.1  | Database connectivity          |
| HikariCP        | 5.1.0   | Connection pooling             |
| Kafka Clients   | 3.6.1   | Kafka admin operations         |
| RabbitMQ Client | 5.20.0  | Message queue operations       |
| Docker Java     | 3.3.4   | Docker API integration         |
| OkHttp          | 4.12.0  | HTTP client for API calls      |
| Jackson         | 2.16.1  | JSON parsing and generation    |
| Dotenv Java     | 3.0.0   | .env file configuration        |
| Logback         | 1.4.14  | Logging framework              |

## Usage Modes

### 1. Interactive Menu Mode

Run without arguments:

```bash
java -jar target/platform-cli.jar
```

### 2. Direct CLI Mode

Run with commands:

```bash
java -jar target/platform-cli.jar db query command --format json
java -jar target/platform-cli.jar kafka topics
java -jar target/platform-cli.jar docker list --all
```

## Configuration

All configuration via `.env` file:

### Required Settings

- Database: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- API: `API_BASE_URL`
- Kafka: `KAFKA_BOOTSTRAP_SERVERS`
- RabbitMQ: `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`
- Docker: `DOCKER_HOST`

### Optional Settings

- `CLI_PAGE_SIZE` - Default pagination size (default: 20)
- `CLI_DEFAULT_IDEMPOTENCY_PREFIX` - Default prefix for API idempotency keys (default: "cli-request")
- `DB_MAX_POOL_SIZE` - Database connection pool size (default: 10)
- `API_TIMEOUT_SECONDS` - API request timeout (default: 30)

## Building and Running

### Build

```bash
cd platform-cli
mvn clean package
```

Creates: `target/platform-cli.jar` (~50MB with all dependencies)

### Run

```bash
# Interactive mode
java -jar target/platform-cli.jar

# CLI mode
java -jar target/platform-cli.jar <command> [options]
```

### Help

```bash
java -jar target/platform-cli.jar --help
java -jar target/platform-cli.jar db --help
```

## Example Usage

### Database Queries

```bash
# List all tables
java -jar target/platform-cli.jar db list

# Query with pagination
java -jar target/platform-cli.jar db query command --page 1

# Get as JSON
java -jar target/platform-cli.jar db query command --format json | jq '.data[] | select(.status=="PENDING")'
```

### API Commands

```bash
# Execute with payload
java -jar target/platform-cli.jar api exec ProcessPayment examples/payment-payload.json

# With custom idempotency prefix
java -jar target/platform-cli.jar api exec CreateAccount examples/account-payload.json --idempotency-prefix manual-test
```

### Monitoring

```bash
# Check queue health
java -jar target/platform-cli.jar mq list

# View Kafka topic metrics
java -jar target/platform-cli.jar kafka topics

# Check consumer lag
java -jar target/platform-cli.jar kafka lag payment-processor

# Container stats
java -jar target/platform-cli.jar docker stats payments-worker
```

### Docker Operations

```bash
# Execute SQL
java -jar target/platform-cli.jar docker exec messaging-postgres "psql -U postgres -d reliable -c 'SELECT COUNT(*) FROM command;'"

# View logs
java -jar target/platform-cli.jar docker logs payments-worker --tail 100
```

## Output Formats

### Table Format (Default)

Human-readable tables with borders and alignment

### JSON Format

Machine-readable JSON for scripting and automation (use `--format json`)

## Security Considerations

- ✅ SQL injection protection via table name validation
- ✅ Credentials stored in `.env` file (gitignored)
- ✅ Docker command input sanitization
- ✅ Connection pooling with limits
- ✅ Timeout protection on all operations
- ⚠️ **Note**: Ensure `.env` file has restricted permissions (600)

## Logging

- Console: INFO level and above
- File: `platform-cli.log` (rotated daily, 7-day retention)
- Configurable via `src/main/resources/logback.xml`

## Future Enhancements

Potential improvements for future versions:

1. **Export Capabilities**
    - CSV/Excel export for query results
    - Batch export of metrics

2. **Monitoring & Alerts**
    - Scheduled health checks
    - Alert notifications
    - Threshold-based warnings

3. **Batch Operations**
    - Bulk API command execution
    - Multi-table queries

4. **Docker Compose Integration**
    - Start/stop services
    - Health check automation

5. **Real-time Features**
    - Log streaming
    - Live metrics dashboard

6. **Configuration Profiles**
    - Multiple environment support
    - Profile switching
    - Environment templates

## Testing

Current state:

- ✅ Compiles successfully with Java 17
- ✅ All dependencies resolved
- ✅ Executable JAR built
- ⚠️ Unit tests not implemented (use `-DskipTests`)
- ⚠️ Integration tests not implemented

Recommended testing approach:

1. Unit tests for each service class
2. Integration tests with Testcontainers
3. End-to-end CLI command tests with Picocli testing utilities

## Known Limitations

1. **RabbitMQ Queue Discovery**: Uses hardcoded list of known queues. For complete queue discovery, use RabbitMQ
   Management API.

2. **Kafka Event Count**: Calculation may be slow for topics with many partitions as it reads all partition offsets.

3. **Docker Stats**: Some statistics may not be available depending on Docker version and host OS.

4. **Connection Management**: Services create connections on demand. For high-frequency usage, consider connection
   caching.

## Troubleshooting

### Build Issues

- Ensure Java 17+ is installed: `java -version`
- Clean Maven cache: `mvn clean`
- Check internet connection for dependency downloads

### Runtime Issues

- Connection errors: Verify services are running and `.env` is configured correctly
- Permission errors: Check Docker socket permissions and database user privileges
- Table not found: Verify database name and that tables exist

## Documentation

- **README.md**: Complete usage documentation with all commands and options
- **QUICKSTART.md**: 5-minute getting started guide
- **PROJECT_SUMMARY.md**: This file - high-level overview
- **cli-setup.md**: Original specification and requirements
- **.env.template**: Configuration template with all options

## Contributors

Created by Claude Code based on requirements in `cli-setup.md`

## License

Internal tool for Acme Messaging Platform.
