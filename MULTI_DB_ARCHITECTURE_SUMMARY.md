# Multi-Database Support Architecture - Implementation Summary

## Completed: Core Architecture Implementation (8 of 14 tasks)

### Phase 1: Strategy Pattern & Dialect Implementation ✅

#### 1. **SqlDialect Interface** (Core Strategy Contract)

- **Location**: `msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/dialect/SqlDialect.java`
- **Purpose**: Encapsulates all database-specific SQL syntax variations
- **Methods**: 22 interface methods covering:
    - Timestamp functions (now() → CURRENT_TIMESTAMP → GETDATE())
    - JSON handling (JSONB casting, object aggregation, key-value unpacking)
    - Date/time arithmetic (INTERVAL → DATEADD)
    - Upsert operations (ON CONFLICT → MERGE)
    - Row locking (FOR UPDATE SKIP LOCKED → FOR UPDATE → WITH UPDLOCK)
    - Complex operations (sweepBatchQuery, result processing)
    - Dialect metadata (name, version, max string length)

#### 2. **PostgresSqlDialect** (Production Implementation)

- **Location**:
  `msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/dialect/PostgresSqlDialect.java`
- **Status**: ✅ Complete - Uses native PostgreSQL features
- **Features**:
    - Activated via `@Requires(property = "app.database.dialect", value = "postgres", defaultValue = "postgres")`
    - JSONB casting with `::jsonb` operator
    - CTE (Common Table Expression) with FOR UPDATE SKIP LOCKED for batch operations
    - RETURNING clause for single ID retrieval
    - Native JSON aggregation functions (jsonb_object_agg, jsonb_each_text)
    - INTERVAL arithmetic for timestamp calculations

#### 3. **H2SqlDialect** (Testing Implementation)

- **Location**:
  `msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/dialect/H2SqlDialect.java`
- **Status**: ✅ Complete - Simplified for H2 limitations
- **Features**:
    - Activated via `@Requires(property = "app.database.dialect", value = "h2")`
    - VARCHAR storage for JSON (no JSONB type)
    - DATEADD for interval arithmetic
    - MERGE syntax for upsert operations
    - FOR UPDATE (without SKIP LOCKED)
    - processSweepResultRow() method for Java-side result transformation
    - Fallback strategies for unsupported features

#### 4. **SqlServerDialect** (Future Implementation)

- **Location**:
  `msg-platform-persistence-jdbc/src/main/java/com/acme/reliable/persistence/jdbc/dialect/SqlServerDialect.java`
- **Status**: ✅ Complete - Placeholder for SQL Server 2016+
- **Features**:
    - Activated via `@Requires(property = "app.database.dialect", value = "sqlserver")`
    - T-SQL syntax (GETDATE(), DATEADD, OPENJSON)
    - JSON_OBJECT and OPENJSON for JSON handling
    - OUTPUT clause instead of RETURNING
    - WITH (UPDLOCK, READCOMMITTED) for table-level locking
    - MERGE syntax for upsert operations

---

### Phase 2: Test Configuration & H2 Setup ✅

#### 5. **H2TestConfiguration**

- **Location**:
  `msg-platform-persistence-jdbc/src/test/java/com/acme/reliable/persistence/jdbc/config/H2TestConfiguration.java`
- **Purpose**: Provides test-specific beans for H2 testing
- **Provides**:
  ```java
  @Bean h2DataSource() → jdbc:h2:mem:testdb (in-memory, volatile)
  @Bean h2SqlDialect() → H2SqlDialect instance
  ```
- **Usage**: Imported in test classes via `@Import(H2TestConfiguration.class)`

#### 6. **H2 Migration Files**

- **Location**: `msg-platform-persistence-jdbc/src/test/resources/db/migration/h2/V1__baseline_h2.sql`
- **Purpose**: H2-specific schema simplified from PostgreSQL
- **Includes**:
    - Schema creation (platform schema)
    - All 6 core tables (outbox, command, inbox, command_dlq, process_instance, process_log)
    - VARCHAR storage for JSON instead of JSONB
    - CHECK constraints for enums instead of ENUM types
    - All necessary indexes for query performance
    - Foreign key relationships with ON DELETE CASCADE

#### 7. **pom.xml H2 Dependency**

- **Update**: Added H2 database driver for testing
  ```xml
  <dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
  </dependency>
  ```

#### 8. **JdbcRepositoryTestBase**

- **Location**:
  `msg-platform-persistence-jdbc/src/test/java/com/acme/reliable/persistence/jdbc/JdbcRepositoryTestBase.java`
- **Purpose**: Reusable base class for all JDBC repository tests
- **Provides**:
    - Automatic H2 schema initialization via Flyway
    - JDBC template utilities (namedJdbc, jdbc)
    - SQL dialect access (sqlDialect)
    - Dialect verification (ensures H2 is used in tests)
    - Table cleanup between tests
    - Helper methods:
        - `cleanupAllTables()` - Disable foreign keys, truncate all tables
        - `truncateTable(tableName)` - Safe truncation with error handling
        - `rowExists(sql, args)` - Check row existence
        - `countRows(table, whereClause)` - Count matching rows
        - `getCurrentTimestamp()` - Get dialect-aware timestamp

---

## Architecture Benefits

### 1. **Single Implementation, Multiple Databases**

Each `JdbcXxxRepository` uses injected `SqlDialect` strategy:

```java
@Singleton
public class JdbcCommandRepository {
  private final SqlDialect sqlDialect;  // Injected

  public Optional<Command> find(UUID id) {
    String sql = "SELECT ... FROM command WHERE id = :id";
    // SQL is dialect-agnostic, execution uses dialect-specific functions
  }
}
```

