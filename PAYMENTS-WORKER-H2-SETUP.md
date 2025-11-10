# Payments Worker: H2 & Repository Restructuring

## Overview

Restructure `msg-platform-payments-worker` repositories to follow the pattern in `msg-platform-persistence-jdbc`:

- Abstract base JDBC classes (template method pattern)
- H2-specific implementations (for testing)
- PostgreSQL implementations (for production)
- Flyway migrations for both databases

## Key Differences: PostgreSQL vs H2

### PostgreSQL (Production)

```sql
-- Schemas
CREATE SCHEMA payments;

-- Data types
CREATE TYPE account_status AS ENUM ('ACTIVE', 'CLOSED');
CREATE TABLE payments.account (
    account_id UUID PRIMARY KEY,
    status account_status NOT NULL,
    available_balance DECIMAL(19, 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- JSON
payload JSONB NOT NULL,
headers JSONB NOT NULL DEFAULT '{}'::JSONB,

-- Functions
gen_random_uuid()
```

### H2 (Testing)

```sql
-- No schemas (tables in default)
CREATE TABLE account (
    account_id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,  -- String enum
    available_balance DECIMAL(19, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- JSON as TEXT
payload TEXT NOT NULL,
headers TEXT NOT NULL DEFAULT '{}',

-- Auto increment
id BIGINT AUTO_INCREMENT PRIMARY KEY
```

## Directory Structure

### Before (Current)

```
msg-platform-payments-worker/src/
├── main/java/com/acme/payments/
│   ├── infrastructure/persistence/
│   │   ├── JdbcAccountRepository.java
│   │   ├── JdbcAccountLimitRepository.java
│   │   ├── JdbcPaymentRepository.java
│   │   └── JdbcFxContractRepository.java
│   └── domain/repository/
│       ├── AccountRepository.java (interface)
│       ├── AccountLimitRepository.java (interface)
│       ├── PaymentRepository.java (interface)
│       └── FxContractRepository.java (interface)
└── test/java/ (only unit tests)
```

### After (Restructured)

```
msg-platform-payments-worker/src/
├── main/java/com/acme/payments/
│   ├── infrastructure/persistence/
│   │   ├── base/
│   │   │   ├── JdbcAccountRepository.java (abstract)
│   │   │   ├── JdbcAccountLimitRepository.java (abstract)
│   │   │   ├── JdbcPaymentRepository.java (abstract)
│   │   │   └── JdbcFxContractRepository.java (abstract)
│   │   ├── h2/
│   │   │   ├── H2AccountRepository.java (@Requires(property = "db.dialect", value = "H2"))
│   │   │   ├── H2AccountLimitRepository.java
│   │   │   ├── H2PaymentRepository.java
│   │   │   └── H2FxContractRepository.java
│   │   └── postgres/
│   │       ├── PostgresAccountRepository.java (@Requires(property = "db.dialect", value = "PostgreSQL"))
│   │       ├── PostgresAccountLimitRepository.java
│   │       ├── PostgresPaymentRepository.java
│   │       └── PostgresFxContractRepository.java
│   └── domain/repository/
│       ├── AccountRepository.java (interface)
│       ├── AccountLimitRepository.java (interface)
│       ├── PaymentRepository.java (interface)
│       └── FxContractRepository.java (interface)
├── test/java/com/acme/payments/
│   ├── integration/
│   │   ├── PaymentCommandConsumerIntegrationTest.java
│   │   ├── PaymentWorkflowIntegrationTest.java
│   │   └── PaymentServiceIntegrationTest.java
│   └── testdata/
│       └── PaymentTestData.java
└── test/resources/
    ├── db/migration/h2/
    │   ├── V1__baseline_h2.sql
    │   └── V1.1__payments_schema_h2.sql
    └── application-test.yml
```

## Migration Files

### Test Migrations (H2)

**Location**: `msg-platform-payments-worker/src/test/resources/db/migration/h2/`

**V1__baseline_h2.sql**: Platform tables (command, inbox, outbox, process_instance)

- Remove schemas (no `CREATE SCHEMA payments`)
- Replace TIMESTAMPTZ with TIMESTAMP
- Replace JSONB with TEXT
- Replace `gen_random_uuid()` with UUID
- No ENUM types (use VARCHAR)
- Use AUTO_INCREMENT for sequences

**V1.1__payments_schema_h2.sql**: Payment-specific tables (account, transaction, limit, fx_contract, payment)

