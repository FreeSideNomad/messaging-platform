# JDBC Schema Analysis - Complete Documentation Index

## Analysis Documents Created

This exploration created **4 comprehensive documents** totaling **1,641 lines** of detailed analysis:

### 1. SCHEMA_ANALYSIS_README.md (Start Here!)

**Purpose**: Navigation guide and quick reference for all analysis documents
**Length**: 285 lines
**Key Content**:

- Overview of current architecture
- Document guide with use cases
- Key findings summary
- Reading order recommendations
- Critical limitations
- Next steps

**Read this first if**: You want orientation or need to decide which other documents to read

---

### 2. SCHEMA_ARCHITECTURE_SUMMARY.md (Visual Guide)

**Purpose**: ASCII diagrams, quick lookup tables, and visual architecture
**Length**: 285 lines
**Key Content**:

- Current state diagram (PostgreSQL, databases, containers)
- Repository pattern comparison flowcharts
- Entity/table mapping matrices
- Flyway migration strategy diagram
- Datasource configuration patterns
- Key design decisions table
- Single-schema options (A, B, C)
- File location reference guide
- Critical limitations of Micronaut Data JDBC

**Read this when**: You need visual understanding, quick lookup, or reference during implementation

**Key Diagrams**:

```
PostgreSQL Server
├─ reliable database (6 tables)
└─ payments database (7 tables)
   Connected by Docker Compose topology
```

---

### 3. JDBC_SCHEMA_ANALYSIS.md (Deep Dive)

**Purpose**: Comprehensive technical analysis of entire system
**Length**: 704 lines
**Organized in 8 sections**:

1. **JDBC Entity Configuration** (150 lines)
    - OutboxEntity, InboxEntity, CommandEntity, DlqEntity
    - @MappedEntity annotations
    - Entity vs domain model approach
    - No explicit @EntityScan

2. **Flyway Migration Organization** (180 lines)
    - V1__baseline.sql details
    - V2__process_manager.sql details
    - V3__payments_schema.sql details
    - V4__add_claimed_at_column.sql details
    - Manual Docker orchestration
    - baselineOnMigrate strategy

3. **Current Application Structure** (120 lines)
    - Datasource configuration patterns
    - Module structure (persistence-jdbc, payments-worker, core)
    - Docker initialization sequence
    - Flyway integration with applications

4. **Repository/DAO Patterns** (120 lines)
    - Micronaut Data JDBC pattern (messaging framework)
    - Raw JDBC pattern (payments domain)
    - Multi-database pattern explanation
    - Why two patterns coexist

5. **Architectural Patterns** (80 lines)
    - Separation of concerns diagram
    - Deployment topology
    - Transaction management approaches

6. **Single-Schema Approach** (100 lines)
    - Option A: PostgreSQL schemas
    - Option B: Table name prefixes
    - Option C: Keep current approach
    - Repository configuration changes needed
    - Flyway configuration changes needed

7. **Summary Table** (40 lines)
    - Quick comparison of all aspects
    - Current vs required changes

8. **Recommendations** (80 lines)
    - Detailed implementation guidance
    - Pros/cons analysis
    - Key files reference

**Read this when**: You need complete understanding, planning significant changes, or creating new modules

---

### 4. COMPARISON_TABLE.md (Pattern Comparison)

**Purpose**: Detailed side-by-side comparison of implementation patterns
**Length**: 367 lines
**Key Content**:

- **Side-by-Side Comparison**:
    - Entity definitions (OutboxEntity vs Account model)
    - Repository interfaces (Micronaut Data vs Raw JDBC)
    - Query implementations (declarative vs explicit JDBC)
    - Result mapping (automatic vs manual)

- **Feature Comparison Matrix** (20 features):
  | Feature | Micronaut Data | Raw JDBC |
  | Compile-time setup | 5 lines | None |
  | Entity classes | Required | Optional |
  | Implementation | Auto-generated | Hand-written |
  | Transactions | Implicit | @Transactional |
  | Query syntax | @Query | String + PreparedStatement |
  | Result mapping | Automatic | Manual |
  | Complex aggregates | Difficult | Natural fit |

