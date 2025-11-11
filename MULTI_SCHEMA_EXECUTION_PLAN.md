# Multi-Schema Architecture Execution Plan

**Objective**: Consolidate platform messaging and payments domain into single `payments` database using two PostgreSQL
schemas (`platform` and `payments`) with proper dependency inversion and clean architecture.

**Scope**: JDBC layer, persistence module, Flyway migrations only. Core services and application logic remain unchanged.

---

## Phase 1: Prepare and Organize (No Breaking Changes)

### Step 1.1: Create Repository Interface Package Structure in msg-platform-core

**Location**: `msg-platform-core/src/main/java/com/acme/reliable/repository/`

Create new directories (interfaces only, no implementation):

```
com/acme/reliable/repository/
├── platform/
│   ├── CommandRepository.java
│   ├── InboxRepository.java
│   ├── OutboxRepository.java
│   ├── ProcessInstanceRepository.java
│   └── ProcessLogRepository.java
└── payments/
    ├── AccountRepository.java
    ├── TransactionRepository.java
    ├── PaymentRepository.java
    ├── FxContractRepository.java
    └── AccountLimitRepository.java
```

**Task**: Create all repository interfaces in msg-platform-core

- Copy method signatures from existing JdbcOutboxRepository, JdbcOutboxDao, etc.
- Keep only interface definitions
- No implementation details

**Files to Create**:

1. `CommandRepository.java` - save, findById, findByStatus, etc.
2. `InboxRepository.java` - insertIfAbsent, findByMessageId, etc.
3. `OutboxRepository.java` - insert, findNew, claim, markPublished, etc.
4. `ProcessInstanceRepository.java` - insert, findById, update, etc.
5. `ProcessLogRepository.java` - insert, findByProcessId, etc.
6. `AccountRepository.java` - save, findById, findByCustomerId, etc.
7. `TransactionRepository.java` - save, findById, findByAccountId, etc.
8. `PaymentRepository.java` - save, findById, findByStatus, etc.
9. `FxContractRepository.java` - save, findById, etc.
10. `AccountLimitRepository.java` - save, findById, etc.

**Verification**: All interfaces compile successfully

---

### Step 1.2: Create Service Package Structure in msg-platform-core

**Location**: `msg-platform-core/src/main/java/com/acme/reliable/service/`

Create new directories for services that depend on repository interfaces:

```
com/acme/reliable/service/
├── platform/
│   ├── OutboxService.java
│   ├── InboxService.java
│   └── CommandService.java
└── payments/
    ├── PaymentService.java
    └── AccountService.java
```

**Task**: Create service interfaces/implementations that depend on repository interfaces

- Move existing OutboxService, InboxService logic to depend on repository interfaces
- Create new PaymentService, AccountService
- Keep services in core module (persistence-agnostic)

**Files to Create/Move**:

1. `OutboxService.java` - depends on OutboxRepository interface
2. `InboxService.java` - depends on InboxRepository interface
3. `CommandService.java` - depends on CommandRepository interface
4. `PaymentService.java` - depends on AccountRepository, PaymentRepository interfaces
5. `AccountService.java` - depends on AccountRepository interface

**Verification**: Services compile, depend only on repository interfaces (not JDBC)

---

## Phase 2: Reorganize JDBC Layer (Breaking Changes - Do in Sequence)

### Step 2.1: Reorganize msg-platform-persistence-jdbc Package Structure

**Current**:

```
msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/
├── model/
│   ├── OutboxEntity.java
│   ├── InboxEntity.java
│   └── ...
├── JdbcOutboxRepository.java
├── JdbcOutboxDao.java
└── ... (scattered JDBC implementations)
```

**Target**:

```
msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/
├── platform/
│   ├── entity/
│   │   ├── Command.java
│   │   ├── Inbox.java
│   │   ├── Outbox.java
│   │   ├── ProcessInstance.java
│   │   └── ProcessLog.java
│   └── repository/
│       ├── JdbcCommandRepository.java
│       ├── JdbcInboxRepository.java
│       ├── JdbcOutboxRepository.java
│       ├── JdbcProcessInstanceRepository.java
│       └── JdbcProcessLogRepository.java
└── payments/
    ├── entity/
    │   ├── Account.java
    │   ├── Transaction.java
    │   ├── Payment.java
    │   ├── FxContract.java
    │   └── AccountLimit.java
    └── repository/
        ├── JdbcAccountRepository.java
        ├── JdbcTransactionRepository.java
        ├── JdbcPaymentRepository.java
        ├── JdbcFxContractRepository.java
        └── JdbcAccountLimitRepository.java
```

