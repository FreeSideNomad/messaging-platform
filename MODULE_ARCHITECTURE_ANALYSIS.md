# Three-Module Architecture Analysis: msg-platform-processor, msg-platform-payments-worker, msg-platform-worker

## Executive Summary

This analysis covers three interconnected modules in a distributed payment processing system using IBM MQ, Kafka,
PostgreSQL, and Redis. The architecture implements a saga pattern with process orchestration, transactional outbox, and
command-driven event sourcing.

---

## 1. MSG-PLATFORM-PROCESSOR Module

### Overview

Core orchestration and publishing engine for commands, replies, and events. Manages workflow processes, outbox
transactional guarantees, and message distribution.

### Key Responsibilities

#### 1.1 OutboxRelay (Primary Message Publisher)

**File:** `OutboxRelay.java` (92 lines)

- **Role:** Main outbox relay mechanism - publishes messages from DB to external systems
- **Mechanism:**
    - Scheduled polling every 1 second (configurable via `timeout.outbox-sweep-interval`)
    - Atomically claims batches of messages within transactions
    - Publishes outside transactions to avoid holding DB connections during slow I/O
    - Automatic backoff with exponential retry logic (2^n * 1000ms, capped at maxBackoffMillis)

- **Message Flow:**
  ```
  Database (PENDING) → [Atomic Claim] → Publish to MQ/Kafka → Mark PUBLISHED
                                      ↓ [Exception]
                                   Reschedule with backoff
  ```

- **Database Operations:**
    - `store.claim(batchSize, host())` - Atomic claim of PENDING messages
    - `store.markPublished(id)` - Mark as successfully published
    - `store.reschedule(id, backoff, error)` - Reschedule failed messages

- **Category Routing:**
    - `"command"`, `"reply"` → IBM MQ via `mq.send()`
    - `"event"` → Kafka via `kafka.publish()`

#### 1.2 OutboxSweeper (Legacy Alternative - DEPRECATED)

**File:** `OutboxSweeper.java` (66 lines)

- **Role:** Legacy background sweeper (appears to be alternative implementation)
- **Difference from Relay:**
    - Uses `outbox.sweepBatch()` instead of atomic claim
    - Has explicit recovery for STUCK messages (10-second timeout)
    - Uses `markFailed()` with explicit retry timestamps
    - Direct scheduling approach without transaction coordination

- **Status:** Dual implementation suggests migration in progress

#### 1.3 NotifyPublisher (Fast-Path Publisher via Redis)

**File:** `NotifyPublisher.java` (103 lines)

- **Role:** Optimized real-time publisher for immediate message distribution
- **Implementation:**
    - Uses Redis blocking queue (`outbox:notify`) for events
    - Async message consumption with concurrent processing (Semaphore with 32 permits)
    - Republishes to MQ/Kafka in parallel paths
    - Automatic resubscription on completion/error

- **Concurrency Pattern:**
  ```
  Redis Queue (blocking) → Async handlers (32 concurrent)
                        ↓
                  Try publish to MQ/Kafka
                        ↓ [Success]
                   markPublished()
                        ↓ [Failure]
                   markFailed() with backoff
                        ↓
                   Auto-resubscribe
  ```

- **Lifecycle:** Implements AutoCloseable, graceful shutdown with AtomicBoolean flag

#### 1.4 TransactionalCommandBus (Command Entry Point)

**File:** `TransactionalCommandBus.java` (42 lines)

- **Role:** Primary entry point for new commands from API
- **Contract:**
  ```
  @Transactional
  UUID accept(String name, String idem, String bizKey, String payload, Map<String, String> reply)
  ```

- **Responsibilities:**
    1. Idempotency check: `commands.existsByIdempotencyKey(idem)`
    2. Save command as PENDING: `commands.savePending()`
    3. Create outbox entry: `outboxStore.addReturningId()`

- **Transaction Safety:**
    - All database operations in single transaction
    - Fast path publishing DISABLED (comment: "causing transaction leak")
    - Returns command UUID for API response

- **Note:** Reply channel configuration passed via `MessagingConfig`

#### 1.5 ProcessManager (Workflow Orchestration)

**File:** `ProcessManager.java` (76 lines)

- **Role:** Main saga/process orchestration engine
- **Capabilities:**
    1. Auto-discovery of ProcessConfiguration beans at startup
    2. Transaction-per-action execution model
    3. Process state persistence to DB
    4. Step-by-step command emission

- **Architecture Pattern:**
    - Extends `BaseProcessManager` (domain logic)
    - Implements `ApplicationEventListener<ServerStartupEvent>` (auto-registration)
    - Transactional execution wrapper

- **Auto-Discovery Mechanism:**
  ```
  Application Startup Event
       ↓
  BeanContext.getBeansOfType(ProcessConfiguration.class)
       ↓
  register(config) → ProcessManager.register()
       ↓
  Log: "Auto-registered process: {processType}"
  ```

- **Error Handling:**
    - Ambiguous configuration detection (multiple configs for same type)
    - Startup failure if configuration conflicts detected

