# Messaging Platform Framework - Developer's Guide

## Table of Contents

1. [Introduction](#1-introduction)
2. [Core Concepts](#2-core-concepts)
3. [Getting Started](#3-getting-started)
4. [Command Handling](#4-command-handling)
5. [Process Orchestration](#5-process-orchestration)
6. [Domain Modeling](#6-domain-modeling)
7. [Testing Strategy](#7-testing-strategy)
8. [Configuration and Deployment](#8-configuration-and-deployment)
9. [Best Practices and Anti-Patterns](#9-best-practices-and-anti-patterns)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Introduction

### 1.1 What is This Framework?

This is a **distributed messaging platform framework** for building reliable, event-driven applications with:

- **Command handling** - Type-safe, auto-discovered command handlers
- **Process orchestration** - Multi-step workflows with saga pattern support
- **Reliable messaging** - Outbox/inbox patterns for exactly-once delivery
- **Idempotency** - Built-in protection against duplicate processing
- **Transactional guarantees** - Each step executes within database transactions
- **Automatic compensation** - Saga pattern for distributed rollback

### 1.2 Architecture Overview

```
┌───────────────────────────────────────────────────────────────┐
│                        Application Layer                      │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐   │
│  │   Commands   │  │   Services   │  │ Process Definitions│   │
│  └──────────────┘  └──────────────┘  └────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
                              ↓
┌───────────────────────────────────────────────────────────────┐
│                        Framework Layer                        │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐   │
│  │  CommandBus  │  │ProcessManager│  │ Auto-Discovery     │   │
│  └──────────────┘  └──────────────┘  └────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
                              ↓
┌───────────────────────────────────────────────────────────────┐
│                     Infrastructure Layer                      │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐   │
│  │  Outbox/Inbox│  │  PostgreSQL  │  │   IBM MQ / Kafka   │   │
│  └──────────────┘  └──────────────┘  └────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
```

### 1.3 Reference Implementation

The **payments-worker** module serves as a complete reference implementation demonstrating:

- Account management with transactions
- Limit-based controls
- FX contract booking
- Multi-step payment processing
- Compensation flows

---

## 2. Core Concepts

### 2.1 Commands

**Commands** represent the intention to perform an action. They are:

- **Immutable** - Typically Java records
- **Serializable** - Converted to/from JSON
- **Self-validating** - Validation logic in constructors
- **Type-safe** - Compile-time checking

**Example:**

```java
public record CreateAccountCommand(
    UUID customerId,
    String currencyCode,
    AccountType accountType,
    String transitNumber,
    boolean limitBased,
    List<AccountLimitDefinition> limits
) implements DomainCommand {
    // Validation in compact constructor if needed
    public CreateAccountCommand {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId is required");
        }
    }
}
```

### 2.2 Command Handlers

**Command Handlers** are service methods that:

- Accept a single `DomainCommand` parameter
- Are **auto-discovered** by the framework
- Execute within `@Transactional` boundaries
- Return results that become `CommandReply.data`

**Example:**

```java
@Singleton
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;

    @Transactional
    public Account createAccount(CreateAccountCommand cmd) {
        Account account = new Account(
            UUID.randomUUID(),
            cmd.customerId(),
            generateAccountNumber(),
            cmd.currencyCode(),
            cmd.accountType(),
            cmd.transitNumber(),
            cmd.limitBased(),
            Money.zero(cmd.currencyCode())
        );

        accountRepository.save(account);
        return account;
    }
}
```

### 2.3 Process Definitions

**Process Definitions** orchestrate multi-step workflows:

- Define process graphs using fluent DSL
- Coordinate multiple commands
- Handle conditional branching
- Manage parallel execution
- Execute compensations on failure

**Example:**

```java
@Singleton
public class SimplePaymentProcessDefinition implements ProcessConfiguration {

    @Override
    public String getProcessType() {
        return "SimplePayment";
    }

    @Override
    public ProcessGraph defineProcess() {
        return process()
            .startWith(BookLimitsCommand.class)
                .withCompensation(ReverseLimitsCommand.class)
            .thenIf(data -> (Boolean) data.get("requiresFx"))
                .whenTrue(BookFxCommand.class)
                    .withCompensation(UnwindFxCommand.class)
            .then(CreateTransactionCommand.class)
                .withCompensation(ReverseTransactionCommand.class)
            .then(CreatePaymentCommand.class)
            .end();
    }
}
```

### 2.4 Process Manager

The **Process Manager** is the orchestration engine that:

- Starts process instances
- Executes steps sequentially or in parallel
- Handles command replies (success/failure/timeout)
- Manages retries with exponential backoff
- Executes compensation flows on failure
- Tracks process state in the database

**Key behavior:**

- Each step executes in its own transaction
- State is persisted before and after each step
- Process auto-completes when reaching terminal step (`.end()`)
- No explicit completion command needed

### 2.5 Outbox/Inbox Pattern

**Outbox Pattern** ensures reliable message delivery:

1. Command written to `outbox` table in same transaction as business data
2. Separate `OutboxRelay` process sweeps outbox every second
3. Messages published to IBM MQ or Kafka
4. Outbox entries marked as DISPATCHED after successful publish

**Inbox Pattern** ensures idempotent message processing:

1. Incoming message ID recorded in `inbox` table
2. If already processed, message is ignored
3. Database constraint prevents duplicate processing
4. Works across service restarts

**Why this matters:**

- Database commit = message commit (exactly-once semantics)
- No lost messages on failure
- No duplicate processing
- Safe retries

---

## 3. Getting Started

### 3.1 Creating a New Worker Module

```bash
# 1. Create module structure
mkdir -p msg-platform-myapp-worker/src/main/java/com/acme/myapp
mkdir -p msg-platform-myapp-worker/src/main/resources/db/migration
mkdir -p msg-platform-myapp-worker/src/test/java/com/acme/myapp

# 2. Add dependencies to build.gradle
dependencies {
    implementation project(':msg-platform-core')
    implementation project(':msg-platform-processor')
    implementation project(':msg-platform-persistence-jdbc')

    implementation 'io.micronaut:micronaut-runtime'
    implementation 'io.micronaut.data:micronaut-data-jdbc'
    implementation 'org.postgresql:postgresql'
    // ... other dependencies
}

# 3. Create application.yml
# 4. Create Dockerfile
# 5. Add to docker-compose.yml
```

### 3.2 Project Structure

```
msg-platform-myapp-worker/
├── src/
│   ├── main/
│   │   ├── java/com/acme/myapp/
│   │   │   ├── application/command/     # Command definitions
│   │   │   ├── domain/
│   │   │   │   ├── model/               # Domain entities
│   │   │   │   ├── repository/          # Repository interfaces
│   │   │   │   └── service/             # Domain services (handlers)
│   │   │   ├── infrastructure/
│   │   │   │   └── persistence/         # Repository implementations
│   │   │   └── orchestration/           # Process definitions
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           └── V3__myapp_schema.sql
│   └── test/
│       └── java/com/acme/myapp/
│           ├── domain/model/            # Unit tests
│           ├── domain/service/          # Service tests
│           └── infrastructure/          # Integration tests
└── Dockerfile
```

### 3.3 Essential Configuration

**application.yml:**

```yaml
micronaut:
  application:
    name: myapp-worker
  server:
    port: ${MYAPP_PORT:9092}

datasources:
  default:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:myapp}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
    maximum-pool-size: ${HIKARI_MAX_POOL_SIZE:50}

flyway:
  datasources:
    default:
      enabled: true
      locations: classpath:db/migration

mq:
  host: ${MQ_HOST:localhost}
  port: ${MQ_PORT:1414}
  qmgr: ${MQ_QMGR:QM1}
  channel: ${MQ_CHANNEL:DEV.APP.SVRCONN}
  required-queues: ${MQ_REQUIRED_QUEUES:APP.CMD.MYCOMMAND.Q}

messaging:
  consumers:
    enabled: ${JMS_CONSUMERS_ENABLED:true}
    concurrency: ${JMS_CONCURRENCY:10}
```

---

## 4. Command Handling

### 4.1 Auto-Discovery Convention

The framework automatically discovers command handlers using **convention over configuration**:

**Convention Rules:**

1. Command class must implement `DomainCommand`
2. Command class name must end with "Command"
3. Service method must have exactly one parameter of command type
4. Method will be registered as handler for derived command type

**Example:**

```java
// 1. Define command
public record CreateOrderCommand(
    UUID customerId,
    List<OrderItem> items
) implements DomainCommand {}

// 2. Service method is auto-discovered
@Singleton
public class OrderService {
    // Auto-registered as handler for "CreateOrder"
    @Transactional
    public Order createOrder(CreateOrderCommand cmd) {
        // Implementation
    }
}
```

**Command Type Derivation:**

- `CreateOrderCommand` → `"CreateOrder"`
- `UpdateInventoryCommand` → `"UpdateInventory"`
- `CancelPaymentCommand` → `"CancelPayment"`

### 4.2 Handler Registration Process

**Phase 1: Discovery (at startup)**

```
1. Scan all beans in application context
2. For each bean, scan all methods
3. Find methods with single DomainCommand parameter
4. Collect all candidates (including proxy versions)
```

**Phase 2: Consolidation**

```
1. Group candidates by command type
2. Prefer proxy beans (for @Transactional support)
3. Detect ambiguous registrations (multiple different handlers)
4. Register selected handler
```

**Important:** The framework prefers **proxy beans** created by Micronaut's AOP (e.g., for `@Transactional`) over plain
beans. This ensures transactional behavior is preserved.

### 4.3 Command Reply

Command handlers return results that are automatically wrapped in `CommandReply`:

```java
public sealed interface CommandReply {
    record Completed(UUID commandId, UUID correlationId, Map<String, Object> data) {}
    record Failed(UUID commandId, UUID correlationId, String error) {}
    record TimedOut(UUID commandId, UUID correlationId, String error) {}
}
```

**Return Value Handling:**

- `void` methods → Empty data map
- Non-void methods → Serialized to `Map<String, Object>` via JSON
- Exceptions → Wrapped in `Failed` reply

**Example:**

```java
// Handler returns domain object
public Payment createPayment(CreatePaymentCommand cmd) {
    Payment payment = new Payment(...);
    paymentRepository.save(payment);
    return payment; // Serialized to CommandReply.data
}

// Process receives reply data
CommandReply reply = CommandReply.completed(
    commandId,
    correlationId,
    Map.of(
        "paymentId", "uuid-here",
        "status", "SUBMITTED",
        "amount", 100.0
    )
);
```

### 4.4 Idempotency Keys

Every command requires an **idempotency key** to prevent duplicate execution:

```java
UUID commandId = commandBus.accept(
    "CreateOrder",
    "process-123:CreateOrder", // Idempotency key
    "order-456",                // Business key
    jsonPayload,
    headers
);

// Duplicate submission with same key returns existing commandId
// Throws exception if key already processed
```

**Key Patterns:**

- Process step: `processId + ":" + stepName`
- API request: `requestId`
- Message: `messageId + ":" + handlerName`

### 4.5 Transaction Boundaries

Each command handler executes within a **single database transaction**:

```java
@Transactional
public Order createOrder(CreateOrderCommand cmd) {
    // 1. Read from database
    Customer customer = customerRepo.findById(cmd.customerId())
        .orElseThrow();

    // 2. Business logic
    Order order = new Order(customer, cmd.items());

    // 3. Write to database
    orderRepository.save(order);

    // 4. Write to outbox (for events/replies)
    // Framework handles this automatically

    // All committed atomically
    return order;
}
```

**What's Included in Transaction:**

- Command record insertion
- Handler execution (business logic + repository calls)
- Outbox entries for replies/events
- Process state updates (if part of process)

**What's NOT in Transaction:**

- Message publishing to IBM MQ/Kafka (handled by OutboxRelay)
- Subsequent process steps (each step has own transaction)

---

## 5. Process Orchestration

### 5.1 When to Use Process Orchestration

**Use Process Manager when:**

- Multiple steps that must execute in order
- Steps span multiple aggregates or services
- Compensation/rollback needed on failure
- Parallel execution required
- Complex conditional branching

**Use Simple Command Handler when:**

- Single aggregate operation
- No compensation needed
- Synchronous execution acceptable

**Examples:**

| Scenario                         | Solution                       |
|----------------------------------|--------------------------------|
| Create account + set limits      | ✅ Process (2 aggregates)       |
| Update account balance           | ❌ Simple handler (1 aggregate) |
| Payment: debit + credit + notify | ✅ Process (3+ steps)           |
| Query account by ID              | ❌ Simple handler (read-only)   |
| Parallel FX + limits booking     | ✅ Process (parallel execution) |

### 5.2 Process Graph DSL

The **ProcessGraphBuilder** provides a fluent DSL for defining workflows:

#### 5.2.1 Sequential Steps

```java
return process()
    .startWith(Step1Command.class)
    .then(Step2Command.class)
    .then(Step3Command.class)
    .end();
```

**Execution:**

```
Step1 → Step2 → Step3 → [COMPLETED]
```

#### 5.2.2 Conditional Branching

**Optional Branch (if-then):**

```java
return process()
    .startWith(BookLimitsCommand.class)
    .thenIf(data -> (Boolean) data.get("requiresFx"))
        .whenTrue(BookFxCommand.class)  // Execute if true
    .then(CreateTransactionCommand.class) // Continue either way
    .end();
```

**Execution:**

```
BookLimits → [if requiresFx] → BookFx → CreateTransaction → [COMPLETED]
           → [else] ────────────────────→
```

**Full If-Else:**

```java
return process()
    .startWith(ValidateCommand.class)
    .thenIf(data -> data.get("valid").equals(true))
        .whenTrue(ProcessCommand.class)
            .then(CompleteCommand.class)
        .whenFalse(RejectCommand.class)
            .then(CompleteCommand.class)
    .end();
```

#### 5.2.3 Parallel Execution

```java
return process()
    .startWith(InitiateCommand.class)
    .thenParallel()
        .branch(BookFxCommand.class)
        .branch(BookLimitsCommand.class)
        .branch(ValidateBeneficiaryCommand.class)
    .joinAt(CreatePaymentCommand.class) // Wait for all branches
    .end();
```

**Execution:**

```
Initiate → ┬─ BookFx ──────────────┬─→ CreatePayment → [COMPLETED]
           ├─ BookLimits ──────────┤
           └─ ValidateBeneficiary ─┘
```

**Key behavior:**

- All branches execute concurrently
- `joinAt` waits for all branches to complete
- Any branch failure fails entire process
- Compensation executes for completed branches

#### 5.2.4 Compensation (Saga Pattern)

```java
return process()
    .startWith(BookLimitsCommand.class)
        .withCompensation(ReverseLimitsCommand.class)
    .then(BookFxCommand.class)
        .withCompensation(UnwindFxCommand.class)
    .then(CreateTransactionCommand.class)
        .withCompensation(ReverseTransactionCommand.class)
    .then(CreatePaymentCommand.class) // No compensation (terminal)
    .end();
```

**Normal execution:**

```
BookLimits → BookFx → CreateTransaction → CreatePayment → [COMPLETED]
```

**Failure at CreateTransaction:**

```
BookLimits → BookFx → CreateTransaction [FAILED]
                    ↓
UnwindFx ← ReverseLimits ← [COMPENSATING] → [COMPENSATED]
```

**Compensation Rules:**

1. Executes in **reverse order** of completion
2. Only compensates **completed steps**
3. Compensation must be **idempotent**
4. Terminal step typically has no compensation

### 5.3 Process Lifecycle

```
                    startProcess()
                         ↓
                    ┌─────────┐
                    │   NEW   │
                    └─────────┘
                         ↓ execute first step
                    ┌─────────┐
              ┌─────│ RUNNING │─────┐
              │     └─────────┘     │
              │          ↓          │
              │    handleReply()    │
              │          ↓          │
              │   ┌──────────┐      │
              │   │ Continue │      │ failure
         success  │   next   │      │
              │   │   step   │      │
              │   └──────────┘      │
              ↓         ↑           ↓
        ┌──────────┐    │    ┌──────────────┐
        │SUCCEEDED │    └────│ COMPENSATING │
        └──────────┘         └──────────────┘
                                     ↓
                              ┌─────────────┐
                              │ COMPENSATED │
                              │  or FAILED  │
                              └─────────────┘
```

### 5.4 Process State Management

**ProcessInstance** holds current state:

```java
public record ProcessInstance(
    UUID processId,
    String processType,
    String businessKey,     // Domain identifier (e.g., paymentId)
    ProcessStatus status,
    String currentStep,     // Current command being executed
    Map<String, Object> data, // Process state (accumulated from replies)
    int retries,
    Instant createdAt,
    Instant updatedAt
) {}
```

**State Evolution:**

```java
// Initial state
Map<String, Object> initialData = Map.of(
    "customerId", uuid,
    "amount", 100.0,
    "requiresFx", true
);

// After BookLimits reply
data = {
    "customerId": uuid,
    "amount": 100.0,
    "requiresFx": true,
    "limitId": limitUuid,        // Added from reply
    "limitBookedAmount": 100.0
}

// After BookFx reply
data = {
    ...,
    "fxContractId": fxUuid,      // Added from reply
    "exchangeRate": 1.25
}
```

**Key Behaviors:**

- Reply data is **merged** into process data
- Each step can access all previous step results
- Data is serialized to JSON in database
- Process completes when `getNextStep()` returns empty

### 5.5 Error Handling and Retries

#### 5.5.1 Retry Configuration

```java
@Override
public boolean isRetryable(String step, String error) {
    // Transient errors - retry
    return error != null && (
        error.contains("timeout") ||
        error.contains("connection") ||
        error.contains("temporary") ||
        error.contains("deadlock")
    );
}

@Override
public int getMaxRetries(String step) {
    // Allow 3 retry attempts
    return 3;
}

@Override
public long getRetryDelay(String step, int attempt) {
    // Exponential backoff: 1s, 2s, 4s
    long baseDelay = 1000L;
    return baseDelay * (1L << attempt); // 2^attempt seconds
}
```

#### 5.5.2 Retry Flow

```
Step Failed
    ↓
Is Retryable?
    ├─ Yes ─→ retries < maxRetries?
    │             ├─ Yes ─→ Sleep(backoff) ─→ Retry
    │             └─ No ──→ Compensation
    └─ No ──→ Compensation
```

#### 5.5.3 Permanent Failures

```java
// Domain exceptions should NOT be retried
public class InsufficientFundsException extends RuntimeException {
    // This is a business rule violation, not a transient error
}

@Override
public boolean isRetryable(String step, String error) {
    if (error != null && error.contains("InsufficientFunds")) {
        return false; // Don't retry, go straight to compensation
    }
    return error.contains("timeout") || error.contains("connection");
}
```

### 5.6 Process Completion

**Automatic Completion:**

The process **automatically completes** when reaching a terminal step:

```java
// In BaseProcessManager.handleSequentialStepCompleted()
Optional<String> nextStep = getGraph(processType).getNextStep(currentStep, data);

if (nextStep.isPresent()) {
    // Continue to next step
    executeStep(updated, definition);
} else {
    // No next step → Process complete
    ProcessInstance completed = instance.update(ProcessStatus.SUCCEEDED, currentStep, data, 0);
    ProcessEvent completeEvent = new ProcessEvent.ProcessCompleted("All steps completed successfully");
    getProcessRepository().update(completed, completeEvent);
}
```

**Key Points:**

- `.end()` creates a terminal step that returns `Optional.empty()`
- No explicit completion command needed
- Terminal command handlers can be no-ops
- Useful as convergence points for conditional branches

**Example:**

```java
// CompleteAccountCreationCommand serves as convergence point
return process()
    .startWith(CreateAccountCommand.class)
    .thenIf(data -> (Boolean) data.get("limitBased"))
        .whenTrue(CreateLimitsCommand.class)
    .then(CompleteAccountCreationCommand.class) // Convergence + terminal
    .end();

// Handler can be minimal
public Map<String, Object> handleCompleteAccountCreation(CompleteAccountCreationCommand cmd) {
    log.info("Account creation completed: {}", cmd.accountId());
    return Map.of("status", "completed");
}
```

---

## 6. Domain Modeling

### 6.1 Aggregates and Entities

**Aggregate Root Example:**

```java
@Getter
public class Account {
    private final UUID accountId;           // Identity
    private final UUID customerId;
    private final String accountNumber;
    private final String currencyCode;
    private final AccountType accountType;
    private final boolean limitBased;
    private final List<Transaction> transactions; // Child entities
    private Money availableBalance;         // Mutable state

    public Transaction createTransaction(TransactionType type, Money amount, String description) {
        // Validate
        if (!amount.currencyCode().equals(currencyCode)) {
            throw new IllegalArgumentException("Currency mismatch");
        }

        Money newBalance = calculateNewBalance(type, amount);

        // Business rule
        if (!limitBased && type.isDebit() && newBalance.isNegative()) {
            throw new InsufficientFundsException(accountId, availableBalance, amount);
        }

        // Create transaction
        Transaction transaction = new Transaction(...);
        transactions.add(transaction);
        availableBalance = newBalance;

        return transaction;
    }
}
```

**Aggregate Design Principles:**

1. **Single aggregate per transaction** - Avoid loading multiple aggregates
2. **Consistency boundary** - All invariants enforced within aggregate
3. **Identity-based references** - Reference other aggregates by ID only
4. **No lazy loading** - Load all data needed upfront

### 6.2 Value Objects

**Immutable with Business Logic:**

```java
public record Money(BigDecimal amount, String currencyCode) {
    public Money {
        if (amount == null) throw new IllegalArgumentException("amount required");
        if (currencyCode == null) throw new IllegalArgumentException("currency required");
    }

    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, currencyCode);
    }

    public static Money of(double amount, String currencyCode) {
        return new Money(BigDecimal.valueOf(amount), currencyCode);
    }

    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(amount.add(other.amount), currencyCode);
    }

    public Money subtract(Money other) {
        validateSameCurrency(other);
        return new Money(amount.subtract(other.amount), currencyCode);
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
}
```

**Benefits:**

- Encapsulates domain logic
- Prevents invalid states
- Type safety (can't add Money to BigDecimal)
- Immutable (thread-safe)

### 6.3 Domain Services

**When to Use:**

- Operations spanning multiple aggregates
- Complex calculations
- External service coordination

**Example:**

```java
@Singleton
@RequiredArgsConstructor
public class LimitService {
    private final AccountLimitRepository limitRepository;

    @Transactional
    public AccountLimit bookLimits(BookLimitsCommand cmd) {
        // Load existing limits
        List<AccountLimit> limits = limitRepository.findByAccountId(cmd.accountId());

        // Find applicable limit
        AccountLimit limit = limits.stream()
            .filter(l -> l.canAccommodate(cmd.amount(), cmd.transactionType()))
            .findFirst()
            .orElseThrow(() -> new LimitExceededException(...));

        // Book amount
        AccountLimit updated = limit.book(cmd.amount());
        limitRepository.update(updated);

        return updated;
    }
}
```

### 6.4 Repository Pattern

**Interface (Domain Layer):**

```java
public interface AccountRepository {
    void save(Account account);
    Optional<Account> findById(UUID accountId);
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByCustomerId(UUID customerId);
}
```

**Implementation (Infrastructure Layer):**

```java
@Singleton
@RequiredArgsConstructor
public class JdbcAccountRepository implements AccountRepository {
    private final AccountMapper accountMapper;
    private final TransactionMapper transactionMapper;
    private final TransactionOperations<Connection> tx;

    @Override
    public void save(Account account) {
        tx.executeWrite(status -> {
            // 1. Upsert account
            accountMapper.upsert(toEntity(account));

            // 2. Insert new transactions
            for (Transaction txn : account.getTransactions()) {
                transactionMapper.insert(toEntity(txn));
            }

            return null;
        });
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        return tx.executeRead(status -> {
            // 1. Load account
            Optional<AccountEntity> entity = accountMapper.findById(accountId);
            if (entity.isEmpty()) return Optional.empty();

            // 2. Load transactions
            List<TransactionEntity> txnEntities =
                transactionMapper.findByAccountId(accountId);

            // 3. Reconstruct aggregate
            Account account = toDomain(entity.get());
            txnEntities.forEach(txn ->
                account.restoreTransaction(toDomain(txn)));

            return Optional.of(account);
        });
    }
}
```

**Key Patterns:**

- Repositories hide persistence details
- Aggregate loaded/saved as a whole
- Transactions managed by framework
- Domain model stays clean

---

## 7. Testing Strategy

### 7.1 Test Pyramid

```
        ┌─────────────┐
        │   E2E (5)   │  Full workflows, real database
        └─────────────┘
       ┌───────────────┐
       │Integration(17)│  Repositories, database, mocked framework
       └───────────────┘
    ┌────────────────────┐
    │  Unit Tests (33)   │  Domain logic, no infrastructure
    └────────────────────┘
```

### 7.2 Unit Tests

**Domain Model:**

```java
@Test
void shouldCreateTransactionAndUpdateBalance() {
    Account account = new Account(
        UUID.randomUUID(), customerId, "ACC123",
        "USD", AccountType.CHECKING, "001", false,
        Money.of(1000, "USD")
    );

    Transaction txn = account.createTransaction(
        TransactionType.DEBIT,
        Money.of(100, "USD"),
        "Purchase"
    );

    assertEquals(Money.of(900, "USD"), account.getAvailableBalance());
    assertEquals(TransactionType.DEBIT, txn.transactionType());
}

@Test
void shouldThrowInsufficientFundsException() {
    Account account = new Account(..., Money.of(50, "USD"));

    assertThrows(InsufficientFundsException.class, () ->
        account.createTransaction(
            TransactionType.DEBIT,
            Money.of(100, "USD"),
            "Overdraft attempt"
        )
    );
}
```

**Value Objects:**

```java
@Test
void shouldAddMoneyWithSameCurrency() {
    Money m1 = Money.of(100, "USD");
    Money m2 = Money.of(50, "USD");

    Money result = m1.add(m2);

    assertEquals(Money.of(150, "USD"), result);
}

@Test
void shouldThrowWhenAddingDifferentCurrencies() {
    Money m1 = Money.of(100, "USD");
    Money m2 = Money.of(50, "EUR");

    assertThrows(IllegalArgumentException.class, () -> m1.add(m2));
}
```

### 7.3 Integration Tests

**Setup with Testcontainers:**

```java
@MicronautTest(environments = "test", startApplication = false)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAccountRepositoryIntegrationTest implements TestPropertyProvider {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @Inject
    AccountRepository accountRepository;

    @Inject
    TransactionOperations<Connection> tx;

    @Override
    public Map<String, String> getProperties() {
        postgres.start();
        return Map.of(
            "datasources.default.url", postgres.getJdbcUrl(),
            "flyway.datasources.default.enabled", "true"
        );
    }

    @Test
    void shouldSaveAndRetrieveAccount() {
        Account account = new Account(...);
        accountRepository.save(account);

        Optional<Account> retrieved = accountRepository.findById(account.getAccountId());

        assertTrue(retrieved.isPresent());
        assertEquals(account.getAccountNumber(), retrieved.get().getAccountNumber());
    }
}
```

**Mock Framework Beans:**

```java
@MockBean(AutoCommandHandlerRegistry.class)
AutoCommandHandlerRegistry autoCommandHandlerRegistry() {
    return mock(AutoCommandHandlerRegistry.class);
}

@MockBean(ProcessManager.class)
ProcessManager processManager() {
    return mock(ProcessManager.class);
}
```

### 7.4 E2E Tests

**Full Process Execution:**

```java
@MicronautTest
@Testcontainers
class SimplePaymentE2ETest {

    @Inject
    ProcessManager processManager;

    @Inject
    AccountRepository accountRepository;

    @Inject
    PaymentRepository paymentRepository;

    @Test
    void shouldCompletePaymentProcess() {
        // Setup
        Account account = createAccountWithBalance(1000);
        accountRepository.save(account);

        // Start process
        UUID processId = processManager.startProcess(
            "SimplePayment",
            "payment-123",
            Map.of(
                "debitAccountId", account.getAccountId(),
                "amount", 100.0,
                "requiresFx", false
            )
        );

        // Simulate async execution (in real system, handled by framework)
        ProcessInstance instance;
        do {
            Thread.sleep(100);
            instance = processRepository.findById(processId).get();
        } while (instance.status() == ProcessStatus.RUNNING);

        // Verify
        assertEquals(ProcessStatus.SUCCEEDED, instance.status());

        Account updated = accountRepository.findById(account.getAccountId()).get();
        assertEquals(Money.of(900, "USD"), updated.getAvailableBalance());

        List<Payment> payments = paymentRepository.findByAccountId(account.getAccountId());
        assertEquals(1, payments.size());
    }
}
```

### 7.5 Testing Process Graphs

**Verify Structure:**

```java
@Test
void shouldDefineCorrectProcessFlow() {
    ProcessConfiguration config = new SimplePaymentProcessDefinition();
    ProcessGraph graph = config.defineProcess();

    // Verify initial step
    assertEquals("BookLimits", graph.getInitialStep());

    // Verify next steps
    Optional<String> nextStep = graph.getNextStep("BookLimits", Map.of("requiresFx", true));
    assertTrue(nextStep.isPresent());
    assertEquals("BookFx", nextStep.get());

    // Verify compensation
    assertTrue(graph.requiresCompensation("BookLimits"));
    assertEquals("ReverseLimits", graph.getCompensationStep("BookLimits").get());
}
```

---

## 8. Configuration and Deployment

### 8.1 Database Schema Management

**Flyway Migrations:**

```
resources/db/migration/
├── V1__baseline.sql              # Framework tables (command, inbox, outbox)
├── V2__process_manager.sql       # Process tables (process_instance, process_log)
└── V3__myapp_schema.sql         # Application tables
```

**V3__myapp_schema.sql:**

```sql
-- Domain table
CREATE TABLE account (
    account_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    currency_code VARCHAR(3) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    transit_number VARCHAR(10),
    limit_based BOOLEAN DEFAULT false,
    available_balance DECIMAL(19, 4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Child entities
CREATE TABLE transaction (
    transaction_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account(account_id),
    transaction_time TIMESTAMP NOT NULL,
    transaction_type VARCHAR(10) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    description TEXT,
    balance_after DECIMAL(19, 4) NOT NULL
);

-- Indexes
CREATE INDEX idx_transaction_account ON transaction(account_id);
CREATE INDEX idx_transaction_time ON transaction(transaction_time);
```

### 8.2 Application Configuration

**Environment Variables:**

```yaml
# application.yml
micronaut:
  server:
    port: ${APP_PORT:9091}
    netty:
      worker:
        threads: ${NETTY_WORKER_THREADS:50}

datasources:
  default:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:myapp}
    maximum-pool-size: ${HIKARI_MAX_POOL_SIZE:50}
    minimum-idle: ${HIKARI_MIN_IDLE:10}

outbox:
  sweep-interval: ${OUTBOX_SWEEP_INTERVAL:1000}  # milliseconds
  claim-timeout: ${OUTBOX_CLAIM_TIMEOUT:60}      # seconds
  batch-size: ${OUTBOX_BATCH_SIZE:100}

messaging:
  consumers:
    enabled: ${JMS_CONSUMERS_ENABLED:true}
    concurrency: ${JMS_CONCURRENCY:10}
```

**Docker Compose:**

```yaml
services:
  myapp-worker:
    build:
      context: .
      dockerfile: msg-platform-myapp-worker/Dockerfile
    container_name: myapp-worker
    ports:
      - "9093:9093"
    environment:
      APP_PORT: 9093
      POSTGRES_HOST: postgres
      POSTGRES_DB: myapp
      MQ_HOST: ibmmq
      HIKARI_MAX_POOL_SIZE: 50
      JMS_CONCURRENCY: 10
    depends_on:
      postgres:
        condition: service_healthy
      ibmmq:
        condition: service_healthy
```

### 8.3 Queue Configuration

**Required Queues:**

```yaml
mq:
  required-queues: >-
    APP.CMD.CREATEORDER.Q,
    APP.CMD.UPDATEINVENTORY.Q,
    APP.CMD.PROCESSSHIPMENT.Q,
    APP.CMD.REPLY.Q
```

**Queue Naming Convention:**

```
{PREFIX}.{COMMAND_TYPE}.{SUFFIX}

Examples:
- APP.CMD.CREATEACCOUNT.Q
- APP.CMD.BOOKLIMITS.Q
- APP.CMD.REPLY.Q
```

**Queue Setup (IBM MQ):**

```bash
# Connect to MQ container
docker exec -it messaging-ibmmq bash

# Define queues
runmqsc QM1 <<EOF
DEFINE QLOCAL('APP.CMD.CREATEORDER.Q') MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.UPDATEINVENTORY.Q') MAXDEPTH(50000) REPLACE
DEFINE QLOCAL('APP.CMD.REPLY.Q') MAXDEPTH(50000) REPLACE
EOF
```

### 8.4 Monitoring and Health Checks

**Health Endpoint:**

```java
@Controller("/health")
public class HealthController {

    @Inject
    DataSource dataSource;

    @Inject
    OutboxService outboxService;

    @Get
    public HttpResponse<HealthStatus> health() {
        boolean dbHealthy = checkDatabase();
        boolean outboxHealthy = checkOutbox();

        if (dbHealthy && outboxHealthy) {
            return HttpResponse.ok(new HealthStatus("UP"));
        } else {
            return HttpResponse.serverError(new HealthStatus("DOWN"));
        }
    }
}
```

**Metrics to Monitor:**

- Outbox backlog size
- Process success/failure rates
- Command retry counts
- Dead letter queue depth
- Database connection pool usage

---

## 9. Best Practices and Anti-Patterns

### 9.1 Process Design Best Practices

#### ✅ DO: Keep Steps Idempotent

**Good:**

```java
@Transactional
public AccountLimit bookLimits(BookLimitsCommand cmd) {
    // Check if already booked
    Optional<AccountLimit> existing = limitRepository.findByBookingId(cmd.bookingId());
    if (existing.isPresent()) {
        return existing.get(); // Already processed
    }

    // Book limits
    AccountLimit limit = limitRepository.findByAccountId(cmd.accountId())
        .orElseThrow();
    AccountLimit updated = limit.book(cmd.amount(), cmd.bookingId());
    limitRepository.save(updated);

    return updated;
}
```

**Why:** Process steps can be retried on transient failures. Idempotency ensures retry safety.

#### ❌ DON'T: Create Side Effects in Non-Idempotent Way

**Bad:**

```java
@Transactional
public void bookLimits(BookLimitsCommand cmd) {
    AccountLimit limit = limitRepository.findByAccountId(cmd.accountId())
        .orElseThrow();

    // BUG: Retry will double-book!
    AccountLimit updated = limit.book(cmd.amount());
    limitRepository.save(updated);
}
```

#### ✅ DO: Design Proper Compensation

**Good:**

```java
@Transactional
public void reverseLimits(ReverseLimitsCommand cmd) {
    AccountLimit limit = limitRepository.findById(cmd.limitId())
        .orElseThrow();

    // Reverse using booking ID
    AccountLimit reversed = limit.release(cmd.bookingId());
    limitRepository.save(reversed);
}
```

**Why:** Compensation must undo exactly what the forward step did, even if called multiple times.

#### ❌ DON'T: Assume Compensation Won't Be Retried

**Bad:**

```java
@Transactional
public void reverseLimits(ReverseLimitsCommand cmd) {
    // BUG: If retried, will throw exception
    AccountLimit limit = limitRepository.findById(cmd.limitId())
        .orElseThrow();

    limit.releaseAmount(cmd.amount()); // Not idempotent!
    limitRepository.save(limit);
}
```

### 9.2 Command Design Best Practices

#### ✅ DO: Make Commands Self-Contained

**Good:**

```java
public record CreatePaymentCommand(
    UUID debitAccountId,
    Money debitAmount,
    Money creditAmount,
    LocalDate valueDate,
    Beneficiary beneficiary
) implements DomainCommand {
    // All data needed to execute the command
}
```

#### ❌ DON'T: Require External Context

**Bad:**

```java
public record CreatePaymentCommand(
    UUID accountId,
    BigDecimal amount // Missing currency!
) implements DomainCommand {
    // Handler would need to look up account to get currency
}
```

#### ✅ DO: Validate in Constructor

**Good:**

```java
public record CreatePaymentCommand(
    UUID accountId,
    Money amount
) implements DomainCommand {
    public CreatePaymentCommand {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId is required");
        }
        if (amount == null || amount.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
```

### 9.3 Transaction Management

#### ✅ DO: Keep Transactions Short

**Good:**

```java
@Transactional
public Account createAccount(CreateAccountCommand cmd) {
    // 1. Create account
    Account account = new Account(...);
    accountRepository.save(account);

    // Transaction commits here
    return account;
}

// Separate transaction for limits (different step)
@Transactional
public void createLimits(CreateLimitsCommand cmd) {
    // 2. Create limits
}
```

**Why:** Short transactions reduce lock contention and improve throughput.

#### ❌ DON'T: Create Long-Running Transactions

**Bad:**

```java
@Transactional
public void createAccountWithLimits(CreateAccountCommand cmd) {
    // Create account
    Account account = new Account(...);
    accountRepository.save(account);

    // Call external service (SLOW!)
    externalService.validateCustomer(cmd.customerId());

    // Create limits
    limitService.createLimits(...);

    // Transaction held for too long!
}
```

### 9.4 Process State Management

#### ✅ DO: Use Process State for Coordination

**Good:**

```java
@Override
public ProcessGraph defineProcess() {
    return process()
        .startWith(BookLimitsCommand.class) // Sets limitId in state
        .then(CreateTransactionCommand.class) // Uses limitId from state
        .end();
}

// Command uses process state
public Transaction createTransaction(CreateTransactionCommand cmd) {
    UUID limitId = UUID.fromString((String) cmd.limitId());
    // Use limit ID for correlation
}
```

#### ❌ DON'T: Store Large Objects in Process State

**Bad:**

```java
// Command reply includes entire account object
return Map.of(
    "account", account, // Complex object with transactions list
    "allCustomerData", customerData // Even worse!
);
```

**Better:**

```java
// Store only IDs and essential data
return Map.of(
    "accountId", account.getAccountId().toString(),
    "balance", account.getAvailableBalance().amount()
);
```

### 9.5 Error Handling

#### ✅ DO: Distinguish Transient from Permanent Errors

**Good:**

```java
@Override
public boolean isRetryable(String step, String error) {
    // Transient - retry
    if (error.contains("timeout") || error.contains("connection")) {
        return true;
    }

    // Business rules - don't retry
    if (error.contains("InsufficientFunds") ||
        error.contains("InvalidBeneficiary")) {
        return false;
    }

    return false;
}
```

#### ❌ DON'T: Retry Business Rule Violations

**Bad:**

```java
@Override
public boolean isRetryable(String step, String error) {
    return true; // Retries everything, including business rules!
}
```

**Why:** Business rule violations (insufficient funds, invalid data) won't be fixed by retrying. Go straight to
compensation.

### 9.6 Handler Registration

#### ✅ DO: One Handler Per Command Type

**Good:**

```java
@Singleton
public class AccountService {
    public Account createAccount(CreateAccountCommand cmd) { ... }
}

@Singleton
public class PaymentService {
    public Payment createPayment(CreatePaymentCommand cmd) { ... }
}
```

#### ❌ DON'T: Multiple Handlers for Same Command

**Bad:**

```java
@Singleton
public class AccountService {
    public Account createAccount(CreateAccountCommand cmd) { ... }
}

@Singleton
public class AccountProcessDefinition {
    public Map<String, Object> createAccount(CreateAccountCommand cmd) { ... }
}

// ERROR: Ambiguous handler registration for 'CreateAccount'
```

**Solution:** If process needs to handle command, don't also create service method with same signature. Process should
coordinate, service should implement.

### 9.7 Testing Anti-Patterns

#### ❌ DON'T: Test Framework Behavior

**Bad:**

```java
@Test
void shouldAutoDiscoverHandler() {
    // Testing the framework, not your code
    assertTrue(registry.hasHandler("CreateAccount"));
}
```

#### ✅ DO: Test Your Domain Logic

**Good:**

```java
@Test
void shouldPreventOverdraftOnNonLimitBasedAccount() {
    Account account = new Account(..., limitBased = false, balance = 100);

    assertThrows(InsufficientFundsException.class, () ->
        account.createTransaction(TransactionType.DEBIT, Money.of(200, "USD"), "Overdraft")
    );
}
```

---

## 10. Troubleshooting

### 10.1 Common Issues

#### Issue: Handler Not Discovered

**Symptoms:**

```
ERROR c.a.r.p.c.AutoCommandHandlerRegistry - Unknown command type: CreateOrder
```

**Checklist:**

1. ✓ Command class implements `DomainCommand`
2. ✓ Command class name ends with "Command"
3. ✓ Handler method has exactly one parameter of command type
4. ✓ Service class is annotated with `@Singleton`
5. ✓ Check startup logs for discovery errors

**Debug:**

```java
// Enable debug logging in application.yml
logger:
  levels:
    com.acme.reliable.processor.command: DEBUG
```

#### Issue: Duplicate Handler Registration

**Error:**

```
IllegalStateException: Ambiguous handler registration for command type 'CreateAccount':
Found 2 different implementations: [AccountService, CreateAccountProcessDefinition]
```

**Solution:**

- If process handles command, don't create service method with same signature
- Rename service method to not match command pattern
- Or make service method internal (pass individual parameters, not command object)

#### Issue: Process Stuck in RUNNING State

**Symptoms:**

- Process never completes
- No errors in logs

**Checklist:**

1. ✓ Check outbox table for PENDING messages
2. ✓ Verify OutboxRelay is running
3. ✓ Check MQ connection
4. ✓ Verify queue names match configuration
5. ✓ Check for unhandled exceptions in handlers

**Query:**

```sql
-- Find stuck processes
SELECT process_id, process_type, business_key, current_step, created_at
FROM process_instance
WHERE status = 'RUNNING'
  AND updated_at < NOW() - INTERVAL '5 minutes';

-- Check outbox backlog
SELECT category, status, COUNT(*)
FROM outbox
GROUP BY category, status;
```

#### Issue: Compensation Not Executing

**Symptoms:**

- Process marked as FAILED instead of COMPENSATED
- Compensation steps not visible in logs

**Checklist:**

1. ✓ Compensation step defined in process graph
2. ✓ Compensation handler exists and is registered
3. ✓ Step actually completed before failure (compensation only runs for completed steps)
4. ✓ Check `graph.requiresCompensation(step)` returns true

**Debug:**

```java
ProcessConfiguration config = getConfiguration(processType);
ProcessGraph graph = config.defineProcess();

// Check if step has compensation
boolean hasCompensation = graph.requiresCompensation("BookLimits");
Optional<String> compensationStep = graph.getCompensationStep("BookLimits");
```

#### Issue: Idempotency Key Collision

**Error:**

```
IllegalStateException: Duplicate idempotency key: process-123:CreateAccount
```

**Causes:**

1. Process retrying same step (expected behavior)
2. Duplicate process start request
3. Manual command submission with wrong key

**Solution:**

- Ensure business key uniqueness for process
- Check for duplicate API requests
- Use proper idempotency key format: `processId:stepName`

### 10.2 Debugging Tools

#### Database Queries

**Process Status:**

```sql
-- Current process states
SELECT status, COUNT(*)
FROM process_instance
GROUP BY status;

-- Recent failures
SELECT process_id, business_key, current_step, updated_at
FROM process_instance
WHERE status = 'FAILED'
ORDER BY updated_at DESC
LIMIT 10;
```

**Process Event Log:**

```sql
-- Full event history for a process
SELECT event_type, step_name, event_data, created_at
FROM process_log
WHERE process_id = 'uuid-here'
ORDER BY created_at;
```

**Outbox Backlog:**

```sql
-- Messages pending dispatch
SELECT category, destination, COUNT(*), MIN(created_at) as oldest
FROM outbox
WHERE status = 'PENDING'
GROUP BY category, destination;

-- Failed messages
SELECT * FROM outbox
WHERE status = 'FAILED'
ORDER BY retry_count DESC;
```

**Command Status:**

```sql
-- Command processing states
SELECT type, status, COUNT(*)
FROM command
GROUP BY type, status;
```

#### Logging Configuration

```yaml
logger:
  levels:
    # Framework components
    com.acme.reliable.processor: DEBUG
    com.acme.reliable.command: DEBUG

    # Your application
    com.acme.payments: DEBUG

    # SQL queries
    io.micronaut.data.query: DEBUG
```

#### Process Inspection

```java
// Get process instance
Optional<ProcessInstance> instance = processRepository.findById(processId);

// Get event log
List<ProcessEvent> events = processLogRepository.findByProcessId(processId);

// Check current state
System.out.println("Status: " + instance.status());
System.out.println("Current Step: " + instance.currentStep());
System.out.println("Data: " + instance.data());
System.out.println("Retries: " + instance.retries());
```

### 10.3 Performance Tuning

#### Database Connection Pool

```yaml
datasources:
  default:
    maximum-pool-size: 50    # Tune based on load
    minimum-idle: 10
    connection-timeout: 30000
    idle-timeout: 600000
```

**Guidelines:**

- Start with `pool_size = available_cores * 2`
- Monitor connection usage
- Increase if seeing connection wait times

#### Outbox Sweep Interval

```yaml
outbox:
  sweep-interval: 1000  # milliseconds
  batch-size: 100       # messages per batch
```

**Trade-offs:**

- Shorter interval = lower latency, more database queries
- Larger batch = better throughput, higher memory usage

#### JMS Consumer Concurrency

```yaml
messaging:
  consumers:
    concurrency: 10  # concurrent message consumers
```

**Guidelines:**

- `concurrency = pool_size / 2` (leave room for outbox relay)
- Monitor queue depth
- Increase if backlog growing

#### PostgreSQL Tuning

```sql
-- Check connection count
SELECT count(*) FROM pg_stat_activity;

-- Check slow queries
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;

-- Index usage
SELECT schemaname, tablename, indexname, idx_scan
FROM pg_stat_user_indexes
WHERE idx_scan = 0;
```

---

## Appendix A: Framework Tables Reference

### command

Tracks all commands submitted to the system.

| Column          | Type      | Description                            |
|-----------------|-----------|----------------------------------------|
| command_id      | UUID      | Primary key                            |
| type            | VARCHAR   | Command type (e.g., "CreateAccount")   |
| idempotency_key | VARCHAR   | Unique key for deduplication           |
| business_key    | VARCHAR   | Domain identifier                      |
| payload         | TEXT      | JSON command data                      |
| status          | VARCHAR   | PENDING, PROCESSING, COMPLETED, FAILED |
| created_at      | TIMESTAMP | Submission time                        |

### process_instance

Current state of each process.

| Column       | Type      | Description                           |
|--------------|-----------|---------------------------------------|
| process_id   | UUID      | Primary key                           |
| process_type | VARCHAR   | Type of process                       |
| business_key | VARCHAR   | Domain identifier                     |
| status       | VARCHAR   | NEW, RUNNING, SUCCEEDED, FAILED, etc. |
| current_step | VARCHAR   | Current command being executed        |
| data         | TEXT      | JSON process state                    |
| retries      | INT       | Retry attempt count                   |
| created_at   | TIMESTAMP | Process start time                    |
| updated_at   | TIMESTAMP | Last update time                      |

### process_log

Event-sourced audit trail.

| Column     | Type      | Description                         |
|------------|-----------|-------------------------------------|
| log_id     | UUID      | Primary key                         |
| process_id | UUID      | Foreign key to process_instance     |
| event_type | VARCHAR   | ProcessStarted, StepCompleted, etc. |
| step_name  | VARCHAR   | Step that triggered event           |
| event_data | TEXT      | JSON event details                  |
| created_at | TIMESTAMP | Event time                          |

### outbox

Reliable message publishing.

| Column      | Type      | Description                 |
|-------------|-----------|-----------------------------|
| outbox_id   | UUID      | Primary key                 |
| category    | VARCHAR   | "command", "reply", "event" |
| destination | VARCHAR   | Queue/topic name            |
| payload     | TEXT      | Message body                |
| status      | VARCHAR   | PENDING, DISPATCHED, FAILED |
| retry_count | INT       | Number of dispatch attempts |
| created_at  | TIMESTAMP | Creation time               |

### inbox

Idempotent message processing.

| Column       | Type      | Description                    |
|--------------|-----------|--------------------------------|
| message_id   | VARCHAR   | JMS message ID                 |
| handler      | VARCHAR   | Handler that processed message |
| processed_at | TIMESTAMP | Processing time                |

Primary key: (message_id, handler)

---

## Appendix B: Glossary

**Aggregate** - A cluster of domain objects treated as a single unit for data changes.

**Command** - An intention to perform an action, represented as an immutable message.

**Command Handler** - A service method that processes a command.

**Compensation** - Reverse operation executed during saga rollback.

**Domain Command** - Marker interface for commands that can be auto-discovered.

**Idempotency** - Property that multiple identical requests have the same effect as a single request.

**Inbox Pattern** - Pattern for ensuring idempotent message processing.

**Outbox Pattern** - Pattern for reliable message publishing using database as intermediary.

**Process Definition** - Configuration defining a multi-step workflow.

**Process Graph** - Directed acyclic graph representing process steps and transitions.

**Process Instance** - Runtime execution of a process with specific data.

**Process Manager** - Orchestration engine that coordinates multi-step workflows.

**Saga** - Pattern for managing distributed transactions across multiple services.

**Transactional Command Bus** - Command bus that ensures transactional guarantees.

---

## Appendix C: Further Reading

**Internal Documentation:**

- `README.md` - Project overview and setup
- `docs/ARCHITECTURE.md` - Detailed architecture documentation
- `docs/TESTING.md` - Testing guidelines

**External Resources:**

- [Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Domain-Driven Design](https://martinfowler.com/bliki/DomainDrivenDesign.html)
- [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)

**Reference Implementations:**

- `msg-platform-payments-worker` - Complete payment processing example
- `msg-platform-processor` - Process manager implementation
- `msg-platform-core` - Framework core abstractions

---

*This guide is maintained by the platform team. For questions or suggestions, please create an issue in the repository.*