- Same H2 conversions as above
- Tables in default schema (no prefix)

### Production Migrations (PostgreSQL)

Keep existing migrations:

- `migrations/payments/V1__baseline.sql` (PostgreSQL native)
- `migrations/payments/V1.1__payments_schema.sql` (PostgreSQL native)

## Repository Implementation Pattern

### 1. Abstract Base Classes (JDBC Template Method)

**Location**: `msg-platform-payments-worker/src/main/java/com/acme/payments/infrastructure/persistence/base/`

```java
public abstract class JdbcAccountRepository {
    protected final DataSource dataSource;

    protected abstract String getInsertSql();
    protected abstract String getFindByIdSql();
    protected abstract String getUpdateSql();
    protected abstract String getDeleteSql();

    @Transactional
    public void create(Account account) {
        String sql = getInsertSql();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set parameters
            ps.executeUpdate();
        } catch (SQLException e) {
            throw ExceptionTranslator.translateException(e, "create account", LOG);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Account> findById(UUID id) {
        String sql = getFindByIdSql();
        // Implementation
    }

    // ... other CRUD methods
}
```

### 2. H2 Implementation

**Location**: `msg-platform-payments-worker/src/main/java/com/acme/payments/infrastructure/persistence/h2/`

```java
@Singleton
@Requires(property = "db.dialect", value = "H2")
public class H2AccountRepository extends JdbcAccountRepository implements AccountRepository {

    public H2AccountRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected String getInsertSql() {
        return """
            INSERT INTO account
            (account_id, customer_id, account_number, currency_code, account_type,
             transit_number, limit_based, available_balance, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    }

    @Override
    protected String getFindByIdSql() {
        return "SELECT * FROM account WHERE account_id = ?";
    }

    // ... other implementations
}
```

### 3. PostgreSQL Implementation

**Location**: `msg-platform-payments-worker/src/main/java/com/acme/payments/infrastructure/persistence/postgres/`

```java
@Singleton
@Requires(property = "db.dialect", value = "PostgreSQL")
public class PostgresAccountRepository extends JdbcAccountRepository implements AccountRepository {

    public PostgresAccountRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected String getInsertSql() {
        return """
            INSERT INTO payments.account
            (account_id, customer_id, account_number, currency_code, account_type,
             transit_number, limit_based, available_balance, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    }

    @Override
    protected String getFindByIdSql() {
        return "SELECT * FROM payments.account WHERE account_id = ?";
    }

    // ... other implementations
}
```

## Flyway Configuration

### Test Configuration: application-test.yml

```yaml
micronaut:
  application:
    name: msg-platform-payments-worker-test
  server:
    port: -1

datasources:
  default:
    url: jdbc:h2:mem:paymentdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password: ''
    dialect: POSTGRES
    schema-generate: CREATE_DROP
    maximum-pool-size: 10

flyway:
  datasources:
    default:
      enabled: true
      locations:
        - "classpath:db/migration/h2"  # H2-specific migrations for testing
      placeholders:
        schema: ""  # Empty schema for H2 (no schema prefix needed)

jms:
  consumers:
    enabled: true

db:
  dialect: H2  # Used by @Requires(property = "db.dialect", value = "H2")
```

### Production Configuration: application.yml

```yaml
datasources:
  default:
    url: ${DATABASE_URL}
    dialect: POSTGRES
    schema-generate: NONE
    maximum-pool-size: 20

flyway:
  datasources:
    default:
      enabled: true
      locations:
        - "classpath:../../../migrations/payments"  # PostgreSQL migrations
      schemas:
        - payments  # Explicit schema for PostgreSQL

jms:
  consumers:
    enabled: true

db:
  dialect: PostgreSQL  # Used by @Requires
```

## Implementation Steps

### Step 1: Create H2 Migrations

**File**: `msg-platform-payments-worker/src/test/resources/db/migration/h2/V1__baseline_h2.sql`