#### 1.6 ProcessReplyConsumer (Reply Routing)

**File:** `ProcessReplyConsumer.java` (78 lines)

- **Role:** Consumes replies from worker queues and routes to ProcessManager
- **Status:** STUB IMPLEMENTATION
    - Commented out @JMSListener annotation
    - Method signature defined but listener not wired
    - Placeholder for future JMS integration

- **Reply Types Supported:**
    - `CommandCompleted` → Extract payload
    - `CommandFailed` → Extract error message
    - `CommandTimedOut` → Extract timeout error

- **Route to ProcessManager:**
  ```
  processManager.handleReply(correlationId, commandId, reply)
  ```

#### 1.7 ResponseRegistry (In-Memory Response Tracking)

**File:** `ResponseRegistry.java` (40 lines)

- **Role:** Maps command IDs to CompletableFutures for synchronous response waiting
- **Data Structure:** `ConcurrentHashMap<UUID, CompletableFuture<String>>`
- **Lifetime:** Auto-cleanup on timeout (2 seconds default)
- **Methods:**
    - `register(UUID)` → CompletableFuture
    - `complete(UUID, String response)` → Complete future
    - `fail(UUID, String error)` → Complete exceptionally

- **Use Case:** Real-time API response waiting (polling outbox results)

#### 1.8 FastPathPublisher (Transaction-Aware Publishing)

**File:** `FastPathPublisher.java` (36 lines)

- **Role:** Registers outbox publishing to occur after transaction commit
- **Pattern:** Transaction Synchronization callback
- **Current Status:** DISABLED in TransactionalCommandBus (line 38: "// DISABLED: causing transaction leak")
- **Implementation:**
  ```
  transactionOps.findTransactionStatus()
    → status.registerSynchronization()
    → afterCommit() → relay.publishNow(outboxId)
  ```

### Message Flow Architecture

```
API Request
  ↓
TransactionalCommandBus.accept()
  ├─ Check idempotency key
  ├─ Save command (PENDING status)
  ├─ Create outbox entry
  └─ Return command ID (202 Accepted)
  
Database
  ├─ command table (PENDING)
  └─ outbox table (PENDING/SENDING/PUBLISHED states)
  
Publishing Path (2 options):
  
  Path 1: OutboxRelay (Primary)
  ├─ @Scheduled every 1 second
  ├─ Claim batch atomically
  ├─ Send to MQ/Kafka
  └─ Mark published
  
  Path 2: NotifyPublisher (Fast-path via Redis)
  ├─ Redis queue notification
  ├─ Async concurrent handlers (32)
  ├─ Send to MQ/Kafka
  └─ Mark published
  
Worker receives on MQ
  ├─ Process command
  ├─ Emit reply to reply queue
  └─ Send events to Kafka (optional)
  
ProcessReplyConsumer (STUB)
  ├─ Receive from APP.CMD.REPLY.Q
  ├─ Route to ProcessManager
  └─ Advance process state
```

### Database Operations Summary

| Component               | Operation       | Method                              | Transactional |
|-------------------------|-----------------|-------------------------------------|---------------|
| OutboxRelay             | Claim           | `store.claim()`                     | YES           |
| OutboxRelay             | Mark published  | `store.markPublished()`             | YES           |
| OutboxRelay             | Reschedule      | `store.reschedule()`                | YES           |
| OutboxSweeper           | Sweep           | `outbox.sweepBatch()`               | NO            |
| OutboxSweeper           | Recover stuck   | `outbox.recoverStuck()`             | NO            |
| TransactionalCommandBus | Check duplicate | `commands.existsByIdempotencyKey()` | YES           |
| TransactionalCommandBus | Save command    | `commands.savePending()`            | YES           |
| TransactionalCommandBus | Create outbox   | `outboxStore.addReturningId()`      | YES           |
| NotifyPublisher         | Claim           | `outbox.claimIfNew()`               | NO            |
| NotifyPublisher         | Mark published  | `outbox.markPublished()`            | NO            |
| NotifyPublisher         | Mark failed     | `outbox.markFailed()`               | NO            |

### Current Test Coverage

**Test Files:** 4 files, 1120 total lines

1. **ProcessManagerAutoDiscoveryTest** - Auto-discovery mechanism
2. **ProcessManagerTest** - Unit tests
3. **ProcessManagerIntegrationTest** - Integration tests with Testcontainers
4. **SimpleRedisQueueTest** - Redis integration

**Coverage Gaps:**

- [ ] OutboxRelay transaction behavior under failure
- [ ] OutboxSweeper recovery logic
- [ ] NotifyPublisher concurrency limits and backpressure
- [ ] FastPathPublisher transaction synchronization
- [ ] ProcessReplyConsumer (currently STUB - no tests)
- [ ] ResponseRegistry timeout behavior
- [ ] Message corruption/malformed JSON handling
- [ ] Cross-module integration (Processor + Worker + Payments)

### Configuration Dependencies