**Task**: Create directory structure and move/refactor files

1. Create `platform/entity/` directory
2. Create `platform/repository/` directory
3. Create `payments/entity/` directory
4. Create `payments/repository/` directory
5. Move OutboxEntity → platform/entity/Outbox.java
6. Move InboxEntity → platform/entity/Inbox.java
7. Create new PaymentsEntity classes in payments/entity/
8. Update package declarations and imports

**Verification**: Project still compiles after reorganization

---

### Step 2.2: Update Entities with Schema-Qualified @MappedEntity

**Task**: Update all entity classes with schema-qualified table names

For each entity in `platform/entity/`:

```java
// Before
@MappedEntity("outbox")
public class OutboxEntity {

    // After
    @MappedEntity("platform.outbox")
    @Table("outbox")
    public class Outbox {  // Rename from OutboxEntity to Outbox
```

For each entity in `payments/entity/`:

```java

@MappedEntity("payments.account")
@Table("account")
public class Account {
```

**Files to Update**:

1. `platform/entity/Command.java` → `@MappedEntity("platform.command")`
2. `platform/entity/Inbox.java` → `@MappedEntity("platform.inbox")`
3. `platform/entity/Outbox.java` → `@MappedEntity("platform.outbox")`
4. `platform/entity/ProcessInstance.java` → `@MappedEntity("platform.process_instance")`
5. `platform/entity/ProcessLog.java` → `@MappedEntity("platform.process_log")`
6. `payments/entity/Account.java` → `@MappedEntity("payments.account")`
7. `payments/entity/Transaction.java` → `@MappedEntity("payments.transaction")`
8. `payments/entity/Payment.java` → `@MappedEntity("payments.payment")`
9. `payments/entity/FxContract.java` → `@MappedEntity("payments.fx_contract")`
10. `payments/entity/AccountLimit.java` → `@MappedEntity("payments.account_limit")`

**Verification**: Entities compile with updated schema qualifications

---

### Step 2.3: Create Repository Implementations Implementing Core Interfaces

**Task**: Create JdbcXxxRepository classes that implement repository interfaces from msg-platform-core

**Pattern**:

```java
// In msg-platform-persistence-jdbc/platform/repository/
@DataRepository
public class JdbcOutboxRepository
        implements com.acme.reliable.repository.platform.OutboxRepository {

    private final GenericRepository<Outbox, Long> delegate;

    @Override
    public void insert(Outbox outbox) {
        delegate.save(outbox);
    }

    @Query("SELECT * FROM platform.outbox WHERE status = 'NEW' ORDER BY created_at")
    @Override
    public List<Outbox> findNew() {
        // ... implementation
    }
}
```

**Files to Create**:

1. `platform/repository/JdbcCommandRepository.java` implements CommandRepository
2. `platform/repository/JdbcInboxRepository.java` implements InboxRepository
3. `platform/repository/JdbcOutboxRepository.java` implements OutboxRepository
4. `platform/repository/JdbcProcessInstanceRepository.java` implements ProcessInstanceRepository
5. `platform/repository/JdbcProcessLogRepository.java` implements ProcessLogRepository
6. `payments/repository/JdbcAccountRepository.java` implements AccountRepository
7. `payments/repository/JdbcTransactionRepository.java` implements TransactionRepository
8. `payments/repository/JdbcPaymentRepository.java` implements PaymentRepository
9. `payments/repository/JdbcFxContractRepository.java` implements FxContractRepository
10. `payments/repository/JdbcAccountLimitRepository.java` implements AccountLimitRepository

**Verification**: All JDBC repository implementations compile

---

### Step 2.4: Update All Existing References to Old Entities/Repositories

**Task**: Update imports and references throughout codebase

**Files to Search and Update**:

- `msg-platform-processor/` - update imports from old JdbcXxxRepository to new locations
- `msg-platform-payments-worker/` - update imports
- `msg-platform-api/` - update imports
- Any test files referencing old repository locations

**Search Pattern**:

```
Find: import com.acme.reliable.persistence.jdbc.JdbcOutboxRepository
Replace: import com.acme.reliable.persistence.jdbc.platform.repository.JdbcOutboxRepository

Find: import com.acme.reliable.persistence.jdbc.model.OutboxEntity
Replace: import com.acme.reliable.persistence.jdbc.platform.entity.Outbox
```

**Verification**: All imports updated, project compiles

