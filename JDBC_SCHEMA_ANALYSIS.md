# Messaging Platform JDBC Schema Configuration Analysis

## Executive Summary

The messaging platform currently uses a **multi-database approach** with separate PostgreSQL databases:
- **reliable**: Contains messaging/command framework tables (command, inbox, outbox, process)
- **payments**: Contains domain-specific tables (account, transaction, payment, etc.)

Both databases are in the **same PostgreSQL server**, managed by Flyway migrations from different modules.

---

## 1. JDBC Entity Configuration

### 1.1 Messaging Framework Entities (msg-platform-persistence-jdbc)

**Location**: `/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/model/`

All entities use **Micronaut Data JDBC annotations** (no JPA):

#### OutboxEntity
```java
@MappedEntity("outbox")  // Maps to 'outbox' table in 'reliable' DB
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcOutboxRepository extends OutboxRepository, GenericRepository<OutboxEntity, Long>
```
- **Schema**: reliable
- **Table**: outbox
- **Uses**: UUID for event IDs, JSONB for payload/headers
- **Key Pattern**: Direct table name mapping via @MappedEntity("outbox")

#### InboxEntity
```java
@MappedEntity("inbox")  // Maps to 'inbox' table in 'reliable' DB
public interface JdbcInboxRepository extends InboxRepository, GenericRepository<InboxEntity, InboxEntity.InboxId>
```
- **Schema**: reliable
- **Table**: inbox
- **Uses**: Embedded composite key (messageId, handler)

#### CommandEntity
```java
@MappedEntity("command")  // Maps to 'command' table in 'reliable' DB
public interface JdbcCommandRepository extends CommandRepository, GenericRepository<CommandEntity, UUID>
```
- **Schema**: reliable
- **Table**: command
- **Uses**: UUID primary key, JSONB payload

#### DlqEntity
```java
@MappedEntity("command_dlq")  // Maps to 'command_dlq' table in 'reliable' DB
public interface JdbcDlqRepository extends DlqRepository, GenericRepository<DlqEntity, UUID>
```
- **Schema**: reliable
- **Table**: command_dlq

### 1.2 Payments Domain Entities (msg-platform-payments-worker)

**Location**: `/msg-platform-payments-worker/src/main/java/com/acme/payments/infrastructure/persistence/`

**NOTE**: No Micronaut Data entity classes exist for payments domain. Instead, repositories use **raw JDBC**:

- JdbcAccountRepository - uses javax.sql.DataSource directly
- JdbcPaymentRepository - uses javax.sql.DataSource directly
- JdbcAccountLimitRepository - uses javax.sql.DataSource directly
- JdbcFxContractRepository - uses javax.sql.DataSource directly

**Why?** The payment domain uses more complex aggregate operations with domain models (Account, Payment) that don't map cleanly to simple CRUD repositories. Custom JDBC mapping is more suitable.

### 1.3 Entity Scan / Component Discovery

**How Micronaut discovers repositories:**

1. **No explicit @EntityScan** - Micronaut Data JDBC uses annotation processing at compile-time
2. **Compile-time configuration** in pom.xml:
   ```xml
   <compilerArgs>
     <arg>-Amicronaut.processing.group=com.acme</arg>
     <arg>-Amicronaut.processing.module=msg-platform-persistence-jdbc</arg>
   </compilerArgs>
   ```
3. **Processor** generates implementation classes for @JdbcRepository interfaces
4. **No runtime scanning** - implementations are pre-generated

---

## 2. Flyway Migration Organization

### 2.1 Directory Structure

```
msg-platform-persistence-jdbc/src/main/resources/db/migration/
├── V1__baseline.sql              # Command, Inbox, Outbox, DLQ tables
├── V2__process_manager.sql       # Process Instance & Process Log tables
└── V4__redis_fastpublish_outbox.sql  # Add claimed_at columns

msg-platform-payments-worker/src/main/resources/db/migration/
├── V3__payments_schema.sql       # Account, Transaction, Payment, FX, AccountLimit
└── V4__add_claimed_at_column.sql # Add claimed_at/claimed_by to outbox_bc
```