```yaml
timeout:
  outbox-sweep-interval: "1s"
  max-backoff-millis: 300000
  outbox-batch-size: 500
```

### External Dependencies

- **IBM MQ:** Command/Reply queues via `CommandQueue` interface
- **Kafka:** Event publishing via `EventPublisher` interface
- **Redis:** NotifyPublisher optional dependency
- **PostgreSQL:** Outbox, command, process repositories
- **Micronaut:** DI, transactions, scheduling

---

## 2. MSG-PLATFORM-PAYMENTS-WORKER Module

### Overview

Domain-specific payment processing worker implementing saga orchestration, limit management, and FX operations for
banking workflow.

### Key Responsibilities

#### 2.1 PaymentCommandConsumer (JMS Entry Point)

**File:** `PaymentCommandConsumer.java` (92 lines)

- **Role:** JMS listener for payment-specific commands
- **Configuration:**
    - Requires: `beans = IbmMqFactoryProvider`
    - Enabled by: `jms.consumers.enabled=true`
    - Disabled in test environment

- **Listeners (11 queue handlers):**
  ```
  APP.CMD.BOOKFX.Q              → onBookFxCommand()
  APP.CMD.BOOKLIMITS.Q          → onBookLimitsCommand()
  APP.CMD.COMPLETEACCOUNTCREATION.Q → onCompleteAccountCreationCommand()
  APP.CMD.CREATEACCOUNT.Q       → onCreateAccountCommand()
  APP.CMD.CREATELIMITS.Q        → onCreateLimitsCommand()
  APP.CMD.CREATEPAYMENT.Q       → onCreatePaymentCommand()
  APP.CMD.CREATETRANSACTION.Q   → onCreateTransactionCommand()
  APP.CMD.INITIATESIMPLEPAYMENT.Q → onInitiateSimplePaymentCommand()
  APP.CMD.REVERSELIMITS.Q       → onReverseLimitsCommand()
  APP.CMD.REVERSETRANSACTION.Q  → onReverseTransactionCommand()
  APP.CMD.UNWINDFX.Q            → onUnwindFxCommand()
  ```

- **Processing:**
  ```
  @MessageBody String → processCommand(commandType, body, jmsMessage)
  ```
    - Extends `BaseCommandConsumer` for common logic
    - Reply queue: `APP.CMD.REPLY.Q`

#### 2.2 Domain Services Layer

##### 2.2.1 AccountService

**File:** `AccountService.java` (174 lines)

- **Responsibilities:**
    1. Account creation with properties
    2. Transaction management (CREATE, DEBIT, CREDIT)
    3. Transaction reversal for compensation

- **Command Handlers (Auto-discovered):**
    - `handleCreateAccount(CreateAccountCommand)` → Account created, returns accountId + accountNumber
    - `handleCompleteAccountCreation(CompleteAccountCreationCommand)` → No-op terminal step
    - `handleReverseTransaction(ReverseTransactionCommand)` → Creates opposite transaction

- **Data Model:**
  ```
  Account {
    accountId: UUID
    customerId: UUID
    accountNumber: String
    currencyCode: String
    accountType: CHECKING | SAVINGS
    transitNumber: String
    limitBased: boolean
    balance: Money
    transactions: List<Transaction>
  }
  
  Transaction {
    transactionId: UUID
    accountId: UUID
    type: DEBIT | CREDIT
    amount: Money
    description: String
  }
  ```

- **Database Operations:**
    - `accountRepository.save(account)` - Persist account with transactions
    - `accountRepository.findById(accountId)` - Read account aggregate
    - All operations within @Transactional boundaries

- **Compensation Pattern:**
    - Reverse transaction creates opposite type transaction
    - Maintains audit trail with reference to original

##### 2.2.2 PaymentService

**File:** `PaymentService.java` (61 lines)

- **Responsibilities:**
    1. Payment creation (debit/credit pair)
    2. Payment state updates
    3. Payment retrieval

- **Data Model:**
  ```
  Payment {
    paymentId: UUID
    debitAccountId: UUID
    debitAmount: Money
    creditAmount: Money
    valueDate: LocalDate
    beneficiary: Beneficiary
    status: PaymentStatus
  }
  ```

- **Methods:**
    - `createPayment(CreatePaymentCommand)` → Payment aggregate
    - `updatePayment(Payment)` → Persisted payment
    - `getPaymentById(UUID)` → Payment aggregate

##### 2.2.3 FxService

**File:** `FxService.java` (79 lines)

- **Responsibilities:**
    1. FX contract booking (debit/credit currencies)
    2. FX contract unwinding for compensation
    3. Rate calculation

- **Data Model:**
  ```
  FxContract {
    fxContractId: UUID
    customerId: UUID
    debitAccountId: UUID
    debitAmount: Money
    creditAmount: Money
    rate: BigDecimal
    valueDate: LocalDate
    status: FxStatus (BOOKED, UNWOUND)
  }
  ```

- **Command Handlers:**
    - `bookFx(BookFxCommand)` → Calculates rate (creditAmount / debitAmount)
    - `handleUnwindFx(UnwindFxCommand)` → Marks contract unwound

