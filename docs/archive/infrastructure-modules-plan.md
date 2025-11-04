# Infrastructure Modules Refactoring Plan

## Overview
Extract technology-specific infrastructure into reusable library modules following Hexagonal Architecture.

## Current Architecture Issues
- IBM MQ infrastructure duplicated in API and Worker
- No clear separation between domain logic and infrastructure
- Hard to test in isolation
- Hard to swap implementations (e.g., ActiveMQ instead of IBM MQ)

## Target Architecture

```
messaging-platform/
├── msg-platform-core/                    # Domain + Ports (interfaces)
│   ├── domain/                           # Entities: Command, Inbox, Outbox, Dlq
│   ├── core/                             # Business logic: CommandBus, Executor, Outbox
│   ├── spi/                              # Ports (interfaces): CommandService, EventPublisher, CommandQueue
│   └── config/                           # Configuration POJOs
│
├── msg-platform-db-postgresql/           # Database Persistence Adapter - PostgreSQL (NEW)
│   ├── repositories/                     # Micronaut Data JDBC repositories
│   ├── services/                         # CommandService, InboxService, OutboxService, DlqService impls
│   └── migrations/                       # Flyway SQL scripts
│
├── msg-platform-messaging-ibmmq/         # Command Messaging Adapter - IBM MQ (NEW)
│   ├── producer/                         # JmsCommandQueue implementation
│   ├── consumer/                         # CommandConsumers (JMS listeners)
│   ├── config/                           # IbmMqFactoryProvider, MqQueueInitializer
│   └── mappers/                          # Mappers (JMS Message → Envelope)
│
├── msg-platform-events-kafka/            # Event Publishing Adapter - Kafka (NEW)
│   ├── producer/                         # KafkaEventPublisher implementation
│   └── config/                           # KafkaProducerFactory, KafkaTopicInitializer
│
├── msg-platform-api/                     # Application: REST API
│   ├── web/                              # Controllers only
│   ├── config/                           # Application-specific config
│   └── dependencies:                     # core + db-postgresql + messaging-ibmmq (producer only)
│
└── msg-platform-worker/                  # Application: Command Processor
    ├── handlers/                         # Command handlers only
    ├── config/                           # Application-specific config
    └── dependencies:                     # core + db-postgresql + messaging-ibmmq (consumer) + events-kafka
```

## Benefits
✅ **No Code Duplication**: MQ infrastructure shared between API and Worker
✅ **Clear Separation**: Domain logic (core) vs Infrastructure (adapters)
✅ **Testability**: Can test with in-memory implementations
✅ **Flexibility**: Easy to swap IBM MQ for ActiveMQ or RabbitMQ
✅ **Reusability**: Other applications can use these infrastructure modules
✅ **Single Responsibility**: Each module has one technology concern

## Module Responsibilities

### msg-platform-core (no changes)
- Domain entities (Command, Inbox, Outbox, Dlq)
- Business logic (CommandBus, Executor, Outbox)
- SPI interfaces (ports)
- Configuration POJOs
- **Dependencies**: Micronaut core, Lombok, Jackson
- **NO infrastructure dependencies** (no JDBC, no JMS, no Kafka)

### msg-platform-db-postgresql (NEW)
**Purpose**: Database Persistence Adapter
**Technology**: PostgreSQL

Implements database persistence for the domain.

**Provides**:
- `CommandRepository`, `InboxRepository`, `OutboxRepository`, `DlqRepository`
- `CommandService`, `InboxService`, `OutboxService`, `DlqService` implementations
- Flyway database migrations
- PostgreSQL-specific optimizations (SKIP LOCKED, JSONB)

**Dependencies**:
- `msg-platform-core` (domain entities, SPI interfaces)
- Micronaut Data JDBC
- PostgreSQL driver
- Flyway

**Configuration Required** (via application.yml or .env):
```yaml
datasources:
  default:
    url: ${JDBC_URL}
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    maximum-pool-size: ${HIKARI_MAX_POOL_SIZE:200}
```

