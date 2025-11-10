# Detailed Comparison: Micronaut Data vs Raw JDBC in This Codebase

## Side-by-Side Implementation Comparison

### Outbox (Micronaut Data JDBC) vs Account (Raw JDBC)

#### Entity Definition

**Micronaut Data JDBC** (OutboxEntity):

```java

@MappedEntity("outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEntity {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    @MappedProperty(type = DataType.STRING)
    private String category;
    @MappedProperty(type = DataType.STRING)
    private String topic;
    @MappedProperty(value = "key", type = DataType.STRING)
    private String key;
    @MappedProperty(type = DataType.STRING)
    private String type;
    @MappedProperty(type = DataType.JSON)
    private String payload;
    @MappedProperty(type = DataType.JSON)
    private Map<String, String> headers;
    // 11 properties total
}
```

**Raw JDBC** (No entity for Account - uses domain model):

```java
public class Account {
    private UUID accountId;
    private UUID customerId;
    private String accountNumber;
    private String currencyCode;
    private AccountType accountType;
    private String transitNumber;
    private boolean limitBased;
    private Money availableBalance;
    private Instant createdAt;
    private List<Transaction> transactions;  // Complex aggregate
}
```

---

#### Repository Interface Definition

**Micronaut Data JDBC**:

```java

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcOutboxRepository
        extends OutboxRepository, GenericRepository<OutboxEntity, Long> {

    @Query(value = "...", nativeQuery = true)
    Optional<OutboxEntity> claimOne(long id, String claimer);

    @Query(value = "...", nativeQuery = true)
    List<OutboxEntity> claimBatch(int maxRecords, String claimer);

    @Query(value = "...", nativeQuery = true)
    void markPublished(long id);
}
```

**Raw JDBC**:

```java
public interface AccountRepository {
    void save(Account account);

    Optional<Account> findById(UUID accountId);

    Optional<Account> findByAccountNumber(String accountNumber);
}

// Implementation NOT an interface extension, pure POJO
@Singleton
@RequiredArgsConstructor
public class JdbcAccountRepository implements AccountRepository {
    private final DataSource dataSource;

    @Override
    public void save(Account account) {
        try (Connection conn = dataSource.getConnection()) {
            // Explicit JDBC code
        }
    }
}
```

---

#### Query Implementation

**Micronaut Data JDBC** (claimBatch):

```java

@Query(
        value = """
                WITH c AS (
                    SELECT id
                    FROM outbox
                    WHERE status = 'NEW'
                      AND (next_at IS NULL OR next_at <= now())
                    ORDER BY created_at
                    LIMIT :maxRecords
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox o
                SET status = 'CLAIMED', claimed_by = :claimer, attempts = o.attempts
                FROM c
                WHERE o.id = c.id
                RETURNING o.id, o.category, ...
                """,
        nativeQuery = true)
List<OutboxEntity> claimBatch(int maxRecords, String claimer);
```

**Raw JDBC** (save):

```java
private void saveTransactions(Connection conn, Account account) throws SQLException {
    String deleteSql = "DELETE FROM transaction WHERE account_id = ?";
    try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
        stmt.setObject(1, account.getAccountId());
        stmt.executeUpdate();
    }

    String insertSql = """
            INSERT INTO transaction (transaction_id, account_id, transaction_date, ...)
            VALUES (?, ?, ?, ...)
            """;
    try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
        for (Transaction txn : account.getTransactions()) {
            stmt.setObject(1, txn.transactionId());
            stmt.setObject(2, txn.accountId());
            stmt.setTimestamp(3, Timestamp.from(txn.transactionDate()));
            // 8 parameters total
            stmt.addBatch();
        }
        stmt.executeBatch();
    }
}
```

---

#### Result Mapping

**Micronaut Data JDBC** (Automatic):

```java
// No explicit mapping - Micronaut Data does this:
// SELECT id, category, topic, key, type, payload, headers, status, ...
// Maps ResultSet columns directly to OutboxEntity fields by name
```

**Raw JDBC** (Manual):

```java
private Account mapAccount(ResultSet rs) throws SQLException {
    return AccountMapper.mapFromResultSet(rs);
}

// In AccountMapper:
public static Account mapFromResultSet(ResultSet rs) throws SQLException {
    UUID accountId = (UUID) rs.getObject("account_id");
    UUID customerId = (UUID) rs.getObject("customer_id");
    String accountNumber = rs.getString("account_number");
    String currencyCode = rs.getString("currency_code");
    AccountType accountType = AccountType.valueOf(rs.getString("account_type"));
    // ... 10 lines of explicit mapping

    return new Account(accountId, customerId, accountNumber, ...);
}
```

---

## Feature Comparison Matrix

