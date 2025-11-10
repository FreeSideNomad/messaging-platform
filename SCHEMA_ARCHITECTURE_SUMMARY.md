# Schema Architecture Summary - Quick Reference

## Current State Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                        PostgreSQL Server                              │
│  ┌────────────────────────┐  ┌────────────────────────────────────┐  │
│  │   reliable database    │  │    payments database               │  │
│  │                        │  │                                    │  │
│  │  Tables (public schema)│  │  Tables (public schema)            │  │
│  │  ├─ command           │  │  ├─ account                        │  │
│  │  ├─ inbox             │  │  ├─ transaction                    │  │
│  │  ├─ outbox            │  │  ├─ account_limit                  │  │
│  │  ├─ command_dlq       │  │  ├─ fx_contract                    │  │
│  │  ├─ process_instance  │  │  ├─ payment                        │  │
│  │  └─ process_log       │  │  ├─ inbox_bc                       │  │
│  │                        │  │  └─ outbox_bc                      │  │
│  └────────────────────────┘  └────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
        │                               │
        │                               │
        ├──────────────┬────────────────┘
        │              │
     ┌──▼──┐      ┌────▼──────┐
     │ API │      │ Payments   │
     │     │      │ Worker     │
     │ DB= │      │ DB=        │
     │reli-│      │payments    │
     │able │      │            │
     └─────┘      └────────────┘
        │              │
        │              │
     ┌──▼──────────────▼────┐
     │  General Worker      │
     │  DB=reliable         │
     └──────────────────────┘
```

## Repository Pattern Comparison

### Messaging Framework (Micronaut Data JDBC)

```
Domain Layer
   ↓
OutboxRepository (interface in msg-platform-core)
   ↓
@JdbcRepository annotation
   ↓
JdbcOutboxRepository (extends OutboxRepository)
   ↓
Compile-time: Micronaut Data processor generates implementation
   ↓
Runtime: Auto-wired as Singleton bean
```

**Key Characteristics**:

- Entity classes with @MappedEntity annotations
- Declarative @Query methods with native SQL
- No DataSource injection (automatic handling)
- Transaction management implicit
- Entities: OutboxEntity, InboxEntity, CommandEntity, DlqEntity

**Location**: `/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/`

---

### Payments Domain (Raw JDBC)

```
Domain Layer
   ↓
AccountRepository (interface in payments module)
   ↓
JdbcAccountRepository (raw JDBC implementation)
   ↓
Direct DataSource injection by Micronaut
   ↓
Manual JDBC: Connection, PreparedStatement, ResultSet
   ↓
Custom mapping to domain models (Account, Payment)
```

**Key Characteristics**:

- NO entity classes (uses domain models directly)
- Raw JDBC: Connection, PreparedStatement, ResultSet
- Direct DataSource injection
- Manual transaction management via @Transactional
- More flexible aggregate mapping
- No automatic query generation

**Location**: `/msg-platform-payments-worker/src/main/java/com/acme/payments/infrastructure/persistence/`

---

## Entity/Table Mapping

### Reliable Database (Messaging Framework)

| Entity Class  | Table Name       | Module           | Pattern                |
|---------------|------------------|------------------|------------------------|
| OutboxEntity  | outbox           | persistence-jdbc | @MappedEntity          |
| InboxEntity   | inbox            | persistence-jdbc | @MappedEntity          |
| CommandEntity | command          | persistence-jdbc | @MappedEntity          |
| DlqEntity     | command_dlq      | persistence-jdbc | @MappedEntity          |
| (none)        | process_instance | persistence-jdbc | Raw SQL (V2 migration) |
| (none)        | process_log      | persistence-jdbc | Raw SQL (V2 migration) |

### Payments Database (Domain Entities)

| Domain Model | Table Name    | Repository                 | Pattern                |
|--------------|---------------|----------------------------|------------------------|
| Account      | account       | JdbcAccountRepository      | Raw JDBC               |
| Transaction  | transaction   | JdbcAccountRepository      | Raw JDBC               |
| AccountLimit | account_limit | JdbcAccountLimitRepository | Raw JDBC               |
| FxContract   | fx_contract   | JdbcFxContractRepository   | Raw JDBC               |
| Payment      | payment       | JdbcPaymentRepository      | Raw JDBC               |
| (none)       | inbox_bc      | (none)                     | Raw SQL (V3 migration) |
| (none)       | outbox_bc     | (none)                     | Raw SQL (V3 migration) |

---

## Flyway Migration Strategy

### Current Approach: Multi-Database

```
┌─────────────────────┬──────────────────────┐
│  reliable database  │  payments database   │
├─────────────────────┼──────────────────────┤
│ V1__baseline.sql    │ V3__payments...sql   │
│ V2__process...sql   │ V4__add_claimed...   │
│ V4__redis...sql     │                      │
├─────────────────────┴──────────────────────┤
│ Flyway Orchestration (Docker)             │
│ ├─ init-databases.sh: CREATE DATABASE     │
│ ├─ run-flyway-migrations.sh               │
│ │  ├─ flyway-reliable.conf (V1,V2,V4)    │
│ │  └─ flyway-payments.conf (V3,V4)       │
│ └─ Both migrations run independently      │
└─────────────────────────────────────────────┘
```

**Advantages**:

- Clear separation per database
- Independent migration history
- Different baseline versions possible
- Decoupled from application startup

**Disadvantages**:

- Extra Docker orchestration overhead
- Must coordinate migration versions (V3 vs V4 timing)
- Two independent Flyway tracking tables
- Complex multi-database deployments

---

## Datasource Configuration

### Application.yml Pattern

Each application points to single database:

**API** (msg-platform-api/application.yml):

```yaml
datasources:
  default:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:reliable}