### msg-platform-messaging-ibmmq (NEW)
**Purpose**: Command Messaging Adapter
**Technology**: IBM MQ (JMS)

Implements command messaging infrastructure for reliable command delivery.

**Provides**:
- `JmsCommandQueue` - implements `CommandQueue` SPI (producer)
- `CommandConsumers` - JMS listeners for consuming commands (consumer)
- `IbmMqFactoryProvider` - ConnectionFactory configuration
- `MqQueueInitializer` - Queue validation on startup
- `Mappers` - JMS Message ↔ Envelope conversion

**Dependencies**:
- `msg-platform-core` (Envelope, CommandQueue SPI)
- Micronaut JMS
- IBM MQ Client

**Configuration Required**:
```yaml
jms:
  consumers:
    enabled: ${JMS_CONSUMERS_ENABLED:false}  # true for Worker, false for API

# Environment variables:
MQ_HOST, MQ_PORT, MQ_QMGR, MQ_CHANNEL, MQ_USER, MQ_PASS
```

**Features**:
- Conditional consumer activation via `jms.consumers.enabled`
- Session pooling for high throughput
- Queue validation on startup
- Automatic queue creation (if permissions allow)

### msg-platform-events-kafka (NEW)
**Purpose**: Event Publishing Adapter
**Technology**: Apache Kafka

Implements event publishing infrastructure for domain events and command replies.

**Provides**:
- `KafkaEventPublisher` - implements `EventPublisher` SPI
- `KafkaProducerFactory` - Kafka producer configuration
- `KafkaTopicInitializer` - Topic validation/creation on startup

**Dependencies**:
- `msg-platform-core` (EventPublisher SPI)
- Micronaut Kafka

**Configuration Required**:
```yaml
kafka:
  bootstrap:
    servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

### msg-platform-api (simplified)
REST API application that wires infrastructure together.

**Contains**:
- `CommandController` - REST endpoints
- `ApiApplication` - main class
- `application.yml` - configuration

**Dependencies**:
- `msg-platform-core` (domain logic)
- `msg-platform-db-postgresql` (persistence)
- `msg-platform-messaging-ibmmq` (JMS producer only)
- `micronaut-http-server-netty` (HTTP)

**Configuration**:
```yaml
jms:
  consumers:
    enabled: false  # API only produces to MQ, doesn't consume
```

### msg-platform-worker (simplified)
Command processor application that wires infrastructure together.

**Contains**:
- `CreateUserHandler` and other command handlers
- `WorkerApplication` - main class
- `application.yml` - configuration

**Dependencies**:
- `msg-platform-core` (domain logic)
- `msg-platform-db-postgresql` (persistence)
- `msg-platform-messaging-ibmmq` (JMS consumer)
- `msg-platform-events-kafka` (event publishing)
- `micronaut-http-server-netty` (minimal - health checks only)

**Configuration**:
```yaml
jms:
  consumers:
    enabled: true  # Worker consumes from MQ and processes commands