### 2.2 Migration Content Details

#### Reliable Database (V1, V2, V4)

**V1__baseline.sql** (~60 lines):
- Creates command_status ENUM
- Creates: command, inbox, outbox, command_dlq tables
- Uses UUID primary keys, JSONB for JSON data
- Creates indexes on outbox for dispatch queries

**V2__process_manager.sql** (~48 lines):
- Creates: process_instance, process_log tables
- Supports immutable event sourcing pattern
- process_instance: mutable current state
- process_log: immutable audit trail

**V4__redis_fastpublish_outbox.sql** (~35 lines):
- Adds claimed_at and claimed_by columns to outbox
- Creates conditional indexes for SENDING status
- Uses idempotent ALTER TABLE IF NOT EXISTS

#### Payments Database (V3, V4)

**V3__payments_schema.sql** (~121 lines):
- Creates: account, transaction, account_limit, fx_contract, payment
- Creates: inbox_bc, outbox_bc (domain-specific inbox/outbox)
- Uses UUID primary keys where possible
- Creates foreign keys between aggregates
- Creates business indexes (account_number, customer_id, etc.)

**V4__add_claimed_at_column.sql** (~35 lines):
- Mirrors changes from reliable DB's V4
- Adds claimed_at and claimed_by to outbox_bc

### 2.3 Flyway Configuration

**Manual Docker-based approach** (not Spring Boot Flyway auto-configuration):

Location: `/scripts/`

```bash
# init-databases.sh - Creates PostgreSQL databases at startup
CREATE DATABASE reliable;
CREATE DATABASE payments;

# run-flyway-migrations.sh - Runs migrations manually
/flyway/flyway -configFiles=/etc/flyway/flyway-reliable.conf migrate
/flyway/flyway -configFiles=/etc/flyway/flyway-payments.conf migrate

# flyway-*.conf - Database-specific configurations
flyway.url=jdbc:postgresql://postgres:5432/reliable
flyway.locations=filesystem:/flyway/sql/reliable

flyway.url=jdbc:postgresql://postgres:5432/payments
flyway.locations=filesystem:/flyway/sql/payments
```

**Key Points**:
- **Manual orchestration** via shell scripts in Docker compose
- **Separate Flyway invocations** per database
- **Separate locations** for migration files
- **No schema creation** - databases pre-created by init-databases.sh
- **baselineOnMigrate=true** - Allows migrations on existing databases

### 2.4 Micronaut Flyway Integration

Both applications include Micronaut Flyway dependency:
```xml
<dependency>
  <groupId>io.micronaut.flyway</groupId>
  <artifactId>micronaut-flyway</artifactId>
</dependency>
```

**Configuration in application.yml**:
```yaml
flyway:
  datasources:
    default:
      enabled: true
```

This runs Flyway on application startup for the **default datasource only**:
- API: connects to "reliable" database (via application.yml datasources.default)
- Payments-Worker: connects to "payments" database (via application.yml datasources.default)

---

## 3. Current Application Structure

### 3.1 Database Configuration

**Datasource Configuration**:
- **No multiple datasources** in application config
- **Single "default" datasource per application**
- Each application connects to different database via POSTGRES_DB env var

#### API (msg-platform-api/application.yml)
```yaml
datasources:
  default:
    url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB:reliable}
    # POSTGRES_DB = "reliable" (set in docker-compose.yml)
```

#### Payments Worker (msg-platform-payments-worker/application.yml)
```yaml
datasources:
  default:
    url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB:payments}
    # POSTGRES_DB = "payments" (set in docker-compose.yml)
```

#### General Worker (msg-platform-worker/application.yml)
```yaml
datasources:
  default:
    url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB:reliable}
    # POSTGRES_DB = "reliable"
```