- **Lines of Code Comparison**:
    - Simple CRUD: Micronaut Data 15 lines, Raw JDBC 50 lines
    - When you need which approach

- **When to Use Which**:
    - Micronaut Data: Simple tables, straightforward CRUD, Outbox/Inbox/Command
    - Raw JDBC: Complex aggregates, nested objects, Account/Payment

- **Migration Path**: How Outbox evolved from Micronaut Data

- **Risk Analysis**: Limitations of both approaches

**Read this when**: Deciding between patterns, training developers, evaluating refactoring

---

## Current System Overview

### Database Architecture

```
PostgreSQL Server
│
├─ reliable database (messaging framework)
│  ├─ command (UUID PK, JSONB payload)
│  ├─ inbox (composite PK: message_id + handler)
│  ├─ outbox (BIGSERIAL PK, JSONB payload/headers)
│  ├─ command_dlq (UUID PK, dead letter queue)
│  ├─ process_instance (UUID PK, event sourcing)
│  └─ process_log (UUID PK + BIGSERIAL seq, immutable)
│
└─ payments database (domain-specific)
   ├─ account (UUID PK, customer account)
   ├─ transaction (UUID PK, account ledger)
   ├─ account_limit (UUID PK, rate limits)
   ├─ fx_contract (UUID PK, forex contracts)
   ├─ payment (UUID PK, payment submissions)
   ├─ inbox_bc (composite PK, idempotent processing)
   └─ outbox_bc (BIGSERIAL PK, reliable events)
```

### Repository Pattern Distribution

```
Messaging Framework (msg-platform-persistence-jdbc)
├─ JdbcOutboxRepository        (Micronaut Data JDBC)
├─ JdbcOutboxDao               (Alternative Micronaut Data)
├─ JdbcCommandRepository        (Micronaut Data JDBC)
├─ JdbcInboxRepository          (Micronaut Data JDBC)
├─ JdbcDlqRepository            (Micronaut Data JDBC)
└─ JdbcProcessRepository        (Raw JDBC - hybrid)

Payments Domain (msg-platform-payments-worker)
├─ JdbcAccountRepository        (Raw JDBC)
├─ JdbcPaymentRepository        (Raw JDBC)
├─ JdbcAccountLimitRepository   (Raw JDBC)
└─ JdbcFxContractRepository     (Raw JDBC)
```

---

## Key Findings

### 1. Entity Configuration

- Uses Micronaut Data JDBC (not JPA)
- @MappedEntity annotations map POJOs to tables
- No explicit EntityScan - compile-time annotation processing
- pom.xml configuration with -Amicronaut.processing.* args
- Processor generates repository implementations at compile time

### 2. Flyway Migrations

- Separate V1, V2, V4 migrations for reliable database
- Separate V3, V4 migrations for payments database
- Manual Docker orchestration (not Spring Boot auto-config)
- Uses separate Flyway invocations per database
- Pre-creates databases with init-databases.sh

### 3. Datasource/Schema

- Single datasource per application (no runtime switching)
- POSTGRES_DB environment variable selects database
- No explicit schema separation (both use public schema)
- Environment-based selection at deployment time
- Clean separation: API -> reliable, Payments Worker -> payments

### 4. Repository Patterns

- **Messaging**: Micronaut Data JDBC for simple tables (Outbox, Inbox, Command)
- **Payments**: Raw JDBC for complex aggregates (Account with Transactions)
- Both patterns coexist and work well together
- Pragmatic choice based on use-case complexity

### 5. No Schema Routing

- Micronaut Data JDBC has no built-in schema support
- No @DataSource routing annotation
- No schema qualification in table names
- Workaround: Use table prefixes or separate databases (current approach)

---