| Feature                   | Micronaut Data JDBC                        | Raw JDBC                        |
|---------------------------|--------------------------------------------|---------------------------------|
| **Compile-time Setup**    | 5 lines (pom.xml config + 2 compiler args) | None                            |
| **Entity Classes**        | Required (@MappedEntity)                   | Optional (use domain models)    |
| **Repository Interface**  | Extends GenericRepository                  | Plain interface                 |
| **Implementation**        | Auto-generated by processor                | Hand-written                    |
| **Transactions**          | Implicit, no explicit handling             | @Transactional annotation       |
| **DataSource Injection**  | Automatic, hidden                          | Explicit constructor            |
| **Query Syntax**          | @Query(nativeQuery=true)                   | String + PreparedStatement      |
| **Parameter Binding**     | Named parameters (:param)                  | Positional (?)                  |
| **Result Mapping**        | Automatic by name                          | Manual in code                  |
| **Batch Operations**      | Via addBatch() in generated code           | Manual addBatch()               |
| **Connection Management** | Hidden                                     | Try-with-resources              |
| **Complex Aggregates**    | Difficult                                  | Natural fit                     |
| **Multiple Tables**       | Requires @Embeddable or joins              | Join and map manually           |
| **Learning Curve**        | Moderate (framework-specific)              | Minimal (standard JDBC)         |
| **Boilerplate**           | Less (annotations do work)                 | More (explicit mapping)         |
| **Type Safety**           | Partial (interface methods are typed)      | Full (method signature checked) |
| **Performance**           | Generated code optimized                   | Developer-dependent             |
| **Debugging**             | Generated code harder to trace             | Direct JDBC calls               |

---

## Lines of Code Comparison

### Simple CRUD: Save Operation

**Micronaut Data JDBC**:

```java
// Interface (2 lines + @Query annotation with SQL)
// Implementation: AUTO-GENERATED (0 lines written)
// Total: ~15 lines (SQL + annotation)
```

**Raw JDBC**:

```java
private void insertAccount(Connection conn, Account account) throws SQLException {
    String sql = """
            INSERT INTO account (account_id, customer_id, account_number, 
                               currency_code, account_type, transit_number, 
                               limit_based, available_balance, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, account.getAccountId());
        stmt.setObject(2, account.getCustomerId());
        stmt.setString(3, account.getAccountNumber());
        stmt.setString(4, account.getCurrencyCode());
        stmt.setString(5, account.getAccountType().name());
        stmt.setString(6, account.getTransitNumber());
        stmt.setBoolean(7, account.isLimitBased());
        stmt.setBigDecimal(8, account.getAvailableBalance().amount());
        stmt.setTimestamp(9, Timestamp.from(account.getCreatedAt()));

        stmt.executeUpdate();
    }
}
// Total: ~20 lines
```

**Raw JDBC with nested transaction handling** (~50 lines):

```java

@Override
public void save(Account account) {
    log.debug("Saving account: {}", account.getAccountId());

    try (Connection conn = dataSource.getConnection()) {
        boolean exists = accountExists(conn, account.getAccountId());

        if (exists) {
            updateAccount(conn, account);
        } else {
            insertAccount(conn, account);
        }

        saveTransactions(conn, account);

    } catch (SQLException e) {
        log.error("Error saving account: {}", account.getAccountId(), e);
        throw new RuntimeException("Failed to save account", e);
    }
}
```

---

## Deployment Impact

### Configuration Complexity

**Micronaut Data JDBC**:

```yaml
datasources:
  default:
    url: jdbc:postgresql://localhost:5432/reliable
    # Flyway auto-runs for default datasource
flyway:
  datasources:
    default:
      enabled: true
```

**Raw JDBC**:

```yaml
datasources:
  default:
    url: jdbc:postgresql://localhost:5432/payments
    # Bean registered as DataSource
```

**Identical YAML** - difference is only in how repositories use the datasource

---

## When to Use Which Approach

### Use Micronaut Data JDBC When:

1. Tables have simple 1:1 mapping to entities
2. CRUD operations are straightforward (save, find, delete)
3. No complex aggregates or nested objects
4. Query DSL is sufficient (or native SQL is acceptable)
5. You want auto-generated implementations
6. Minimal code is priority
7. Example: Outbox, Inbox, Command tables

### Use Raw JDBC When:

1. Domain models have complex nested structures (aggregates)
2. Save operation involves multiple tables (Account + Transactions)
3. Custom result mapping is needed
4. Performance is critical and you control execution
5. Type safety of domain models is important
6. You want explicit control over JDBC operations
7. Transactions span multiple tables in application logic
8. Example: Account (with transactions), Payment (with FX)

---

## Migration Path: Outbox Example

### Step 1: Outbox Started as Simple Entity

```java

@MappedEntity("outbox")
public class OutboxEntity {
    private Long id;
    private String category;
    private String topic;
    // ...
}

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcOutboxRepository extends OutboxRepository {
    // Simple CRUD
}
```

### Step 2: Added Complex Queries

```java

@Query(value = "...", nativeQuery = true)
List<OutboxEntity> claimBatch(int maxRecords, String claimer);

@Query(value = "...", nativeQuery = true)
Optional<OutboxEntity> claimIfNew(long id);
```

### Step 3: Alternative Dao Pattern

```java

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcOutboxDao extends OutboxDao, GenericRepository<OutboxEntity, Long> {
    // Same as repository but different interface naming
    // Allows different service bindings
}
```

**Lesson**: Even with Micronaut Data, complex queries require native SQL. For aggregates like Account, raw JDBC from
start would be cleaner.

---

## Risk Analysis

### Micronaut Data JDBC Risks:

1. Vendor lock-in (Micronaut-specific annotations)
2. Generated code hard to debug
3. Limited to default schema/database per datasource
4. No easy multi-tenancy or schema routing
5. Compiler changes affect generated code

### Raw JDBC Risks:

1. SQL injection if not careful with parameters
2. Manual mapping errors (typos in column names)
3. More boilerplate code
4. Developer discipline required for transactions
5. Less IDE support for SQL validation

**Current codebase manages risks by combining both**: Simple tables use Micronaut Data, complex aggregates use Raw JDBC.