### 3.2 Module Structure

```
msg-platform-persistence-jdbc/
├── src/main/java/com/acme/reliable/persistence/jdbc/
│   ├── model/              # OutboxEntity, InboxEntity, CommandEntity, DlqEntity
│   ├── JdbcOutboxRepository.java
│   ├── JdbcOutboxDao.java      # Alternative Dao implementation
│   ├── JdbcCommandRepository.java
│   ├── JdbcInboxRepository.java
│   ├── JdbcDlqRepository.java
│   ├── process/
│   │   └── JdbcProcessRepository.java  # Raw JDBC with DataSource injection
│   ├── mapper/                 # Entity mappers
│   └── service/               # Service layer
└── src/main/resources/db/migration/
    ├── V1__baseline.sql
    ├── V2__process_manager.sql
    └── V4__redis_fastpublish_outbox.sql

msg-platform-payments-worker/
├── src/main/java/com/acme/payments/
│   ├── domain/
│   │   ├── model/          # Account, Payment domain models
│   │   ├── repository/     # Repository interfaces (no entities!)
│   │   └── service/
│   ├── infrastructure/persistence/
│   │   ├── JdbcAccountRepository.java      # Raw JDBC implementation
│   │   ├── JdbcPaymentRepository.java      # Raw JDBC implementation
│   │   ├── JdbcAccountLimitRepository.java # Raw JDBC implementation
│   │   ├── JdbcFxContractRepository.java   # Raw JDBC implementation
│   │   └── AccountMapper.java
│   └── application/command/                # Command handlers
└── src/main/resources/db/migration/
    ├── V3__payments_schema.sql
    └── V4__add_claimed_at_column.sql

msg-platform-core/
├── src/main/java/com/acme/reliable/
│   ├── repository/         # Repository interfaces (CommandRepository, OutboxRepository, etc.)
│   ├── spi/               # SPI contracts (OutboxDao, EventPublisher, etc.)
│   └── config/            # Configuration POJOs
```

### 3.3 Docker Initialization

**docker-compose.yml**:
```yaml
postgres:
  image: postgres:16-alpine
  volumes:
    - ./scripts/init-databases.sh:/docker-entrypoint-initdb.d/01-init-databases.sh

flyway-init:
  image: flyway/flyway:10.4.1
  volumes:
    - ./migrations/reliable:/flyway/sql/reliable
    - ./migrations/payments:/flyway/sql/payments
    - ./scripts/flyway-config:/etc/flyway
    - ./scripts/run-flyway-migrations.sh:/flyway/run-migrations.sh

api:
  environment:
    POSTGRES_DB: ${POSTGRES_DB}  # Defaults to "reliable"

payments-worker:
  environment:
    POSTGRES_DB: payments        # Explicitly set to "payments"
```

---

## 4. Repository/DAO Patterns

### 4.1 Messaging Framework Repositories (JdbcRepository Pattern)

**Architecture**:
```
Domain Interface (core module)
    ↓
JDBC Repository (persistence-jdbc module)
    ↓
Generated Implementation (compile-time by Micronaut Data)
```

**Example - OutboxRepository**:
```java
// Core Interface
public interface OutboxRepository {
  void save(OutboxEntity outbox);
  Optional<OutboxEntity> findById(Long id);
  // ...
}

// JDBC Implementation
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcOutboxRepository 
    extends OutboxRepository, GenericRepository<OutboxEntity, Long> {
  
  @Query(nativeQuery = true)
  Optional<OutboxEntity> claimOne(long id, String claimer);
  // ...
}

// Generated Implementation (by Micronaut Data processor)
// $JdbcOutboxRepositoryImpl_$Intercepted
```

**Key Features**:
- Query methods use **native SQL** (nativeQuery = true)
- Mix of declarative (@Query) and dynamic (CRUD)
- Direct injection into services/controllers via Singleton pattern