## Recommendations for Single-Schema Architecture

### Option A: PostgreSQL Schemas (Recommended)

**Create separate schemas within single database**:

```sql
CREATE SCHEMA platform;   -- command, inbox, outbox, process_instance, process_log
CREATE SCHEMA payments;   -- account, transaction, payment, fx_contract, etc.
```

Pros:

- Logical separation at DB level
- Different permission models per schema
- Tables can have same names in different schemas
- Cleaner namespace isolation

Cons:

- Requires custom datasource routing (not built-in)
- Query syntax needs schema qualification
- Additional complexity for repository proxies

### Option B: Table Prefixes (Simpler)

**Use naming convention instead of schemas**:

```java
@MappedEntity("cmd_command")
@MappedEntity("cmd_outbox")
@MappedEntity("pmt_account")
@MappedEntity("pmt_payment")
```

Pros:

- Works with existing Micronaut Data JDBC
- No custom routing needed
- Easy to understand naming

Cons:

- Less isolation (all in same schema)
- Longer table names
- Still requires table name changes

### Option C: Keep Current (No Changes)

- Zero code changes
- Already well-designed
- Multi-database isolation is valid pattern
- Consider if consolidation is necessary

---

## All Documents at a Glance

| Document                       | Lines         | Purpose                 | Best For                                      |
|--------------------------------|---------------|-------------------------|-----------------------------------------------|
| SCHEMA_ANALYSIS_README.md      | 285           | Navigation guide        | Orientation, deciding which docs to read      |
| SCHEMA_ARCHITECTURE_SUMMARY.md | 285           | Visual reference        | Quick lookup, diagrams, architecture overview |
| JDBC_SCHEMA_ANALYSIS.md        | 704           | Deep technical analysis | Complete understanding, planning changes      |
| COMPARISON_TABLE.md            | 367           | Pattern comparison      | Implementation decisions, developer training  |
| **ANALYSIS_INDEX.md**          | **This file** | **Complete index**      | **Reference and navigation**                  |

**Total**: 1,641 lines of detailed documentation

---

## Reading Paths by Use Case

### Path 1: Quick Understanding (30 minutes)

1. This file (ANALYSIS_INDEX.md) - 5 min
2. SCHEMA_ARCHITECTURE_SUMMARY.md - 15 min
3. Key Findings (this file) - 10 min

### Path 2: Complete Understanding (2 hours)

1. SCHEMA_ARCHITECTURE_SUMMARY.md - 20 min (diagrams)
2. JDBC_SCHEMA_ANALYSIS.md Section 1-5 - 60 min (entity config, migrations, structure, patterns)
3. COMPARISON_TABLE.md - 30 min (understand why two patterns)
4. JDBC_SCHEMA_ANALYSIS.md Section 6-8 - 30 min (consolidation options)

### Path 3: Implementation Planning (1.5 hours)

1. SCHEMA_ARCHITECTURE_SUMMARY.md - 15 min (current architecture)
2. JDBC_SCHEMA_ANALYSIS.md Section 6 - 30 min (options and tradeoffs)
3. COMPARISON_TABLE.md - 20 min (pattern implications)
4. JDBC_SCHEMA_ANALYSIS.md Section 8 - 25 min (recommendations)

### Path 4: Developer Onboarding (1 hour)

1. SCHEMA_ARCHITECTURE_SUMMARY.md - 20 min (start with quick reference)
2. COMPARISON_TABLE.md "When to Use Which" - 15 min (understand approach)
3. JDBC_SCHEMA_ANALYSIS.md Section 3-4 - 25 min (modules and repositories)

---

## Quick Reference

### Entity Classes Location

`/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/model/`

- OutboxEntity.java
- InboxEntity.java
- CommandEntity.java
- DlqEntity.java

### Repository Implementations

**Messaging Framework**:
`/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/`