```

**Payments Worker** (msg-platform-payments-worker/application.yml):

```yaml
datasources:
  default:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:payments}
```

**Environment Variables** (docker-compose.yml):

- API: `POSTGRES_DB=reliable` (default)
- Payments-Worker: `POSTGRES_DB=payments` (explicit)

**No runtime switching** - database selection is deployment-time decision

---

## Key Design Decisions

| Aspect           | Decision                | Rationale                                    |
|------------------|-------------------------|----------------------------------------------|
| **Databases**    | 2 (reliable + payments) | Bounded context isolation                    |
| **Schemas**      | public (default)        | Simplified, each DB is isolated              |
| **Connection**   | Environment-based       | Clean deployment separation                  |
| **Repositories** | 2 patterns              | Pragmatic per use-case                       |
| **Migrations**   | Orchestrated            | Manual control, Docker-native                |
| **Entities**     | Only for ORM tables     | Raw JDBC for complex aggregates              |
| **Transactions** | Mixed approach          | Framework implicit + explicit @Transactional |

---

## To Support Single-Schema Architecture

### Option A: PostgreSQL Schemas (Recommended)

```sql
CREATE SCHEMA platform;  -- cmd_*, process_*
CREATE SCHEMA payments;  -- pmt_*, account_*, transaction_*

-- In repositories (workaround for Micronaut Data limitation):
@Query(value = "SELECT * FROM platform.outbox WHERE id = ?", nativeQuery = true)
```

### Option B: Table Prefixes (Simplest)

```java
@MappedEntity("cmd_command")      // Instead of "command"
@MappedEntity("cmd_outbox")       // Instead of "outbox"
@MappedEntity("pmt_account")      // Instead of "account"
@MappedEntity("pmt_payment")      // Instead of "payment"
```

### Option C: Keep Current (No Changes)

- Continue with 2 databases
- Already well-designed
- Questionable if consolidation is needed

---

## Migration File Reference

### Reliable Database

- `/msg-platform-persistence-jdbc/src/main/resources/db/migration/V1__baseline.sql` (1,720 bytes)
    - command, command_status ENUM, inbox, outbox, command_dlq tables

- `/msg-platform-persistence-jdbc/src/main/resources/db/migration/V2__process_manager.sql` (2,283 bytes)
    - process_instance, process_log tables for event sourcing

- `/msg-platform-persistence-jdbc/src/main/resources/db/migration/V4__redis_fastpublish_outbox.sql` (1,444 bytes)
    - Add claimed_at, claimed_by columns to outbox

### Payments Database

- `/msg-platform-payments-worker/src/main/resources/db/migration/V3__payments_schema.sql` (4,491 bytes)
    - account, transaction, account_limit, fx_contract, payment, inbox_bc, outbox_bc tables

- `/msg-platform-payments-worker/src/main/resources/db/migration/V4__add_claimed_at_column.sql` (1,174 bytes)
    - Add claimed_at, claimed_by columns to outbox_bc

---

## Code Location Reference

### Entities & Repository Interfaces

- `/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/model/`
    - OutboxEntity.java
    - InboxEntity.java
    - CommandEntity.java
    - DlqEntity.java

### JDBC Repositories (Messaging Framework)

- `/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/`
    - JdbcOutboxRepository.java
    - JdbcOutboxDao.java
    - JdbcCommandRepository.java
    - JdbcInboxRepository.java
    - JdbcDlqRepository.java
    - process/JdbcProcessRepository.java (raw JDBC)

### JDBC Repositories (Payments Domain)

- `/msg-platform-payments-worker/src/main/java/com/acme/payments/infrastructure/persistence/`
    - JdbcAccountRepository.java
    - JdbcPaymentRepository.java
    - JdbcAccountLimitRepository.java
    - JdbcFxContractRepository.java

### Configuration

- `/scripts/init-databases.sh` - Database creation
- `/scripts/run-flyway-migrations.sh` - Migration orchestration
- `/scripts/flyway-config/flyway-reliable.conf` - Reliable DB migration config
- `/scripts/flyway-config/flyway-payments.conf` - Payments DB migration config

---

## Critical Limitations of Micronaut Data JDBC

1. **No schema support in @MappedEntity** - Can't specify schema name
2. **No multi-datasource routing** - No built-in @DataSource annotation support
3. **No schema qualification in queries** - Repositories assume single schema
4. **Compile-time generation** - Can't dynamically change at runtime

**Workaround**: Use raw JDBC (like payments domain) or schema-qualified table names in @Query methods

