# JDBC Schema Configuration Analysis - Documentation Guide

## Overview

This directory now contains comprehensive documentation analyzing the JDBC entity configuration, Flyway migrations, and
schema handling in the messaging platform. Three detailed analysis documents have been created:

1. **JDBC_SCHEMA_ANALYSIS.md** (704 lines) - Comprehensive technical analysis
2. **SCHEMA_ARCHITECTURE_SUMMARY.md** (285 lines) - Quick reference guide
3. **COMPARISON_TABLE.md** (367 lines) - Detailed comparison of patterns

---

## Quick Summary

### Current Architecture

The platform uses a **multi-database approach**:

- **reliable database**: Messaging framework tables (command, inbox, outbox, process)
- **payments database**: Domain-specific tables (account, transaction, payment, etc.)

Both databases:

- Run in same PostgreSQL server instance
- Managed by Flyway migrations from separate modules
- Connected via environment variables (POSTGRES_DB)
- Use public schema (no explicit schema separation)

### Repository Patterns

**Messaging Framework** (msg-platform-persistence-jdbc):

- Uses **Micronaut Data JDBC** with @MappedEntity annotations
- Auto-generated repository implementations
- Entities: OutboxEntity, InboxEntity, CommandEntity, DlqEntity
- Declarative @Query methods with native SQL

**Payments Domain** (msg-platform-payments-worker):

- Uses **Raw JDBC** with direct DataSource injection
- Hand-written repository implementations
- No entity classes (uses domain models)
- Manual JDBC: Connection, PreparedStatement, ResultSet
- Manual transaction management via @Transactional

### Why Two Patterns?

- **Micronaut Data JDBC**: Good for simple tables with straightforward CRUD
- **Raw JDBC**: Better for complex aggregates (Account with nested Transactions)
- Pragmatic hybrid approach based on use-case complexity

---

## Document Guide

### 1. JDBC_SCHEMA_ANALYSIS.md

**Best for**: Deep understanding of entire system

**Contains**:

- 8 major sections covering all aspects
- Entity configuration details with code examples
- Flyway migration organization and strategies
- Repository/DAO pattern explanation
- Current application structure breakdown
- Architectural patterns and separation of concerns
- What would change for single-schema approach
- Detailed recommendations with options
- Key files reference guide

**Key Sections**:

- Section 1: JDBC Entity Configuration (OutboxEntity, InboxEntity, CommandEntity, etc.)
- Section 2: Flyway Migration Organization (V1-V4 migration details)
- Section 3: Application Structure (module organization, datasource config)
- Section 4: Repository/DAO Patterns (messaging framework vs payments domain)
- Section 5: Architectural Patterns (separation of concerns, deployment topology)
- Section 6: Single-Schema Approach Options (3 recommended options with pros/cons)
- Section 7: Summary Table (quick comparison of aspects)
- Section 8: Recommendations (detailed implementation guidance)

**Use this when**: You need comprehensive understanding, planning changes, or creating new modules

---

### 2. SCHEMA_ARCHITECTURE_SUMMARY.md

**Best for**: Quick reference and visual understanding

**Contains**:

- Current state diagram (ASCII art)
- Repository pattern comparison (messaging vs payments)
- Entity/table mapping matrix
- Flyway migration strategy diagram
- Datasource configuration patterns
- Key design decisions table
- Single-schema options (A, B, C)
- Migration file reference
- Code location reference
- Critical limitations of Micronaut Data JDBC

**Key Diagrams**:

- PostgreSQL Server topology
- Repository pattern flow
- Flyway orchestration
- Entity/table mapping tables

**Use this when**: You need quick orientation, presenting architecture, or looking up file locations

---

### 3. COMPARISON_TABLE.md

**Best for**: Understanding implementation differences

**Contains**:

- Side-by-side implementation comparison (Outbox vs Account)
- Entity definition differences
- Repository interface differences
- Query implementation examples
- Result mapping approaches
- Feature comparison matrix (20 aspects)
- Lines of code comparison
- Deployment impact analysis
- When to use which approach
- Migration path example
- Risk analysis

**Key Tables**:

- Feature comparison (Micronaut Data vs Raw JDBC)
- When to use which approach
- Migration path from Micronaut Data

**Use this when**: Deciding between Micronaut Data and Raw JDBC, training developers, or evaluating approach changes

---

## Key Findings

### Entity Configuration

**No explicit @EntityScan**

- Micronaut Data uses compile-time annotation processing
- `pom.xml` configuration with compiler args
- Processor generates repository implementations at compile time
- No runtime component scanning needed

### Datasource/Schema Configuration

**Environment-Based Selection**

- Single datasource per application (no multi-datasource routing)
- Database selection via POSTGRES_DB environment variable
- No runtime switching or schema routing
- Clean separation at deployment level

### Flyway Migrations

**Manual Docker Orchestration**

- Separate migration directories per database
- Two independent Flyway invocations
- Pre-creates databases before migration
- Uses `baselineOnMigrate` for flexibility

**Migration Sequence**:

```
reliable DB:  V1 (baseline), V2 (process), V4 (redis)
payments DB:  V3 (schema), V4 (columns)
```

### Repository Patterns

**Messaging Framework**: Micronaut Data JDBC

- 5 entity classes (OutboxEntity, InboxEntity, CommandEntity, DlqEntity, etc.)
- Declarative @Query methods
- Auto-generated implementations
- Implicit transaction management

**Payments Domain**: Raw JDBC

- No entity classes (uses domain models)
- Hand-written repository implementations
- Manual JDBC with DataSource injection
- Explicit @Transactional annotation

---

## Changes Needed for Single-Schema Approach

### Option A: PostgreSQL Schemas (Recommended)