```

## Migration Steps

### Step 1: Create msg-platform-db-postgresql module
1. Create module structure
2. Move repositories from core → db-postgresql
3. Move services (implementations) from core → db-postgresql
4. Move Flyway migrations from core → db-postgresql
5. Update core to keep only domain entities and SPI interfaces
6. Update pom.xml dependencies

### Step 2: Create msg-platform-messaging-ibmmq module
1. Create module structure
2. Move JmsCommandQueue from API → messaging-ibmmq
3. Move CommandConsumers from Worker → messaging-ibmmq
4. Move IbmMqFactoryProvider, Mappers, MqQueueInitializer → messaging-ibmmq
5. Make CommandConsumers conditional on `jms.consumers.enabled`
6. Update pom.xml dependencies

### Step 3: Create msg-platform-events-kafka module
1. Create module structure
2. Move KafkaEventPublisher from Worker → events-kafka
3. Move KafkaProducerFactory, KafkaTopicInitializer → events-kafka
4. Update pom.xml dependencies

### Step 4: Update msg-platform-api
1. Remove all infrastructure code
2. Add dependencies: core, db-postgresql, messaging-ibmmq
3. Keep only: CommandController, ApiApplication, application.yml
4. Set `jms.consumers.enabled: false`

### Step 5: Update msg-platform-worker
1. Remove all infrastructure code
2. Add dependencies: core, db-postgresql, messaging-ibmmq, events-kafka
3. Keep only: handlers, WorkerApplication, application.yml
4. Set `jms.consumers.enabled: true`

### Step 6: Update parent POM
1. Add new modules to `<modules>` section
2. Add dependency management for infrastructure modules

### Step 7: Testing
1. Verify all modules compile independently
2. Run API and Worker to verify functionality
3. Update documentation (README, QUICKSTART)

## Expected Outcome

**Before**:
- API: 15+ classes (controllers + infrastructure)
- Worker: 12+ classes (handlers + infrastructure)
- Duplicated MQ code
- Mixed concerns

**After**:
- API: 2-3 classes (controllers + main)
- Worker: 3-4 classes (handlers + main)
- Infrastructure modules: reusable, testable, swappable
- Clear separation of concerns

## Future Benefits

**Easy to add new adapters**:
- `msg-platform-messaging-activemq` - ActiveMQ instead of IBM MQ
- `msg-platform-messaging-rabbitmq` - RabbitMQ messaging
- `msg-platform-db-mssql` - MS SQL Server instead of PostgreSQL
- `msg-platform-db-mysql` - MySQL instead of PostgreSQL
- `msg-platform-events-pulsar` - Apache Pulsar instead of Kafka
- `msg-platform-events-rabbitmq` - RabbitMQ for events

**Easy to add new applications**:
- `msg-platform-cli` - CLI tool using core + db-postgresql
- `msg-platform-scheduler` - Scheduled jobs using core + db-postgresql
- `msg-platform-grpc-api` - gRPC API using core + db-postgresql + messaging-ibmmq

**Easy to test**:
- Core module: pure business logic tests (no infrastructure)
- Infrastructure modules: integration tests with Testcontainers
- Application modules: E2E tests with real infrastructure

## Testing Strategy Updates

After refactoring, update test-strategy-plan.md:

**Unit Tests** (no infrastructure):
- `msg-platform-core`: Test CommandBus, Executor, Outbox with mocks
- No database, no JMS, no Kafka required

**Integration Tests** (with infrastructure):
- `msg-platform-db-postgresql`: Test repositories with Testcontainers PostgreSQL
- `msg-platform-messaging-ibmmq`: Test JMS with Testcontainers ActiveMQ
- `msg-platform-events-kafka`: Test Kafka with Testcontainers Kafka

**E2E Tests** (full stack):
- `msg-platform-api`: Test REST → DB → JMS flow
- `msg-platform-worker`: Test JMS → Handler → Kafka flow

## Questions to Consider

1. **Should OutboxRelay stay in core or move to db-postgresql?**
   - It uses `OutboxService` (SPI), so it could stay in core
   - But it's tied to database sweep logic, so db-postgresql makes sense
   - **Recommendation**: Move to db-postgresql module

2. **Should domain entities stay in core or move to postgresql?**
   - Entities are JPA-annotated, which is PostgreSQL-specific
   - But they're also used by business logic in core
   - **Recommendation**: Keep in core, but make annotations optional via Lombok

3. **Should configuration POJOs stay in core?**
   - TimeoutConfig, MessagingConfig are used by both core and infrastructure
   - **Recommendation**: Keep in core (they're just POJOs, no infrastructure)

4. **Should we support multiple database implementations?**
   - If yes, need `msg-platform-postgres` AND `msg-platform-mysql`
   - If no, can keep simpler structure
   - **Recommendation**: Start with PostgreSQL only, add others if needed

## Conclusion

This refactoring creates a **production-ready, enterprise-grade architecture**:
- Clean separation of concerns
- Highly testable
- Reusable infrastructure modules
- Easy to extend with new technologies
- Follows Hexagonal Architecture principles

Ready to execute?
