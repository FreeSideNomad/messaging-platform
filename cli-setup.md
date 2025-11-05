# CLI Setup for Messaging Platform Management Tool

## Overview
Create a Java-based command-line interface (CLI) application for managing and monitoring the messaging platform. The application should support both interactive menu mode and direct CLI commands with JSON output.

## Core Features

### 1. Database Query Tool
- **View Tables**: Query any database table and display top 20 records
- **Pagination**: Support for next/previous page navigation
- **Format**: Display in formatted table view (interactive) or JSON (CLI mode)
- **Connection**: Use existing database connection configuration from the platform

**Example Commands:**
```bash
# Interactive mode
java -jar cli.jar db query --table commands

# JSON output mode
java -jar cli.jar db query --table commands --page 1 --format json

# Next/previous page
java -jar cli.jar db query --table commands --page 2 --format json
```

### 2. API Command Execution
- **Process Commands**: Execute existing process commands via API
- **Payload Loading**: Load payloads from JSON files
- **Idempotency Keys**: Auto-generate idempotency keys with timestamp suffix
- **Response Handling**: Display API response with status

**Example Commands:**
```bash
# Execute command with payload file
java -jar cli.jar api exec --command CreateAccount --payload account-payload.json

# With custom idempotency key prefix
java -jar cli.jar api exec --command ProcessPayment --payload payment.json --idempotency-prefix test-run
```

**Payload File Format:**
```json
{
  "accountId": "ACC001",
  "amount": 1000.00,
  "currency": "USD"
}
```

**Generated Idempotency Key Format:**
```
{prefix}-{timestamp-millis}
Example: test-run-1699123456789
```

### 3. Message Queue Monitoring
- **List Queues**: Show all MQ queues with their current status
- **Queue Stats**: Display message count, consumers, state
- **Health Check**: Indicate if queue is healthy/unhealthy

**Example Commands:**
```bash
# List all queues
java -jar cli.jar mq list

# Show specific queue details
java -jar cli.jar mq status --queue payment-commands --format json

# List with JSON output
java -jar cli.jar mq list --format json
```

### 4. Kafka Topic Monitoring
- **List Topics**: Show all Kafka topics
- **Event Count**: Display number of events in each topic
- **Partition Info**: Show partition distribution
- **Consumer Lag**: Display consumer group lag if applicable

**Example Commands:**
```bash
# List all topics with event counts
java -jar cli.jar kafka topics

# Show specific topic details
java -jar cli.jar kafka topic --name payment-events --format json

# Show consumer lag
java -jar cli.jar kafka lag --group payment-processor
```

### 5. Docker Container Management
- **List Containers**: Show all containers related to the platform
- **Execute Commands**: Run commands inside specific containers
- **Logs**: Retrieve container logs
- **Stats**: Display container resource usage

**Example Commands:**
```bash
# List all containers
java -jar cli.jar docker list

# Execute command in container
java -jar cli.jar docker exec --container messaging-postgres --command "psql -U postgres -d reliable -c 'SELECT COUNT(*) FROM commands;'"

# Get container logs
java -jar cli.jar docker logs --container payments-worker --tail 100

# Show container stats
java -jar cli.jar docker stats --format json
```

## Application Modes

### Interactive Menu Mode
When run without parameters, display an interactive menu:

```
=== Messaging Platform CLI ===
1. Database Queries
2. API Commands
3. Message Queue Status
4. Kafka Topics
5. Docker Management
6. Exit

Select option:
```

Sub-menus for each option with guided prompts.

### CLI Mode
Direct command execution with optional `--format json` for programmatic use.

## Technical Requirements

### Technology Stack
- **Language**: Java 17+
- **CLI Framework**: Picocli (annotation-based CLI framework)
- **Database**: JDBC with HikariCP connection pool
- **HTTP Client**: Java 11+ HttpClient or OkHttp
- **Docker Integration**: Docker Java API or Process execution
- **Kafka Client**: Apache Kafka Java Client
- **MQ Client**: RabbitMQ Java Client (or appropriate MQ client)
- **JSON Parsing**: Jackson or Gson
- **Build Tool**: Maven
- **Interactive UI**: JLine3 for menu navigation