- **Rate Calculation:**
  ```java
  rate = creditAmount.divide(debitAmount, 6, HALF_UP)
  ```

##### 2.2.4 LimitService

**File:** `LimitService.java` (123 lines)

- **Responsibilities:**
    1. Create time-bucketed limits (HOUR, DAY, WEEK, MONTH)
    2. Book amount against active limits
    3. Reverse (unbook) amounts for compensation

- **Data Model:**
  ```
  AccountLimit {
    limitId: UUID
    accountId: UUID
    periodType: HOUR | DAY | WEEK | MONTH
    startTime: Instant
    endTime: Instant
    limitAmount: Money
    utilized: Money
    
    available() → limitAmount - utilized
    isExpired() → now > endTime
  }
  ```

- **Command Handlers:**
    - `handleCreateLimits(CreateLimitsCommand)` → Creates limit bucket per PeriodType
    - `handleReverseLimits(ReverseLimitsCommand)` → Reduces utilized amount

- **Key Logic:**
    - Time bucket alignment to period boundaries
    - Multi-period support (hour + day + week + month)
    - Booking against all active limits atomically
    - Automatic expiration detection

- **Period Type Alignment:**
  ```
  MINUTE: Aligned to minute start
  HOUR:   Aligned to hour boundary (XX:00:00)
  DAY:    Aligned to day start (00:00:00)
  WEEK:   Aligned to Monday 00:00:00
  MONTH:  Aligned to month 1st 00:00:00
  ```

#### 2.3 Process Definitions (Orchestration)

##### 2.3.1 CreateAccountProcessDefinition

**File:** `CreateAccountProcessDefinition.java` (99 lines)

- **Process Type:** `InitiateCreateAccountProcess`
- **Flow:**
  ```
  InitiateCreateAccountProcess
    ↓
  CreateAccountCommand
    ↓
  if (limitBased && limits exist):
    CreateLimitsCommand
    ↓
  CompleteAccountCreationCommand
    ↓
  END
  ```

- **Idempotency:** All steps retryable on transient errors
    - Retry on: timeout, connection, temporary errors
    - Max retries: 3 per step

- **Compensation:** None (creation is idempotent)

- **State Initialization:**
  ```
  Map from InitiateCreateAccountProcess:
  {
    customerId, currencyCode, accountType, 
    transitNumber, limitBased, limits
  }
  ```

##### 2.3.2 SimplePaymentProcessDefinition

**File:** `SimplePaymentProcessDefinition.java` (86 lines)

- **Process Type:** `InitiateSimplePaymentCommand`
- **Flow with Compensation:**
  ```
  BookLimitsCommand ←─ ReverseLimitsCommand (compensation)
    ↓
  if (requiresFx):
    BookFxCommand ←─ UnwindFxCommand (compensation)
    ↓
  CreateTransactionCommand ←─ ReverseTransactionCommand (compensation)
    ↓
  CreatePaymentCommand (terminal - no compensation)
    ↓
  END
  ```

- **Conditional Logic:**
    - FX booking only if `requiresFx = true` in command data
    - Falls back to CreateTransaction if FX not required

- **Compensation Chain:**
    - Automatic unwind on any step failure
    - Compensation executed in reverse order
    - All compensation steps are idempotent

- **Error Recovery:**
    - Retry on transient: timeout, connection, temporary
    - Max retries: 3

### Data Model - Repositories

**Account Repositories:**

- `AccountRepository.findById(UUID)` → Optional<Account>
- `AccountRepository.save(Account)` → void

**Limit Repositories:**

- `AccountLimitRepository.findActiveByAccountId(UUID)` → List<AccountLimit>
- `AccountLimitRepository.findByAccountIdAndPeriodType(UUID, PeriodType)` → List<AccountLimit>
- `AccountLimitRepository.save(AccountLimit)` → void

**Payment Repositories:**

- `PaymentRepository.findById(UUID)` → Optional<Payment>
- `PaymentRepository.save(Payment)` → void

**FX Repositories:**

- `FxContractRepository.findById(UUID)` → Optional<FxContract>
- `FxContractRepository.save(FxContract)` → void

### Command Flow Example: Simple Payment

```
InitiateSimplePaymentCommand
  ├─ customerId: UUID
  ├─ debitAccountId: UUID
  ├─ debitAmount: Money
  ├─ creditAccountId: UUID
  ├─ creditAmount: Money
  ├─ requiresFx: boolean
  └─ beneficiary: Beneficiary
  
ProcessManager routes to:
  
Step 1: BookLimitsCommand
  └─ LimitService.bookLimits()
     └─ accountRepository.findById() → Account
     └─ limitRepository.findActiveByAccountId() → List<Limit>
     └─ limit.book(amount) for each active limit
     └─ limitRepository.save() ← compensation hook
  
Step 2 (conditional): BookFxCommand
  └─ FxService.bookFx()
     └─ rate = creditAmount / debitAmount
     └─ FxContract created
     └─ fxContractRepository.save() ← compensation hook
  
Step 3: CreateTransactionCommand
  └─ AccountService.createTransaction()
     └─ accountRepository.findById() → Account
     └─ account.createTransaction()
     └─ accountRepository.save() ← compensation hook
  
Step 4: CreatePaymentCommand
  └─ PaymentService.createPayment()
     └─ Payment aggregate created
     └─ paymentRepository.save() ← terminal
```