### 2. **Clean Separation of Concerns**

- **SQL Dialect Logic**: Isolated in strategy classes
- **Repository Logic**: Database-agnostic, depends only on SqlDialect interface
- **Test Configuration**: Separate H2 configuration, no production code changes

### 3. **Extensible for New Databases**

Adding SQL Server support requires:

1. Create `SqlServerDialect` implementation ✅ (done)
2. Create SQL Server migrations in `src/main/resources/db/migration/sqlserver/`
3. Update configuration to support SQL Server profile
4. All repositories work automatically (no code changes needed)

### 4. **Comprehensive Test Support**

- **H2 In-Memory Database**: No external dependencies, fast test execution
- **Dialect Strategy Testing**: Can test dialect logic independently
- **Result Transformation**: H2SqlDialect.processSweepResultRow() handles H2-specific result mapping

---

## File Structure Created

```
msg-platform-persistence-jdbc/
├── src/main/java/com/acme/reliable/persistence/jdbc/
│   └── dialect/
│       ├── SqlDialect.java                        (interface)
│       ├── PostgresSqlDialect.java                (✅ complete)
│       ├── H2SqlDialect.java                      (✅ complete)
│       └── SqlServerDialect.java                  (✅ complete)
│
├── src/test/java/com/acme/reliable/persistence/jdbc/
│   ├── config/
│   │   └── H2TestConfiguration.java               (✅ complete)
│   ├── JdbcRepositoryTestBase.java                (✅ complete)
│   ├── JdbcCommandRepositoryTest.java             (pending)
│   ├── JdbcDlqRepositoryTest.java                 (pending)
│   ├── JdbcInboxRepositoryTest.java               (pending)
│   ├── JdbcOutboxRepositoryTest.java              (pending)
│   └── JdbcProcessRepositoryTest.java             (pending)
│
├── src/test/resources/db/migration/h2/
│   └── V1__baseline_h2.sql                        (✅ complete)
│
└── pom.xml                                        (H2 dependency added ✅)
```

---

## Next Steps: Writing Repository Tests (6 tasks remaining)

### Test Implementation Strategy

#### Pattern for Each Repository Test

```java
@MicronautTest
class JdbcCommandRepositoryTest extends JdbcRepositoryTestBase {

  @Inject CommandRepository repository;  // Injected interface

  @Test void testInsertPending() {
    // 1. Setup: Create command via repository
    // 2. Execute: Call repository method
    // 3. Verify: Check database state or return value
  }
}
```

#### Test Categories (Per Repository)

| Category           | Tests | Complexity | Notes                                           |
|--------------------|-------|------------|-------------------------------------------------|
| **Happy Path**     | 3-4   | Low        | Normal operation, expected results              |
| **Edge Cases**     | 2-3   | Medium     | Empty results, null values, boundaries          |
| **Error Handling** | 1-2   | Medium     | SQL errors, constraint violations               |
| **Concurrency**    | 1-2   | High       | Lock contention, race conditions (outbox sweep) |

#### Estimated Test Coverage

| Repository        | Methods | Tests   | Target Coverage |
|-------------------|---------|---------|-----------------|
| CommandRepository | 6       | 20      | 85%             |
| DlqRepository     | 1       | 3       | 90%             |
| InboxRepository   | 1       | 4       | 95%             |
| OutboxRepository  | 7       | 35      | 80%             |
| ProcessRepository | 8       | 35      | 85%             |
| **TOTAL**         | **23**  | **~95** | **85% overall** |

### Key Test Considerations

1. **H2 JSON Handling**: Store/retrieve JSON as VARCHAR strings, verify JSON content in tests
2. **Locking Behavior**: H2 doesn't support SKIP LOCKED; test with H2SqlDialect.supportsSkipLocked() = false
3. **Timestamp Precision**: H2 timestamps may differ slightly; use ≈ comparisons, not exact equality
4. **Dialect Injection**: Verify `sqlDialect.dialectName()` equals "H2" in each test's setUp()

---

## Configuration for Different Environments

### Production (PostgreSQL)

```yaml
app:
  database:
    dialect: postgres  # Default
```

### Testing (H2)

```java
@MicronautTest
class TestClass extends JdbcRepositoryTestBase {
  // H2 automatically injected via H2TestConfiguration
}
```

### Future: SQL Server

```yaml
app:
  database:
    dialect: sqlserver
```

---

## Implementation Complete: Phase 1

✅ **Strategy Pattern Architecture**: 4 dialect implementations
✅ **Test Infrastructure**: H2 config, migrations, base class
✅ **Dependency Management**: H2 added to pom.xml

🔄 **Next Phase: Repository Tests** (6 tasks, ~40 hours)

- Write comprehensive tests for 5 repositories
- Achieve 80% code coverage + 80% branch coverage
- Test all SQL variations across H2 dialect

---

## How to Continue

1. **Start with simplest repository**: JdbcInboxRepository (1 simple method)
2. **Then**: JdbcDlqRepository (1 INSERT operation)
3. **Then**: JdbcCommandRepository (6 standard CRUD operations)
4. **Then**: JdbcProcessRepository (8 methods, manual JDBC)
5. **Finally**: JdbcOutboxRepository (7 complex operations, most challenging)

Each test class extends `JdbcRepositoryTestBase` and automatically gets:

- H2 DataSource
- H2SqlDialect
- JDBC utilities
- Table cleanup before each test