### 4.2 Payments Domain Repositories (Raw JDBC Pattern)

**Architecture**:
```
Domain Interface (payments-worker module)
    ↓
Raw JDBC Implementation (DataSource injection)
```

**Example - AccountRepository**:
```java
public interface AccountRepository {
  void save(Account account);
  Optional<Account> findById(UUID accountId);
}

@Singleton
@RequiredArgsConstructor
public class JdbcAccountRepository implements AccountRepository {
  private final DataSource dataSource;  // Injected by Micronaut
  
  @Override
  public void save(Account account) {
    try (Connection conn = dataSource.getConnection()) {
      // Raw PreparedStatement JDBC
      // Manages transactions via @Transactional annotation
    }
  }
}
```

**Key Differences**:
- **Direct DataSource injection** instead of repository generation
- **Connection management** via try-with-resources
- **Custom mapping** from domain models to ResultSet
- **No transaction magic** - uses @Transactional annotation
- **No query DSL** - pure SQL strings

### 4.3 Multi-Database Pattern

**Current implementation uses application-level database selection**:

1. **Environment-based selection**:
   - API container: POSTGRES_DB=reliable
   - Payments-Worker container: POSTGRES_DB=payments
   - General Worker: POSTGRES_DB=reliable

2. **Single DataSource per application**:
   - Each app has `datasources.default` pointing to one database
   - No multi-tenant or runtime database switching
   - Clean code separation at deployment level

3. **No multi-datasource configuration**:
   - No Micronaut `@HibernateProperties` or similar
   - No routing logic in repositories
   - Physical databases keep concerns separate

---

## 5. Key Architectural Patterns

### 5.1 Separation of Concerns

```
┌─────────────────────────────────────────────────────────────┐
│ Domain Model Layer                                          │
│ - Account, Payment (in payments domain)                     │
│ - Command, Event, Process (in core)                         │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│ Repository Interface Layer                                  │
│ - AccountRepository, PaymentRepository (interfaces)         │
│ - CommandRepository, OutboxRepository (interfaces)          │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│ Persistence Implementation Layer                            │
│ - JdbcAccountRepository (raw JDBC, payments DB)            │
│ - JdbcPaymentRepository (raw JDBC, payments DB)            │
│ - JdbcCommandRepository (Micronaut Data, reliable DB)      │
│ - JdbcOutboxRepository (Micronaut Data, reliable DB)       │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│ Database Layer                                              │
│ - PostgreSQL "reliable" database (messaging framework)      │
│ - PostgreSQL "payments" database (domain-specific)          │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 Deployment Topology

```
Docker Compose:
┌─────────────────────────────────────────────────────┐
│ PostgreSQL Server                                   │
│ ├─ reliable database   (for messaging framework)   │
│ └─ payments database   (for payments domain)       │
└──────────────┬──────────────────────────────────────┘
               │
        ┌──────┴──────┬────────────┐
        │             │            │
    ┌───▼────┐  ┌────▼────┐  ┌──▼────────┐
    │   API   │  │ Worker  │  │ Payments  │
    │         │  │         │  │  Worker   │
    │ DB=     │  │ DB=     │  │ DB=       │
    │reliable │  │reliable │  │ payments  │
    └─────────┘  └─────────┘  └───────────┘
```

### 5.3 Transaction Management

**Messaging Framework**:
- Uses Micronaut Data transaction support
- No explicit transaction management in repositories
- Declarative via repository method returns

**Payments Domain**:
- Uses @Transactional annotation on methods
- Manual transaction scope management
- Ambient transaction support in tests

---

## 6. What Would Need to Change for Single-Schema Approach

### 6.1 Schema Design Changes

**Current**:
- Platform schema: command, inbox, outbox, command_dlq, process_instance, process_log
- Payment schema: account, transaction, payment, fx_contract, account_limit, inbox_bc, outbox_bc

**Proposed Single-Schema**:
```sql
-- Option A: Same database, separate schema namespaces
CREATE SCHEMA platform;   -- command, inbox, outbox, process_instance, etc.
CREATE SCHEMA payments;   -- account, transaction, payment, etc.