### Current Test Coverage

**Test Files:** ~90 files, 8911 total lines

**Coverage Areas:**

- Unit tests for domain models: Account, Payment, FxContract, Limit, Money, Beneficiary, TransactionType, PeriodType
- Unit tests for services: AccountService, PaymentService, FxService, LimitService
- Integration tests for repositories: JdbcAccountRepository, JdbcPaymentRepository, etc.
- Process definition tests: SimplePaymentProcessDefinition, CreateAccountProcessDefinition
- E2E framework tests: E2ETestRunner, E2EFrameworkTest
- E2E scenario tests: CreateAccountWithLimitsE2ETest, PaymentFlowE2ETest

**Test Framework:**

- Testcontainers for PostgreSQL
- @MicronautTest with transactional=false
- Data generators for accounts, payments, transactions
- Output adapters: Vegeta, MqJson
- Scenario builders with configurable metrics

**Coverage Gaps:**

- [ ] Integration: Processor ← PaymentCommandConsumer → Services → Database
- [ ] Integration: ProcessManager → Service handlers → Compensation on failure
- [ ] Integration: Multi-step process with real orchestration
- [ ] End-to-end: Command submission → Processing → Reply → ProcessManager update
- [ ] Failure scenarios: Timeout, network failures, partial failures
- [ ] Limit expiration and rollover
- [ ] FX rate calculations under edge cases
- [ ] Concurrent limit bookings

### Configuration & Dependencies

**Dependencies:**

- msg-platform-core (domain)
- msg-platform-persistence-jdbc (repositories)
- msg-platform-processor (orchestration)
- msg-platform-messaging-ibmmq (JMS consumer)
- Lombok, Micronaut (runtime)

**Test Dependencies:**

- Testcontainers, PostgreSQL
- JUnit 5, Mockito, AssertJ
- JavaFaker (test data)

---

## 3. MSG-PLATFORM-WORKER Module

### Overview

Generic worker for command processing with simple UserService example. Minimal domain logic focused on framework
integration testing.

### Key Responsibilities

#### 3.1 CommandConsumers (Generic MQ Listener)

**File:** `CommandConsumers.java` (39 lines)

- **Role:** JMS listeners for generic worker commands
- **Configuration:**
    - Requires: `beans = IbmMqFactoryProvider`
    - Enabled by: `jms.consumers.enabled=true`
    - Disabled in test environment

- **Listeners (2 handlers):**
  ```
  APP.CMD.CREATEUSER.Q → onCreateUser()
  APP.CMD.REPLY.Q      → onReply()
  ```

- **Processing:**
  ```
  onCreateUser(@MessageBody String body, @Message jakarta.jms.Message)
    → Mappers.toEnvelope(body, m)
    → CommandExecutor.process(envelope)
  
  onReply(@MessageBody String body, @Message jakarta.jms.Message)
    → Extract commandId from JMS property
    → ResponseRegistry.complete(commandId, body)
  ```

- **Architecture:**
  ```
  JMS Message (IBM MQ)
    ↓
  CommandConsumers listener
    ↓
  CommandExecutor (from processor module)
    ↓
  CommandHandlerRegistry (auto-discovery)
    ↓
  UserService.handleCreateUser() (auto-discovered)
    ↓
  CommandReply published to reply queue
  ```

#### 3.2 UserService (Example Domain Service)

**File:** `UserService.java` (39 lines)

- **Role:** Simple user creation handler (example/reference implementation)
- **Command Handler (auto-discovered):**
  ```java
  @Singleton
  public Map<String, Object> handleCreateUser(CreateUserCommand cmd)
  ```

- **Implementation:**
    - Check for test failure scenarios:
        - username contains "failPermanent" → throw PermanentException
        - username contains "failTransient" → throw TransientException
    - Simulate user creation: userId = "u-123"
    - Return result map: {userId, username}

- **Result Format:**
  ```
  {
    "userId": "u-123",
    "username": "{from command}"
  }
  ```

- **Use Case:** Testing framework integration, error handling

### E2E Testing Framework

#### 3.3 WorkerE2ETestBase

**File:** `WorkerE2ETestBase.java` (155 lines)

- **Purpose:** Base class for end-to-end testing of full stack
- **Scope:** API → Worker → Database

- **Infrastructure Setup:**
    1. HTTP client initialization (10s timeout)
    2. Service availability polling (API health check)
    3. Database connection establishment
    4. Test data cleanup