---

## Phase 3: Update Flyway Migrations (Schema Consolidation)

### Step 3.1: Create New V1__platform_schema.sql

**Location**: `migrations/payments/V1__platform_schema.sql`

**Content**:

```sql
-- Create platform schema
CREATE SCHEMA IF NOT EXISTS platform;

-- Platform messaging tables (from reliable/V1__baseline.sql)
CREATE TABLE platform.command (...)
CREATE TABLE platform.inbox (...)
CREATE TABLE platform.outbox (...)
CREATE TABLE platform.process_instance (...)
CREATE TABLE platform.process_log (...)

-- Indexes
CREATE INDEX ... ON platform.outbox
CREATE INDEX ... ON platform.process_instance
```

**Task**: Extract relevant DDL from `/migrations/reliable/V1__baseline.sql` and
`/migrations/reliable/V2__process_manager.sql` into new file with `platform.` schema qualification

**Verification**: SQL syntax is valid, can be executed manually

---

### Step 3.2: Create New V1.1__payments_schema.sql

**Location**: `migrations/payments/V1.1__payments_schema.sql`

**Content**:

```sql
-- Create payments schema
CREATE SCHEMA IF NOT EXISTS payments;

-- Payments domain tables (from old V1__baseline.sql + V2__process_manager.sql + V3__payments_schema.sql)
CREATE TABLE payments.account (...)
CREATE TABLE payments.transaction (...)
CREATE TABLE payments.payment (...)
CREATE TABLE payments.fx_contract (...)
CREATE TABLE payments.account_limit (...)

-- Indexes
CREATE INDEX ... ON payments.account
CREATE INDEX ... ON payments.payment
```

**Task**: Consolidate:

- Current `/migrations/payments/V1__baseline.sql` (command, inbox, outbox, process tables) → Move to
  V1__platform_schema.sql
- Current `/migrations/payments/V1.1__payments_schema.sql` (payments domain) → Keep as V1.1 but with `payments.` schema

**Verification**: SQL syntax is valid, both V1 and V1.1 can be executed in sequence

---

### Step 3.3: Handle Migration Versioning Conflict

**Problem**: We're redefining V1 (was V1__baseline.sql for payments, now V1__platform_schema.sql)

**Solution Options**:

**Option A (Recommended)**: Start fresh with new environment

- Delete `flyway_schema_history` table from payments database
- Run new V1 + V1.1 migrations
- ✅ Clean migration path
- ✅ Single database instead of dual
- ❌ Requires data migration if running system

**Option B**: Create migration continuation

- Keep old V1__baseline.sql (populated)
- Add V1.1__payments_schema.sql
- Add V1.2__platform_schema_consolidation.sql (creates platform schema, migrates data)
- ❌ Complex, requires data migration script
- ✅ Supports existing data

**Recommendation for this execution**: Use **Option A** (start fresh)

- This is architectural refactoring
- Clean environment enables proper schema separation
- Deploy as new version

**Task**:

1. Delete old migrations/payments/V1__baseline.sql
2. Delete old migrations/payments/V1.1__payments_schema.sql (the one we just cleaned up)
3. Create new V1__platform_schema.sql
4. Create new V1.1__payments_schema.sql

**Verification**: Only 2 migration files in migrations/payments/

---

### Step 3.4: Update Flyway Configuration

**Location**: `scripts/flyway-config/flyway-payments.conf`

**Task**: Verify configuration points to correct directory and database

```properties
flyway.url=jdbc:postgresql://postgres:5432/payments
flyway.user=postgres
flyway.password=postgres
flyway.locations=filesystem:/flyway/sql/payments
flyway.schemas=platform,payments  # Important: both schemas
```

**Verification**: Flyway config lists both schemas

---

## Phase 4: Database Initialization Updates

### Step 4.1: Update Docker Database Initialization

**Location**: `scripts/init-databases.sh`

**Current**: Creates separate `reliable` and `payments` databases

**New**: Keep single payments database (schemas created by Flyway)

```bash
#!/bin/bash
set -e

echo "PostgreSQL Init Script: Creating databases..."

psql -v ON_ERROR_STOP=0 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE DATABASE payments;
    GRANT ALL PRIVILEGES ON DATABASE payments TO $POSTGRES_USER;
EOSQL

echo "PostgreSQL Init Script: Databases created successfully!"
```

**Task**:

1. Remove `CREATE DATABASE reliable;` line
2. Keep only `CREATE DATABASE payments;`
3. Update comments