-- Option B: Same database, same schema, prefixed tables
CREATE TABLE cmd_command (...)
CREATE TABLE cmd_inbox (...)
CREATE TABLE cmd_outbox (...)
CREATE TABLE pmt_account (...)
CREATE TABLE pmt_transaction (...)
CREATE TABLE pmt_payment (...)
```

### 6.2 Repository Configuration Changes

**Current Micronaut Data Mapping**:
```java
@MappedEntity("outbox")  // Works because "outbox" table exists in connected DB
public interface JdbcOutboxRepository extends OutboxRepository ...
```

**To support single schema/database**:

**Option A - Schema-aware mapping**:
```java
@MappedEntity(value = "outbox", schema = "platform")
public interface JdbcOutboxRepository extends OutboxRepository ...
```
⚠️ **Problem**: Micronaut Data JDBC doesn't support schema specification in @MappedEntity

**Option B - Table name prefixes**:
```java
@MappedEntity("cmd_outbox")  // Explicit prefix
public interface JdbcOutboxRepository extends OutboxRepository ...
```
✅ Works but loses schema-level isolation

**Option C - Multiple datasources with routing**:
```yaml
datasources:
  default:     # Point to single database
  platform:    # Alternative datasource
  payments:    # Alternative datasource
```
Then use @DataSource("platform") or @DataSource("payments") on repositories
⚠️ **Problem**: Not directly supported by Micronaut Data JDBC

### 6.3 Flyway Configuration Changes

**Current**:
- Separate migration directories: `/db/migration/V1...` and `/db/migration/V3...`
- Separate flyway invocations per database
- baselineOnMigrate allows independent migrations

**For single database**:
- Merge migration files with proper sequencing
- Single Flyway configuration
- Need to handle migration ordering across domains

Example merged sequence:
```
V1__baseline.sql                    (platform schema)
V2__process_manager.sql             (platform schema)
V3__payments_schema.sql             (payments schema)
V4__redis_fastpublish_outbox.sql   (both schemas)
V5__add_new_feature.sql
```

### 6.4 Application Configuration Changes

**Current application.yml**:
```yaml
datasources:
  default:
    url: jdbc:postgresql://localhost:5432/reliable
    # or
    url: jdbc:postgresql://localhost:5432/payments
```

**For single database with schemas**:
```yaml
datasources:
  default:
    url: jdbc:postgresql://localhost:5432/messaging-unified
    # No schema specified - would use public schema or require schema qualification
```

**Problem**: Micronaut Data JDBC repositories don't support schema qualification in table names

### 6.5 Code-Level Changes

#### Option 1: Keep separate repositories, change datasource routing
```java
// All repositories still use @JdbcRepository(dialect = POSTGRES)
// But runtime determines which schema via:
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcOutboxRepository extends OutboxRepository ...

// Runtime routing logic (MISSING from current codebase):
@Singleton
public class DatasourceRouter {
  public DataSource getDataSourceForDomain(String domain) {
    // Route to appropriate schema or database
  }
}
```

#### Option 2: Prefix all payment tables, keep same database
```java
@MappedEntity("pmt_account")     // Changed from "account"
public class AccountEntity { }

@MappedEntity("pmt_transaction")  // Changed from "transaction"
public class TransactionEntity { }