- **Utilities:**
    - `waitForService(url, timeout)` - Poll until 200 OK
    - `submitCommand(commandName, idempotencyKey, payload)` - POST to API
    - `cleanupTestData()` - DELETE test records
    - `waitForCommandStatus(commandId, expectedStatus, timeout)` - Poll command status
    - `countCommands(statusFilter)` - Query command table

- **Database Schema Assumptions:**
  ```
  platform.command (id, name, status, idempotency_key, payload)
  platform.outbox (id, key)
  platform.inbox (message_id)
  platform.command_dlq (command_name)
  ```

- **API Assumptions:**
    - POST /commands/{commandName}
    - Header: Idempotency-Key
    - Response: X-Command-Id header
    - Status: 202 Accepted

#### 3.4 SingleCommandE2ETest

**File:** `SingleCommandE2ETest.java` (102 lines)

- **Test Scope:** Single command submission and persistence
- **Prerequisites:** Docker Compose stack running
  ```
  docker-compose up -d
  ```

- **Test Flow:**
  ```
  1. Generate idempotency key
  2. POST CreateUser command to API
  3. Verify 202 Accepted response
  4. Extract command ID from X-Command-Id header
  5. Query database:
     - Verify command exists
     - Verify name = "CreateUser"
     - Verify status = "PENDING"
     - Verify idempotency key matches
     - Verify payload contains username
  ```

- **Test Example:**
  ```
  Idempotency-Key: "e2e-single-<random-uuid>"
  Payload: {"username":"testuser"}
  
  Expected:
  - Status 202
  - Command in DB with name="CreateUser"
  - Status="PENDING"
  - Payload contains "testuser"
  ```

### Command Flow

```
API (msg-platform-api)
  ├─ POST /commands/CreateUser
  ├─ TransactionalCommandBus.accept()
  └─ Return 202 with command ID
  
Database
  ├─ command table: (PENDING status)
  └─ outbox table: (PENDING status)
  
Processor (OutboxRelay/NotifyPublisher)
  ├─ Claim outbox message
  ├─ Send to MQ
  └─ Mark published
  
IBM MQ
  ├─ APP.CMD.CREATEUSER.Q (command message)
  
Worker (msg-platform-worker)
  ├─ CommandConsumers.onCreateUser()
  ├─ CommandExecutor.process()
  ├─ UserService.handleCreateUser()
  └─ Return result
  
Reply Publishing
  ├─ Create reply message
  ├─ Publish to APP.CMD.REPLY.Q
  └─ Publish to Kafka (if configured)
  
Reply Reception
  ├─ CommandConsumers.onReply()
  ├─ ResponseRegistry.complete()
  └─ Available for API polling
```

### Current Test Coverage

**Test Files:** 2 files, 257 total lines

1. **SingleCommandE2ETest** - Single command flow
2. **WorkerE2ETestBase** - Base infrastructure

**Coverage Gaps:**

- [ ] Multiple concurrent commands
- [ ] Idempotency validation (duplicate keys)
- [ ] Error scenarios (invalid payload, missing fields)
- [ ] Response polling (API waiting for result)
- [ ] Timeout handling
- [ ] Failure handler testing (failPermanent, failTransient)
- [ ] Message corruption
- [ ] Integration with other workers (ProcessManager)
- [ ] Reply reception and status update
- [ ] End-to-end saga orchestration

### Configuration & Dependencies

**Dependencies:**

- msg-platform-core (domain)
- msg-platform-persistence-jdbc (repositories)
- msg-platform-processor (orchestration, ResponseRegistry)
- msg-platform-messaging-ibmmq (JMS consumer)

**E2E Requirements:**

- Docker Compose: PostgreSQL, IBM MQ, Kafka, Redis
- API service running on localhost:8080
- Database on localhost:5432

---

## Cross-Module Integration Points

### Message Flow Integration