**Verification**: Script creates only payments database

---

### Step 4.2: Verify Docker Compose Configuration

**Location**: `docker-compose.yml`

**Verification**:

- ✅ API service connects to `POSTGRES_DB=reliable` (unchanged, backward compatible)
- ✅ Payments-worker connects to `POSTGRES_DB=payments` (unchanged)
- ✅ Flyway migrations run against both databases
- ✅ Both databases exist after initialization

**No Changes Needed**: docker-compose.yml works as-is

---

## Phase 5: Testing & Validation

### Step 5.1: Unit Tests

**Task**: Update unit tests to use new package structure

**Files to Update**:

- Tests referencing JdbcOutboxRepository → update imports to platform/repository/
- Tests referencing PaymentRepository → update imports to payments/repository/

**Command**:

```bash
mvn clean test -pl msg-platform-persistence-jdbc
mvn clean test -pl msg-platform-payments-worker
```

**Verification**: All unit tests pass

---

### Step 5.2: Integration Tests - Package Structure

**Task**: Verify no compilation errors after reorganization

```bash
mvn clean compile
```

**Verification**: Full compilation succeeds with no errors

---

### Step 5.3: Integration Tests - Database Setup

**Task**: Start fresh and verify migrations work

```bash
# Remove old databases
docker volume rm -f messaging-platform_postgres_data

# Start services
docker-compose up -d

# Wait for initialization
sleep 120

# Verify databases exist
docker-compose exec -T postgres psql -U postgres -c "\l" 2>&1 | grep payments

# Verify schemas exist in payments database
docker-compose exec -T postgres psql -U postgres -d payments -c "\dn" 2>&1
```

**Expected Output**:

```
# Two schemas should exist
 List of schemas
---+---------
 platform    | postgres
 payments    | postgres
```

**Verification**: Both schemas created successfully

---

### Step 5.4: Integration Tests - Table Verification

**Task**: Verify all tables exist in correct schemas

```bash
# Platform schema tables
docker-compose exec -T postgres psql -U postgres -d payments -c "SELECT tablename FROM pg_tables WHERE schemaname='platform' ORDER BY tablename;" 2>&1

# Payments schema tables
docker-compose exec -T postgres psql -U postgres -d payments -c "SELECT tablename FROM pg_tables WHERE schemaname='payments' ORDER BY tablename;" 2>&1
```

**Expected Output**:

```
# Platform schema
 tablename
-------------
 command
 inbox
 outbox
 process_instance
 process_log

# Payments schema
 tablename
--------------
 account
 account_limit
 fx_contract
 payment
 transaction
```

**Verification**: All expected tables present in correct schemas

---

### Step 5.5: Integration Tests - Flyway History

**Task**: Verify migration history recorded correctly

```bash
docker-compose exec -T postgres psql -U postgres -d payments -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;" 2>&1
```

**Expected Output**:

```
 version |   description   | success
---------+-----------------+---------
 1       | platform schema | t
 1.1     | payments schema | t
```

**Verification**: Both migrations recorded

---

### Step 5.6: Application Tests

**Task**: Verify applications start successfully

```bash
# Check API health
curl http://localhost:8080/health

# Check payments-worker health
curl http://localhost:9091/health
```

**Expected**: Both return healthy status

**Verification**: Applications start without errors

---

### Step 5.7: End-to-End Smoke Test

**Task**: Verify platform and payments services can interact

**Test Scenario**:

1. API submits command (goes to platform.outbox)
2. Verify command in platform schema
3. Payments worker processes payment (saves to payments schema)
4. Verify data in both schemas

**Command** (if exposed via API):

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{"accountId":"...", "amount":100}'