```sql
-- H2 Test Schema: Platform infrastructure (command, inbox, outbox, process)
-- All in default schema (no CREATE SCHEMA)
-- TIMESTAMP instead of TIMESTAMPTZ
-- TEXT instead of JSONB

CREATE TABLE command (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    business_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    retries INT NOT NULL DEFAULT 0,
    processing_lease_until TIMESTAMP,
    last_error TEXT,
    reply TEXT NOT NULL DEFAULT '{}',
    CONSTRAINT uk_command_name_bkey UNIQUE (name, business_key),
    CONSTRAINT uk_command_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE inbox (
    message_id VARCHAR(255) NOT NULL,
    handler VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id, handler)
);

CREATE TABLE outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(255) NOT NULL,
    topic VARCHAR(255),
    "key" VARCHAR(255),
    "type" VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    headers TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    attempts INT NOT NULL DEFAULT 0,
    next_at TIMESTAMP,
    claimed_at TIMESTAMP,
    claimed_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    last_error TEXT
);

CREATE INDEX idx_outbox_dispatch ON outbox(status, created_at);
CREATE INDEX idx_outbox_claimed ON outbox(status, claimed_at);

CREATE TABLE process_instance (
    process_id UUID PRIMARY KEY,
    process_type VARCHAR(255) NOT NULL,
    business_key VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_step VARCHAR(255) NOT NULL,
    data TEXT NOT NULL DEFAULT '{}',
    retries INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE process_log (
    process_id UUID NOT NULL,
    seq BIGINT AUTO_INCREMENT,
    at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event TEXT NOT NULL,
    PRIMARY KEY (process_id, seq)
);
```

**File**: `msg-platform-payments-worker/src/test/resources/db/migration/h2/V1.1__payments_schema_h2.sql`

```sql
-- H2 Test Schema: Payments domain tables
-- All in default schema
-- TIMESTAMP instead of TIMESTAMPTZ
-- TEXT instead of JSONB
-- VARCHAR instead of ENUM

CREATE TABLE account (
    account_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    currency_code VARCHAR(3) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    transit_number VARCHAR(20) NOT NULL,
    limit_based BOOLEAN NOT NULL DEFAULT false,
    available_balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_account_number ON account(account_number);
CREATE INDEX idx_account_customer ON account(customer_id);

CREATE TABLE transaction (
    transaction_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(account_id),
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    description TEXT,
    balance DECIMAL(19, 4) NOT NULL
);

CREATE INDEX idx_transaction_account ON transaction(account_id, transaction_date DESC);

CREATE TABLE account_limit (
    limit_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(account_id),
    period_type VARCHAR(20) NOT NULL,
    limit_amount DECIMAL(19, 4) NOT NULL,
    utilized DECIMAL(19, 4) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_limit_account_period ON account_limit(account_id, period_type, period_end);

CREATE TABLE fx_contract (
    fx_contract_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    debit_account_id UUID NOT NULL REFERENCES account(account_id),
    debit_amount DECIMAL(19, 4) NOT NULL,
    debit_currency_code VARCHAR(3) NOT NULL,
    credit_amount DECIMAL(19, 4) NOT NULL,
    credit_currency_code VARCHAR(3) NOT NULL,
    rate DECIMAL(19, 8) NOT NULL,
    value_date TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fx_contract_debit_account ON fx_contract(debit_account_id);
CREATE INDEX idx_fx_contract_customer ON fx_contract(customer_id);

CREATE TABLE payment (
    payment_id UUID PRIMARY KEY,
    debit_account_id UUID NOT NULL REFERENCES account(account_id),
    debit_transaction_id UUID REFERENCES transaction(transaction_id),
    fx_contract_id UUID REFERENCES fx_contract(fx_contract_id),
    debit_amount DECIMAL(19, 4) NOT NULL,
    debit_currency_code VARCHAR(3) NOT NULL,
    credit_amount DECIMAL(19, 4) NOT NULL,
    credit_currency_code VARCHAR(3) NOT NULL,
    value_date TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    beneficiary_name TEXT NOT NULL,
    beneficiary_account_number TEXT NOT NULL,
    beneficiary_transit_number TEXT,
    beneficiary_bank_name TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_debit_account ON payment(debit_account_id);
CREATE INDEX idx_payment_status ON payment(status, created_at);
```

### Step 2: Create Abstract JDBC Repository Base Classes

Base classes should:

- Use Template Method pattern
- Contain all CRUD logic
- Define abstract `getSql*()` methods for dialect-specific SQL
- Handle exception translation
- Use `@Transactional` annotations

### Step 3: Create H2 & PostgreSQL Implementations

Implementations should:

- Extend abstract base class
- Override `getSql*()` methods with dialect-specific SQL
- Use `@Singleton` + `@Requires(property = "db.dialect", value = "H2"|"PostgreSQL")`
- Implement domain `Repository` interface