```
┌─────────────────────────────────────────────────────────┐
│                    API Request                          │
│            (msg-platform-api module)                    │
└──────────────────┬──────────────────────────────────────┘
                   │
                   ↓
┌─────────────────────────────────────────────────────────┐
│           TransactionalCommandBus                       │
│         (msg-platform-processor)                        │
│  ├─ Check idempotency                                   │
│  ├─ Save command → PENDING                              │
│  └─ Create outbox entry → PENDING                       │
└──────────────────┬──────────────────────────────────────┘
                   │
       ┌───────────┴───────────┐
       ↓                       ↓
┌──────────────┐         ┌──────────────┐
│ OutboxRelay  │         │ NotifyPub    │
│ @Scheduled   │         │ (Redis based)│
│ 1s polling   │         │ Async/32 ok  │
└──────┬───────┘         └──────┬───────┘
       │                        │
       └────────────┬───────────┘
                    ↓
            ┌───────────────┐
            │   IBM MQ      │
            │ Command Queue │
            └───────┬───────┘
                    │
    ┌───────────────┼───────────────┐
    ↓               ↓               ↓
┌─────────┐   ┌──────────┐   ┌──────────┐
│ Worker  │   │ Payments │   │   ...    │
│ Service │   │  Worker  │   │ Modules  │
│  Module │   │  Module  │   │          │
└────┬────┘   └────┬─────┘   └────┬─────┘
     │             │              │
     │ ┌───────────┼──────────────┐│
     │ ↓           ↓              ↓│
     │ ┌─────────────────────────┐│
     │ │  Command Execution      ││
     │ │ (Service handlers)      ││
     │ │                         ││
     │ │ UserService → Domain   ││
     │ │ AccountService → Limit ││
     │ │ LimitService → FxSvc   ││
     │ └────────┬────────────────┘│
     │          ↓                 │
     │ ┌─────────────────────────┐│
     │ │   ProcessManager        ││
     │ │  (Orchestration)        ││
     │ │  Step execution         ││
     │ │  State persistence      ││
     │ └────────┬────────────────┘│
     │          ↓                 │
     │ ┌─────────────────────────┐│
     │ │  PostgreSQL Database    ││
     │ │  - Accounts             ││
     │ │  - Payments             ││
     │ │  - Processes            ││
     │ │  - Outbox (replies)     ││
     │ └────────┬────────────────┘│
     │          ↓                 │
     │ ┌─────────────────────────┐│
     │ │  Reply Publishing       ││
     │ │  (Outbox Relay)         ││
     │ └────────┬────────────────┘│
     │          ↓                 │
     │      App.CMD.REPLY.Q       │
     │          │                 │
     └──────────┼─────────────────┘
                │
     ┌──────────┘
     ↓
┌─────────────────────────┐
│ ProcessReplyConsumer    │
│ (STUB - not impl)       │
│ Route to ProcessManager │
└─────────┬───────────────┘
          ↓
   ProcessManager.handleReply()
   ├─ Update process state
   ├─ Emit next command
   └─ Persist to DB
```

### Data Dependencies

**Command → Service → Domain Model → Repository → Database**

Example: Payment Flow

```
InitiateSimplePaymentCommand
  ├─ customerId, debitAccountId, creditAccountId
  ├─ debitAmount, creditAmount, beneficiary
  └─ requiresFx
  
→ ProcessManager routes steps:
  
Step 1: BookLimitsCommand
  → LimitService.bookLimits()
    → accountRepository.findById(debitAccountId)
    → Account aggregate
    → limitRepository.findActiveByAccountId()
    → AccountLimit aggregates
    → limit.book(debitAmount) [domain logic]
    → limitRepository.save()
    → DB: account_limit.utilized += debitAmount
    
Step 2: BookFxCommand (if requiresFx)
  → FxService.bookFx()
    → FxContract created
    → fxContractRepository.save()
    → DB: fx_contract inserted
    
Step 3: CreateTransactionCommand
  → AccountService.createTransaction()
    → accountRepository.findById(debitAccountId)
    → Account.createTransaction() [domain logic]
    → accountRepository.save()
    → DB: transaction inserted
    
Step 4: CreatePaymentCommand
  → PaymentService.createPayment()
    → Payment created
    → paymentRepository.save()
    → DB: payment inserted
    
All within transaction, compensation on failure
```

### Error Handling & Compensation

```
Process Execution Flow:

Step 1 (BookLimits)
  ├─ Success → next step
  ├─ Transient error → retry (max 3)
  └─ Permanent error → 
      Compensation: ReverseLimitsCommand
      ├─ Undo limit booking
      └─ Terminate process (FAILED)

Step 2 (BookFx)
  ├─ Success → next step
  ├─ Transient error → retry (max 3)
  └─ Permanent error →
      Compensation:
      1. UnwindFxCommand (undo FX)
      2. ReverseLimitsCommand (undo limits)
      3. Terminate (FAILED)

Step 3 (CreateTransaction)
  ├─ Success → next step
  ├─ Transient error → retry (max 3)
  └─ Permanent error →
      Compensation:
      1. ReverseTransactionCommand
      2. UnwindFxCommand (if applicable)
      3. ReverseLimitsCommand
      4. Terminate (FAILED)

Step 4 (CreatePayment) - Terminal
  ├─ Success → COMPLETED
  └─ Error → FAILED (no compensation at terminal)
```

---

## Testing Strategy for Integration Tests

### Test Pyramid

```
                    ▲
                    │  E2E Tests (5 tests)
                    │  - Full stack: API → Processor → Worker → DB
                    │  - Command submission → Execution → Reply
                    │  - Cross-module workflows
                    │  
                   /│\
                  / │ \
                 /  │  \ Integration Tests (20 tests)
                /   │   \ - Service × Service
                /    │    \ - ProcessManager × Repositories
                /     │     \ - Outbox × Message Queue
               /      │      \ - Processor × Worker
              /───────┼──────────\
             /        │         \  Unit Tests (80 tests)
            /         │          \ - Domain models
           /          │           \ - Service logic
          /           │            \ - Command handlers
         /────────────┼─────────────\
```

### Gap Analysis: Test Coverage By Module

#### msg-platform-processor

**Current (1120 lines):**