@MappedEntity("pmt_payment")      // Changed from "payment"  
public class PaymentEntity { }
```

#### Option 3: Use custom @Query to specify schema (workaround)
```java
@Query(value = "SELECT * FROM payments.account WHERE id = ?", nativeQuery = true)
Optional<Account> findById(UUID id);
```
But this defeats the purpose of ORM/repository abstraction.

---

## 7. Summary Table

| Aspect | Current Implementation | Change Required? |
|--------|----------------------|------------------|
| **Multiple Databases** | Yes (reliable, payments) | No if consolidating |
| **Multiple Schemas** | No (uses default public schema) | Yes, to keep separate |
| **Repository Pattern** | Micronaut Data JDBC (messaging), Raw JDBC (payments) | Unify to one approach |
| **Datasource Routing** | Environment-based per deployment | Runtime routing if single DB |
| **Flyway Configuration** | Separate per database | Merge or use callbacks |
| **Table Naming** | Implicit via @MappedEntity("table") | Prefix-based or schema-qualified |
| **Entity Classes** | Platform: Yes, Payments: No | Create Payment entities or keep POJO mapping |
| **Transaction Management** | Mixed (@Transactional vs implicit) | Standardize approach |
| **Code Separation** | Module-based (domain, infrastructure) | Keep; only DB connection changes |

---

## 8. Recommendations for Clean Schema Separation

### Option A: PostgreSQL Schemas (Recommended)
**Pros**:
- Logical separation at database level
- Different permission models per schema
- Cleaner namespace isolation
- Tables can have same names in different schemas

**Cons**:
- Requires custom datasource routing (not built-in to Micronaut Data)
- Query syntax needs schema qualification

**Implementation**:
```sql
-- Migration file
CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS payments;

CREATE TABLE platform.command (...);
CREATE TABLE platform.inbox (...);
CREATE TABLE payments.account (...);
```

Then in code, either:
1. Use schema-qualified table names in @Query methods (for complex queries)
2. Create a DatasourceRouter that sets `search_path` per request
3. Wrap repositories with schema-aware proxy

### Option B: Table Prefix Convention (Simpler)
**Pros**:
- No custom routing logic needed
- Works with existing Micronaut Data JDBC
- Easy to understand naming

**Cons**:
- Less isolation; all tables in same namespace
- Slightly longer table names
- Still in same schema

**Implementation**:
```java
@MappedEntity("cmd_command")
@MappedEntity("cmd_inbox")
@MappedEntity("cmd_outbox")
@MappedEntity("pmt_account")
@MappedEntity("pmt_transaction")
@MappedEntity("pmt_payment")
```

### Option C: Keep Current Multi-Database Approach
**Pros**:
- Zero code changes required
- Clear separation at infrastructure level
- Different backup/replication strategies per DB

**Cons**:
- Requires multiple PostgreSQL databases
- More operational complexity
- Transactions can't span domains (good for isolation, bad for consistency)

**This is the current approach - consider if this is actually a problem**

---

## Key Files Reference

### Entity/Repository Definitions
- `/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/model/*.java` - Messaging entities
- `/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/JdbcCommandRepository.java` - Command repository
- `/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/JdbcOutboxRepository.java` - Outbox repository  
- `/msg-platform-payments-worker/src/main/java/com/acme/payments/infrastructure/persistence/Jdbc*Repository.java` - Payment repositories

### Configuration
- `/msg-platform-api/src/main/resources/application.yml` - API datasource config
- `/msg-platform-payments-worker/src/main/resources/application.yml` - Payments worker datasource config
- `/msg-platform-persistence-jdbc/pom.xml` - Micronaut Data processor configuration

### Migrations
- `/msg-platform-persistence-jdbc/src/main/resources/db/migration/V1__baseline.sql` - Platform schema baseline
- `/msg-platform-persistence-jdbc/src/main/resources/db/migration/V2__process_manager.sql` - Process tables
- `/msg-platform-payments-worker/src/main/resources/db/migration/V3__payments_schema.sql` - Payment schema
- `/scripts/init-databases.sh` - PostgreSQL database creation
- `/scripts/run-flyway-migrations.sh` - Flyway orchestration
- `/scripts/flyway-config/flyway-*.conf` - Flyway per-database config

### Process Repository (Unified Raw JDBC)
- `/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/process/JdbcProcessRepository.java` - Process CRUD with direct DataSource usage