```sql
CREATE SCHEMA platform;   -- cmd_*, process_*
CREATE SCHEMA payments;   -- pmt_*, account_*, transaction_*
```

Pros: Clean isolation, flexible permission models
Cons: Requires custom datasource routing (not built-in to Micronaut Data)

### Option B: Table Prefixes (Simpler)

```java
@MappedEntity("cmd_command")
@MappedEntity("cmd_outbox")
@MappedEntity("pmt_account")
@MappedEntity("pmt_payment")
```

Pros: No custom routing, works with Micronaut Data
Cons: Less isolation, longer table names

### Option C: Keep Current (No Changes)

- Already well-designed
- Zero code changes needed
- Consider if consolidation is actually necessary

---

## Critical Limitations Discovered

### Micronaut Data JDBC

1. No schema support in @MappedEntity annotation
2. No multi-datasource routing support
3. No schema qualification in table names
4. Compile-time generation limits flexibility

### Workarounds

1. Use raw JDBC for complex scenarios (like payments domain)
2. Use schema-qualified table names in @Query methods
3. Keep separate databases (current approach)
4. Use table name prefixes instead of schemas

---

## File References

### All analyses reference specific code locations:

**Entity definitions**:

- `/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/model/`

**Repository implementations**:

- `/msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/` (Micronaut Data)
- `/msg-platform-payments-worker/src/main/java/com/acme/payments/infrastructure/persistence/` (Raw JDBC)

**Migrations**:

- `/msg-platform-persistence-jdbc/src/main/resources/db/migration/` (V1, V2, V4)
- `/msg-platform-payments-worker/src/main/resources/db/migration/` (V3, V4)

**Configuration**:

- `/scripts/init-databases.sh`
- `/scripts/run-flyway-migrations.sh`
- `/scripts/flyway-config/flyway-*.conf`

---

## Reading Order

### For Quick Overview (15 minutes)

1. Read this file (SCHEMA_ANALYSIS_README.md)
2. Review SCHEMA_ARCHITECTURE_SUMMARY.md diagrams
3. Check Key Findings section above

### For Complete Understanding (1-2 hours)

1. Start with SCHEMA_ARCHITECTURE_SUMMARY.md
2. Read JDBC_SCHEMA_ANALYSIS.md sections in order
3. Reference COMPARISON_TABLE.md for specific patterns
4. Review code locations in actual repository

### For Implementation Planning (30 minutes + research)

1. Review JDBC_SCHEMA_ANALYSIS.md Section 6 & 8
2. Study SCHEMA_ARCHITECTURE_SUMMARY.md single-schema options
3. Decide which option fits your needs
4. Reference COMPARISON_TABLE.md for pattern implications

### For New Developer Onboarding

1. SCHEMA_ARCHITECTURE_SUMMARY.md (start here)
2. COMPARISON_TABLE.md (understand why two patterns exist)
3. JDBC_SCHEMA_ANALYSIS.md Section 3 & 4 (module structure & repositories)
4. Code review of specific repositories

---

## Key Takeaways

1. **Current architecture is pragmatic**: Uses best tool for each use case (Micronaut Data for simple, Raw JDBC for
   complex)

2. **Schema isolation is database-level**: Each app connects to different database, no runtime routing

3. **Migrations are orchestrated**: Docker-based manual Flyway invocation per database

4. **Two patterns work well together**: Framework tables use Micronaut Data, domain aggregates use Raw JDBC

5. **Consolidation is possible but not necessary**: Would require schema routing or table prefixes, neither is critical

6. **No @EntityScan needed**: Micronaut Data uses compile-time annotation processing

---

## Questions Answered

### How are JDBC entities currently configured?

- Messaging framework uses @MappedEntity annotations on POJOs (OutboxEntity, InboxEntity, CommandEntity, DlqEntity)
- Payments domain uses raw JDBC without entity classes
- No explicit EntityScan or component discovery - compile-time generation

### How are Flyway migrations organized?

- Separate directories per database (V1-V2-V4 for reliable, V3-V4 for payments)
- Docker-based orchestration with separate Flyway invocations
- Pre-creates databases, then runs migrations independently

### What's the current application structure?

- msg-platform-persistence-jdbc: JDBC repositories + entities for messaging framework
- msg-platform-payments-worker: Domain logic + raw JDBC repositories for payments domain
- msg-platform-core: Repository interfaces + SPI contracts
- Single datasource per application selected via environment variable

### How are repositories/DAOs organized?

- Messaging: Micronaut Data JDBC with auto-generated implementations
- Payments: Raw JDBC with hand-written repository classes
- Both patterns coexist effectively

### What would be needed to support single schema?

- Option A (Schemas): Create platform and payments schemas, add custom routing
- Option B (Prefixes): Change table names to cmd_* and pmt_* prefixes
- Option C (Keep current): No changes - current design is already clean

---

## Next Steps

1. **If consolidating to single schema**: Choose Option A or B from JDBC_SCHEMA_ANALYSIS.md Section 8
2. **If adding new tables**: Use Micronaut Data for simple tables, Raw JDBC for complex aggregates
3. **If training developers**: Start with SCHEMA_ARCHITECTURE_SUMMARY.md, use COMPARISON_TABLE.md for details
4. **If refactoring**: Reference JDBC_SCHEMA_ANALYSIS.md Section 4 & 5 for patterns

---

## Document Metadata

**Total lines of analysis**: 1,356 lines (across 3 documents)
**Date created**: 2025-11-08
**Scope**: Complete codebase exploration covering entities, repositories, migrations, configuration, and architecture
**Coverage**: msg-platform-persistence-jdbc, msg-platform-payments-worker, msg-platform-core, docker-compose, Flyway
scripts