- ✓ ProcessManager auto-discovery
- ✓ Process step execution
- ✓ Process state persistence
- Missing:
    - [ ] OutboxRelay failure scenarios
    - [ ] OutboxSweeper recovery
    - [ ] NotifyPublisher backpressure (>32 concurrent)
    - [ ] ProcessReplyConsumer (STUB - requires JMS setup)
    - [ ] Cross-module: Processor ← Worker reply → ProcessManager
    - [ ] Message corruption handling
    - [ ] Database constraint violations

#### msg-platform-payments-worker

**Current (8911 lines):**

- ✓ Domain model tests (many)
- ✓ Service unit tests
- ✓ Repository integration tests
- ✓ E2E framework (extensive)
- Missing:
    - [ ] Multi-step process execution (real orchestration)
    - [ ] Limit expiration and rollover
    - [ ] FX rate edge cases
    - [ ] Concurrent payments on same account
    - [ ] Compensation flow (happy path only)
    - [ ] Timeout/retry behavior
    - [ ] ProcessManager integration

#### msg-platform-worker

**Current (257 lines):**

- ✓ E2E test base infrastructure
- ✓ Single command flow
- Missing:
    - [ ] Multiple concurrent commands
    - [ ] Idempotency (duplicate key rejection)
    - [ ] Response polling
    - [ ] Error scenarios (failPermanent/failTransient)
    - [ ] Message deserialization errors
    - [ ] Reply consumption
    - [ ] Full saga orchestration
    - [ ] Integration: CommandConsumers → ProcessManager

### Recommended E2E Test Coverage

#### Test 1: Single Command E2E (Worker)

**Focus:** Basic flow

- API submission → DB persistence → Worker processing → Reply
- Status: PENDING → COMPLETED

#### Test 2: Payments Account Creation with Limits (Payments-Worker)

**Focus:** Multi-step orchestration

- InitiateCreateAccountProcess → CreateAccount → CreateLimits → Complete
- Verify account created, limits created, process completed
- Database state validation

#### Test 3: Simple Payment with FX (Payments-Worker)

**Focus:** Conditional branching + compensation

- InitiateSimplePayment → BookLimits → BookFx → CreateTransaction → CreatePayment
- Verify all aggregates created
- Verify outbox entries for reply

#### Test 4: Payment Compensation Flow (Payments-Worker)

**Focus:** Error handling

- InitiateSimplePayment → BookLimits → BookFx → FAIL on CreateTransaction
- Compensation: UnwindFx → ReverseLimits
- Verify state rolled back

#### Test 5: Processor to Worker Integration (Cross-module)

**Focus:** Full saga coordination

- Payments command via API → Processor routes → Worker executes
- Multi-worker orchestration
- Reply routing back to ProcessManager

---

## Configuration & Infrastructure

### Docker Stack

```
PostgreSQL:16      port 5432
IBM MQ:9.4.4       port 1414, 9443
Kafka:7.6.0        port 9092
Redis:7            port 6379
```

### Timeouts & Tuning

```yaml
timeout:
  outbox-sweep-interval: 1s      # OutboxRelay polling
  max-backoff-millis: 300000     # 5 min max retry backoff
  outbox-batch-size: 500         # Messages per sweep
  
notification:
  redis-queue: outbox:notify
  concurrency: 32 permits
  
process:
  max-retries: 3
  retry-conditions: timeout, connection, temporary
```

### Dependencies Summary

**Internal modules:**

- msg-platform-core: Domain, ports/SPIs, utilities
- msg-platform-persistence-jdbc: Repository implementations
- msg-platform-processor: Orchestration, command bus, outbox relay
- msg-platform-messaging-ibmmq: IBM MQ provider
- msg-platform-events-kafka: Kafka event publishing
- msg-platform-api: HTTP API endpoints

**External libraries:**

- Micronaut 4.x (DI, transactions, scheduling, JMS)
- PostgreSQL JDBC driver
- IBM MQ client library
- Kafka client
- Redisson (Redis)
- JUnit 5, Mockito, AssertJ
- Testcontainers

---

## Summary: Key Findings

### Architecture Strengths

1. Clear separation: Processor (infrastructure) vs. Worker (domain)
2. Saga pattern with automatic compensation
3. Transactional outbox ensures exactly-once delivery
4. Dual publishing paths (polling + fast Redis-based)
5. Process orchestration with auto-discovery
6. Comprehensive E2E test framework (payments module)

### Critical Gaps

1. **ProcessReplyConsumer STUB** - Not implemented, JMS listener commented out
2. **FastPathPublisher disabled** - Disabled due to "transaction leak"
3. **Limited cross-module E2E** - Processor ← Worker → ProcessManager
4. **Worker module minimal** - Only 257 lines tests, needs compensation testing
5. **Error scenarios sparse** - Timeout, network failures, partial rollback

### Testing Priorities

1. **Implement ProcessReplyConsumer** with tests
2. **Fix FastPathPublisher transaction leak**
3. **Add payment compensation E2E tests**
4. **Multi-command idempotency tests**
5. **Processor-Worker-ProcessManager integration tests**