### Step 4: Update application-test.yml

Configure:

- H2 DataSource URL with in-memory database
- Flyway to load H2 migrations from `classpath:db/migration/h2`
- `db.dialect: H2` property for conditional bean loading
- Disable auto schema generation (use Flyway instead)

### Step 5: Create Integration Tests

Test classes should:

- Use `@MicronautTest(environments = {"test"})`
- Inject repositories and services
- Send JMS commands via ActiveMQ
- Verify database state via repositories
- Verify response messages sent to reply queue

## Files to Create

```
msg-platform-payments-worker/src/main/java/com/acme/payments/infrastructure/persistence/
├── base/
│   ├── JdbcAccountRepository.java
│   ├── JdbcAccountLimitRepository.java
│   ├── JdbcPaymentRepository.java
│   ├── JdbcFxContractRepository.java
│   └── ExceptionTranslator.java (reuse from persistence-jdbc or copy)
├── h2/
│   ├── H2AccountRepository.java
│   ├── H2AccountLimitRepository.java
│   ├── H2PaymentRepository.java
│   └── H2FxContractRepository.java
└── postgres/
    ├── PostgresAccountRepository.java
    ├── PostgresAccountLimitRepository.java
    ├── PostgresPaymentRepository.java
    └── PostgresFxContractRepository.java

msg-platform-payments-worker/src/test/resources/
├── db/migration/h2/
│   ├── V1__baseline_h2.sql
│   └── V1.1__payments_schema_h2.sql
└── application-test.yml

msg-platform-payments-worker/src/test/java/com/acme/payments/
├── integration/
│   ├── PaymentCommandConsumerIntegrationTest.java
│   ├── PaymentWorkflowIntegrationTest.java
│   ├── PaymentServiceIntegrationTest.java
│   └── testdata/
│       └── PaymentTestData.java
```

## Files to Modify

1. **Existing Jdbc repositories**: Rename to `JdbcAccountRepository`, extract common logic
2. **Rename existing implementations**: `JdbcAccountRepository` → move to `base/`
3. **POM**: Ensure Flyway configured for test migrations
4. **application-test.yml**: Add Flyway + H2 datasource config

## Benefits

✅ **Testability**: H2 in-memory database, no external dependencies
✅ **Production Ready**: PostgreSQL implementation for real deployments
✅ **Consistency**: Follows msg-platform-persistence-jdbc pattern
✅ **Reusability**: Base classes can be extended for new repositories
✅ **Coverage**: Integration tests with real JMS + real database
✅ **No Testcontainers**: H2 migrations run via Flyway during test startup

## Challenges & Solutions

| Challenge                      | Solution                                             |
|--------------------------------|------------------------------------------------------|
| H2 lacks ENUM types            | Use VARCHAR(20) for status fields                    |
| H2 has no JSONB                | Use TEXT, parse as needed in code                    |
| H2 has no TIMESTAMPTZ          | Use TIMESTAMP, handle TZ in application layer        |
| H2 no RETURNING clause         | Separate SELECT after INSERT to get ID               |
| H2 AUTO_INCREMENT vs BIGSERIAL | Use AUTO_INCREMENT, adjust parameter binding         |
| Dual dialect SQL               | Use protected abstract methods, override per dialect |

## Testing Strategy

1. **Run migrations**: Flyway auto-runs on `@MicronautTest` startup
2. **Inject repositories**: Get H2 implementations via DI
3. **Send JMS commands**: Use embedded ActiveMQ
4. **Verify database**: Query repositories for state changes
5. **Verify responses**: Check reply queue messages

Example test:

```java
@MicronautTest(environments = {"test"})
class PaymentIntegrationTest {
    @Inject AccountRepository accountRepository;
    @Inject ConnectionFactory jmsFactory;

    @Test
    void testCreateAccountFlow() throws Exception {
        // 1. Send CreateAccount command via JMS
        sendJmsMessage("APP.CMD.CREATEACCOUNT.Q", commandJson);

        // 2. Wait for processing
        Thread.sleep(500);  // Or use Awaitility

        // 3. Verify account created in H2 database
        Optional<Account> account = accountRepository.findById(accountId);
        assertTrue(account.isPresent());
        assertEquals("ACTIVE", account.get().getStatus());

        // 4. Verify reply sent to queue
        String reply = receiveJmsMessage("APP.CMD.REPLY.Q");
        assertNotNull(reply);
    }
}
```