### Project Structure
```
cli-tool/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/acme/platform/cli/
│   │   │       ├── CliApplication.java (main entry)
│   │   │       ├── commands/
│   │   │       │   ├── DatabaseCommands.java
│   │   │       │   ├── ApiCommands.java
│   │   │       │   ├── MqCommands.java
│   │   │       │   ├── KafkaCommands.java
│   │   │       │   └── DockerCommands.java
│   │   │       ├── service/
│   │   │       │   ├── DatabaseService.java
│   │   │       │   ├── ApiService.java
│   │   │       │   ├── MqService.java
│   │   │       │   ├── KafkaService.java
│   │   │       │   └── DockerService.java
│   │   │       ├── ui/
│   │   │       │   └── InteractiveMenu.java
│   │   │       └── config/
│   │   │           └── CliConfiguration.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── logback.xml
│   └── test/
└── README.md
```

### Configuration
Use `application.properties` or environment variables for:
- Database connection (host, port, username, password, database name)
- API endpoint base URL
- Kafka bootstrap servers
- RabbitMQ connection details
- Docker daemon socket/host

### Maven Dependencies
```xml
<dependencies>
    <!-- CLI Framework -->
    <dependency>
        <groupId>info.picocli</groupId>
        <artifactId>picocli</artifactId>
    </dependency>

    <!-- Interactive UI -->
    <dependency>
        <groupId>org.jline</groupId>
        <artifactId>jline</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
    </dependency>

    <!-- Kafka -->
    <dependency>
        <groupId>org.apache.kafka</groupId>
        <artifactId>kafka-clients</artifactId>
    </dependency>

    <!-- RabbitMQ -->
    <dependency>
        <groupId>com.rabbitmq</groupId>
        <artifactId>amqp-client</artifactId>
    </dependency>

    <!-- Docker -->
    <dependency>
        <groupId>com.github.docker-java</groupId>
        <artifactId>docker-java</artifactId>
    </dependency>

    <!-- HTTP Client -->
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
    </dependency>

    <!-- JSON -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>

    <!-- Logging -->
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- Build executable JAR -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>com.acme.platform.cli.CliApplication</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Implementation Details

### Database Pagination
- Use SQL `OFFSET` and `LIMIT` for pagination
- Store page size as configurable (default 20)
- Calculate total pages from COUNT query
- Cache connection metadata for table validation

### Idempotency Key Generation
```java
String generateIdempotencyKey(String prefix) {
    return String.format("%s-%d", prefix, System.currentTimeMillis());
}
```

### Output Formats
**Table Format (Interactive):**
```
+--------+----------+--------+
| ID     | Status   | Amount |
+--------+----------+--------+
| 1      | PENDING  | 100.00 |
| 2      | COMPLETE | 250.00 |
+--------+----------+--------+
```

**JSON Format (CLI):**
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

## Usage Examples

### Complete Workflow Example
```bash
# 1. Check database state
java -jar cli.jar db query --table commands --format json | jq '.data[] | select(.status=="PENDING")'

# 2. Execute API command
java -jar cli.jar api exec --command ProcessPayment --payload payment.json

# 3. Monitor queue
java -jar cli.jar mq status --queue payment-commands

# 4. Check Kafka events
java -jar cli.jar kafka topic --name payment-events

# 5. Inspect container logs
java -jar cli.jar docker logs --container payments-worker --tail 50
```

### Interactive Mode Example
```
$ java -jar cli.jar

=== Messaging Platform CLI ===
1. Database Queries
2. API Commands
3. Message Queue Status
4. Kafka Topics
5. Docker Management
6. Exit

Select option: 1

=== Database Queries ===
Enter table name: commands
Fetching top 20 records from 'commands'...

[Display results]

Options:
n - Next page
p - Previous page
b - Back to main menu
```

## Testing Strategy
- Unit tests for each service class
- Integration tests with Testcontainers for database, Kafka, RabbitMQ
- Mock API endpoints for API command tests
- CLI command tests using Picocli testing utilities

## Error Handling
- Connection failures: Retry with exponential backoff
- Invalid table names: List available tables
- API errors: Display error message and status code
- Docker errors: Check if Docker daemon is running
- Graceful fallback for missing configuration

## Security Considerations
- Store credentials in environment variables or encrypted config
- Validate SQL table names to prevent injection
- Sanitize Docker command inputs
- Use read-only database user where possible
- Log sensitive operations for audit trail

## Future Enhancements
- Export query results to CSV/Excel
- Scheduled monitoring with alerts
- Batch API command execution
- Docker compose management
- Real-time log streaming
- Configuration profiles for different environments