# Verify in database
docker-compose exec -T postgres psql -U postgres -d payments -c "SELECT * FROM platform.outbox LIMIT 1;"
docker-compose exec -T postgres psql -U postgres -d payments -c "SELECT * FROM payments.payment LIMIT 1;"
```

**Verification**: Data persisted in correct schemas

---

## Phase 6: Documentation Updates (Per Note)

### Step 6.1: Update Architecture Documentation

**Files to Update** (as-is, system documents unchanged):

- `reliable-payments-combined-blueprint.md` - already updated, no action needed
- `SPRINT-3-COMPLETE.md` - documents old architecture, leave as historical record
- `process-implementation-plan.md` - documents process manager, leave as-is

**Task**: Create new documentation file

**New File**: `MULTI_SCHEMA_ARCHITECTURE.md`

- Explains new schema separation
- Shows platform vs payments schema purpose
- Includes SQL schema diagram
- Documents JDBC → Repository Interface layering

---

## Summary: Files Changed by Phase

### Phase 1: Core Module (No Breaking Changes)

- Create: `msg-platform-core/src/main/java/com/acme/reliable/repository/platform/*.java` (5 files)
- Create: `msg-platform-core/src/main/java/com/acme/reliable/repository/payments/*.java` (5 files)
- Create: `msg-platform-core/src/main/java/com/acme/reliable/service/platform/*.java` (3 files)
- Create: `msg-platform-core/src/main/java/com/acme/reliable/service/payments/*.java` (2 files)

### Phase 2: JDBC Module (Breaking Changes - Reorganization)

- Move: `msg-platform-persistence-jdbc/` entities to `platform/entity/` and `payments/entity/`
- Create: `msg-platform-persistence-jdbc/platform/repository/*.java` (5 files)
- Create: `msg-platform-persistence-jdbc/payments/repository/*.java` (5 files)
- Update: All `@MappedEntity` annotations with schema qualification
- Update: All imports across codebase

### Phase 3: Flyway Migrations

- Delete: `migrations/payments/V1__baseline.sql` (old)
- Delete: `migrations/payments/V1.1__payments_schema.sql` (old)
- Create: `migrations/payments/V1__platform_schema.sql` (new)
- Create: `migrations/payments/V1.1__payments_schema.sql` (new, payments only)

### Phase 4: Docker/Infrastructure

- Update: `scripts/init-databases.sh` (remove reliable database creation)
- Verify: `docker-compose.yml` (no changes needed)
- Verify: `scripts/flyway-config/flyway-payments.conf` (add schemas config)

### Phase 5: Testing

- Update: Test imports in all modules
- Run: Full test suite

### Phase 6: Documentation

- Create: `MULTI_SCHEMA_ARCHITECTURE.md`

---

## Risk Mitigation

**Risk 1: Import conflicts during reorganization**

- Mitigation: Use IDE refactoring tools (Intellij IDEA refactor → move)
- Mitigation: Commit after each major move with full test run

**Risk 2: Flyway migration version conflicts**

- Mitigation: Use fresh environment (Option A) for clean state
- Mitigation: Verify flyway_schema_history after deployment

**Risk 3: Service dependencies on old JDBC imports**

- Mitigation: Identify all usages first with grep before moving
- Mitigation: Update imports immediately after move

**Risk 4: Schema qualification typos in SQL**

- Mitigation: Test migrations before deployment
- Mitigation: Verify all tables exist in correct schemas

---

## Rollback Plan

If critical issues arise:

1. **Keep old reliable database**: Don't delete migrations/reliable/
2. **Keep old payments migrations**: Backup before deletion
3. **Revert docker-compose**: Restore dual-database setup
4. **Revert JDBC reorganization**: Git reset to pre-Phase 2
5. **Verify**:
   ```bash
   git reset --hard <pre-phase2-commit>
   docker volume rm -f messaging-platform_postgres_data
   docker-compose up -d
   sleep 120
   docker-compose exec -T postgres psql -U postgres -c "\l"
   # Should show reliable + payments databases
   ```

---

## Success Criteria

- ✅ All unit tests pass
- ✅ All integration tests pass
- ✅ Compilation succeeds with no errors
- ✅ Both schemas (platform, payments) created in payments database
- ✅ All 10+ platform tables in platform schema
- ✅ All 5+ payment domain tables in payments schema
- ✅ Flyway migrations recorded (V1, V1.1)
- ✅ Applications start without errors
- ✅ No references to reliable database remain (except old docs)
- ✅ JDBC layer properly organized by schema
- ✅ Services depend only on repository interfaces
- ✅ E2E test demonstrates data in both schemas

---

## Timeline Estimate

| Phase                          | Duration        | Effort                       |
|--------------------------------|-----------------|------------------------------|
| Phase 1: Interfaces            | 2 hours         | 20 lines × 10 interfaces     |
| Phase 2: JDBC Reorganization   | 4 hours         | Move, rename, update imports |
| Phase 3: Flyway Migrations     | 1 hour          | Extract SQL, create 2 files  |
| Phase 4: Docker/Infrastructure | 30 min          | Script updates, verification |
| Phase 5: Testing & Validation  | 2 hours         | Test runs, bug fixes         |
| Phase 6: Documentation         | 1 hour          | New architecture doc         |
| **Total**                      | **~10.5 hours** | Excluding debugging          |

---