- JdbcOutboxRepository.java
- JdbcCommandRepository.java
- JdbcInboxRepository.java
- JdbcDlqRepository.java
- JdbcOutboxDao.java
- process/JdbcProcessRepository.java

**Payments Domain**:
`/msg-platform-payments-worker/src/main/java/com/acme/payments/infrastructure/persistence/`

- JdbcAccountRepository.java
- JdbcPaymentRepository.java
- JdbcAccountLimitRepository.java
- JdbcFxContractRepository.java

### Migration Files

**Reliable Database**:
`/msg-platform-persistence-jdbc/src/main/resources/db/migration/`

- V1__baseline.sql (command, inbox, outbox, dlq)
- V2__process_manager.sql (process_instance, process_log)
- V4__redis_fastpublish_outbox.sql (claimed_at, claimed_by)

**Payments Database**:
`/msg-platform-payments-worker/src/main/resources/db/migration/`

- V3__payments_schema.sql (account, transaction, payment, etc.)
- V4__add_claimed_at_column.sql (claimed_at, claimed_by for outbox_bc)

### Configuration Files

`/scripts/`

- init-databases.sh (creates reliable and payments databases)
- run-flyway-migrations.sh (orchestrates both Flyway invocations)
- flyway-config/flyway-reliable.conf (reliable DB migration config)
- flyway-config/flyway-payments.conf (payments DB migration config)

---

## Critical Limitations

### Micronaut Data JDBC

1. **No schema support** in @MappedEntity annotation
2. **No multi-datasource routing** (@DataSource not supported)
3. **No schema qualification** in table names
4. **Compile-time generation** limits runtime flexibility

### Workarounds in This Codebase

1. Use raw JDBC (like payments domain) for complex scenarios
2. Use table name prefixes (cmd_*, pmt_*) for schema separation
3. Keep separate databases (current approach is valid)
4. Use schema-qualified table names in @Query methods

---

## File Sizes and Coverage

```
SCHEMA_ANALYSIS_README.md ........... 285 lines ... Navigation + key findings
SCHEMA_ARCHITECTURE_SUMMARY.md ...... 285 lines ... Diagrams + quick reference
JDBC_SCHEMA_ANALYSIS.md ............ 704 lines ... Complete technical analysis
COMPARISON_TABLE.md ................ 367 lines ... Pattern comparison details
ANALYSIS_INDEX.md .................. This file ... Master index

Total Coverage: 1,641 lines
Coverage Areas: 
- 4 entity classes (Outbox, Inbox, Command, DLQ)
- 9 repository implementations (5 Micronaut Data, 4 Raw JDBC)
- 6 migration files (3 reliable DB, 3 payments DB)
- 3 configuration files (init, orchestration, Flyway configs)
- 2 application modules (persistence-jdbc, payments-worker)
```

---

## Questions This Analysis Answers

1. **How are JDBC entities configured?** See JDBC_SCHEMA_ANALYSIS.md Section 1
2. **How are Flyway migrations organized?** See JDBC_SCHEMA_ANALYSIS.md Section 2
3. **What's the application structure?** See JDBC_SCHEMA_ANALYSIS.md Section 3
4. **How do repositories work?** See JDBC_SCHEMA_ANALYSIS.md Section 4 & COMPARISON_TABLE.md
5. **What's the architectural pattern?** See JDBC_SCHEMA_ANALYSIS.md Section 5 & SCHEMA_ARCHITECTURE_SUMMARY.md
6. **How to consolidate schemas?** See JDBC_SCHEMA_ANALYSIS.md Section 6
7. **Micronaut Data vs Raw JDBC?** See COMPARISON_TABLE.md
8. **Where are the files?** See SCHEMA_ARCHITECTURE_SUMMARY.md "Code Location Reference"

---

**Analysis Date**: 2025-11-08
**Scope**: Complete JDBC/Flyway/schema exploration
**Coverage**: msg-platform-persistence-jdbc, msg-platform-payments-worker, msg-platform-core, docker-compose, Flyway
configuration

