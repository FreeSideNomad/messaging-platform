# Integration Test Plan: H2 + ActiveMQ

## Overview

This plan designs integration tests for three modules using **H2** (in-memory database) and **Embedded ActiveMQ** (in-process JMS), without Docker/Testcontainers. Tests validate message flow, database persistence, and command processing with real JMS semantics and JDBC operations.

**Constraints & Approach**:
- ✅ H2 for JDBC persistence (already in test profile)
- ✅ Embedded ActiveMQ for JMS (already configured)
- ❌ No Docker/Testcontainers (CI/CD constraint)
- ❌ Skip FastPath (abandoned, no coverage needed)
- ❌ Skip Redis (not critical)
- ✅ Focus on message flow, retry logic, database state changes

---

## Module Analysis

### 1. msg-platform-processor (18 main Java files)

**Key Responsibilities**:
- `OutboxRelay`: Batch processes outbox records, publishes to MQ/Kafka, marks as published (scheduled every 1s)
- `OutboxSweeper`: Batch sweeps outbox records, recovers stuck messages, implements exponential backoff retry (scheduled every 1s)
- `ProcessManager`: Manages command processing workflows
- `ResponseRegistry`: Collects responses from async operations
- `TransactionalCommandBus`: Executes commands transactionally

**Database Operations**:
- Reads from outbox table (PENDING, CLAIMED, SENDING, PUBLISHED states)
- Writes status updates (claim, published, failed with backoff reschedule)
- Transactional operations with connection pooling

**Message Flow**:
```
INSERT outbox (PENDING)
    ↓
OutboxRelay/Sweeper claims (CLAIMED)
    ↓
Publishes to MQ/Kafka (SENDING)
    ↓
Marks PUBLISHED or FAILED with reschedule
```

**Current Test Gap**:
- No integration tests for OutboxRelay/Sweeper
- No tests for retry/backoff logic
- No end-to-end outbox→MQ flow validation

**Coverage Targets**:
- OutboxRelay: ~85% (scheduling is hard to test)
- OutboxSweeper: ~85% (same reason)
- TransactionalCommandBus: ~90%
- ResponseRegistry: ~90%

---

### 2. msg-platform-payments-worker (43 main Java files)

**Key Responsibilities**:
- `PaymentCommandConsumer`: JMS listener receiving commands from 11 different queues
- `AccountService`: Account creation, lookup, updates
- `PaymentService`: Payment creation and processing
- `FxService`: Foreign exchange contract management
- `LimitService`: Account limit creation and management
- `CreateAccountProcessDefinition`: Orchestrates account creation workflow
- `SimplePaymentProcessDefinition`: Orchestrates payment workflow

**Database Operations**:
- Reads/writes Account, AccountLimit, Payment, Transaction, FxContract entities
- Transactional domain operations
- Complex multi-step workflows

**Message Flow**:
```
Command received on queue (e.g., APP.CMD.CREATEACCOUNT.Q)
    ↓
PaymentCommandConsumer.onCreateAccountCommand()
    ↓
CommandHandlerRegistry routes to handler
    ↓
Handler executes (updates database)
    ↓
Response sent to reply queue (APP.CMD.REPLY.Q)
```

**Current Test Gaps**:
- No integration tests for PaymentCommandConsumer receiving actual JMS messages
- Command handlers tested in isolation, not with real message flow
- No e2e workflow tests (create account → add limits → payment)
- Repository integration tests exist but no message-driven tests

**Coverage Targets**:
- PaymentCommandConsumer: ~95% (mostly routing logic)
- AccountService: ~85% (business logic paths)
- PaymentService: ~85% (complex workflows)
- LimitService: ~85% (edge cases)
- Orchestration: ~80% (workflow coordination)

---

### 3. msg-platform-worker (5 main Java files)

**Key Responsibilities**:
- `CommandConsumers`: JMS listener for basic commands (CreateUser)
- `UserService`: Simple user CRUD operations

**Message Flow**:
```
CreateUser command received on queue
    ↓
CommandConsumers.onCreateUser()
    ↓
UserService.createUser()
    ↓
Response sent to reply queue
```

**Current Test Gaps**:
- No integration tests with actual message flow
- Only E2E test exists (SingleCommandE2ETest) but is not tagged as such

**Coverage Targets**:
- CommandConsumers: ~90%
- UserService: ~90%

---

## Integration Test Strategy

### Test Environment Setup

**H2 Database** (already configured in `application-test.yml`):
```yaml
datasources:
  default:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;...
    driver-class-name: org.h2.Driver
```

**Embedded ActiveMQ** (already configured via `TestMqFactoryProvider`):
```java
@Requires(env = "test")
@Factory
public class TestMqFactoryProvider {
    @Bean(preDestroy = "stop")
    public BrokerService brokerService() throws Exception {
        BrokerService broker = new BrokerService();
        broker.setPersistent(false);  // In-memory only
        broker.addConnector("vm://localhost");
        // ...
    }
}
```

**Test Profiles**:
```
MICRONAUT_ENVIRONMENTS=test
```

### Test Class Patterns

**Pattern 1: OutboxRelay/Sweeper Integration Tests**
```java
@MicronautTest(environments = {"test"})
class OutboxProcessorIntegrationTest {

    // Setup: Insert outbox records in PENDING state
    // Action: Trigger OutboxRelay.publishNow() or OutboxSweeper.tick()
    // Verify:
    //   - Messages published to ActiveMQ
    //   - Database marked as PUBLISHED
    //   - Backoff timing on failures
    //   - Stuck message recovery
}
```

**Pattern 2: Command Consumer Integration Tests**
```java
@MicronautTest(environments = {"test"})
class PaymentCommandConsumerIntegrationTest {

    // Setup: Create accounts/limits in database
    // Action: Send command via ActiveMQ queue
    // Verify:
    //   - Command received and processed
    //   - Database state updated
    //   - Response sent to reply queue
    //   - Errors handled with reply message
}
```

**Pattern 3: E2E Workflow Tests**
```java
@MicronautTest(environments = {"test"})
class PaymentWorkflowE2ETest {

    // Scenario 1: Create account with limits
    // Send: CreateAccount command
    // Verify: Account created
    // Send: CreateLimits command
    // Verify: Limits applied

    // Scenario 2: Simple payment flow
    // Setup: Account with limits
    // Send: InitiateSimplePayment command
    // Send: CreatePayment command
    // Verify: Payment created and transacted
}
```

---

## Test Implementation Plan

### Phase 1: msg-platform-processor (2 test classes)

**1. OutboxProcessorIntegrationTest.java**
- Test OutboxRelay.publishNow() with outbox records
- Test OutboxSweeper.tick() batch processing
- Test message publishing to MQ with different categories (command/reply/event)
- Test retry/backoff logic on failures
- Test stuck message recovery

**2. OutboxProcessorErrorHandlingTest.java**
- Test exception handling in message publishing
- Test database rollback on publish failures
- Test backoff calculation
- Test claim/release logic under concurrency

### Phase 2: msg-platform-payments-worker (3 test classes)

**1. PaymentCommandConsumerIntegrationTest.java**
- Create test data in database
- Send payment commands via ActiveMQ (CreateAccount, CreateLimits, CreatePayment)
- Verify database state changes
- Verify response messages sent to reply queue
- Test error scenarios (invalid input, constraint violations)

**2. PaymentWorkflowIntegrationTest.java**
- Test CreateAccount workflow (account creation process)
- Test CreateLimits workflow (add limits to account)
- Test SimplePayment workflow (from initiation to completion)
- Verify all database entities created
- Verify response messages for each step

**3. PaymentServiceIntegrationTest.java**
- Test AccountService with H2 database
- Test PaymentService with real transaction processing
- Test FxService contract management
- Test LimitService operations
- Validate constraint handling

### Phase 3: msg-platform-worker (1 test class)

**1. WorkerCommandConsumerIntegrationTest.java**
- Send CreateUser command via ActiveMQ
- Verify user created in H2 database
- Verify response sent
- Test error handling

---

## Test Scenarios & Coverage

### msg-platform-processor

**OutboxRelay Tests**:
```
✓ Publish PENDING command to MQ
✓ Publish PENDING reply to MQ
✓ Publish PENDING event to Kafka
✓ Mark as PUBLISHED after successful publish
✓ Reschedule with backoff on publish failure
✓ Claim one message for immediate publish
✓ Batch claim messages in scheduled sweep
✓ Skip already-claimed messages (concurrent safety)
✓ Handle unknown category gracefully
```

**OutboxSweeper Tests**:
```
✓ Batch process up to 500 messages
✓ Recover stuck SENDING messages (stuck for > 10s)
✓ Publish and mark PUBLISHED
✓ Backoff and reschedule on failure
✓ Calculate exponential backoff correctly
✓ Cap backoff at 300 seconds
✓ Log debug/warn messages appropriately
```

### msg-platform-payments-worker

**PaymentCommandConsumer Tests**:
```
✓ onCreateAccountCommand: Creates account, sends reply
✓ onCreateLimitsCommand: Adds limits, sends reply
✓ onCreatePaymentCommand: Creates payment, sends reply
✓ onInitiateSimplePaymentCommand: Initiates workflow
✓ onBookFxCommand: Books FX contract
✓ onReverseTransactionCommand: Reverses transaction
✓ Error handling: Invalid command format
✓ Error handling: Database constraint violation
✓ Error handling: Missing account/limit
```

**Workflow Tests**:
```
✓ CreateAccount workflow: Account created, state ACTIVE
✓ CreateLimits workflow: Limits applied to account
✓ SimplePayment workflow: Payment from start to completion
✓ Multi-step workflow: Create account → limits → payment
✓ Error recovery: Transaction rollback on failure
```

**Service Tests**:
```
✓ AccountService.create(): Valid account creation
✓ AccountService.findById(): Retrieve account
✓ PaymentService.create(): Payment with limits check
✓ PaymentService.exceed limits(): Rejected payment
✓ FxService.book(): FX contract creation
✓ LimitService.create(): Limit assignment
✓ Concurrent operations: No race conditions
```

### msg-platform-worker

**Worker Tests**:
```
✓ CreateUser command: User created in database
✓ User response: Reply sent to queue
✓ Error handling: Invalid user data
✓ Database persistence: User persists after JVM restart (H2)
```

---

## Coverage Goals

| Module | Class | Target | Strategy |
|--------|-------|--------|----------|
| processor | OutboxRelay | 85% | Test claim/send/mark, error paths, backoff |
| processor | OutboxSweeper | 85% | Test sweep, recovery, backoff, error handling |
| processor | ProcessManager | 80% | Test workflow coordination |
| processor | ResponseRegistry | 90% | Test response collection/retrieval |
| processor | TransactionalCommandBus | 90% | Test command execution + transaction |
| payments | PaymentCommandConsumer | 95% | Test all 11 command handlers |
| payments | AccountService | 85% | Test CRUD + business logic |
| payments | PaymentService | 85% | Test payment lifecycle + limits |
| payments | FxService | 85% | Test FX operations |
| payments | LimitService | 85% | Test limit management |
| payments | Workflows | 80% | Test orchestration |
| worker | CommandConsumers | 90% | Test command routing |
| worker | UserService | 90% | Test CRUD operations |

**Overall Target**: **80% line + branch coverage across all three modules**

---

## Test Data Setup

### Database Setup (H2)

Each test uses `@MicronautTest` with test profile:
```java
@MicronautTest(environments = {"test"})
class IntegrationTest {
    // H2 database auto-initialized from migrations
    // test-keyspace-1.sql, test-keyspace-2.sql, etc. (if using Flyway in test)
    // Or use @Sql annotations to seed data
}
```

### Message Setup (ActiveMQ)

Send commands via `ConnectionFactory`:
```java
@Inject
private ConnectionFactory connectionFactory;

void sendCreateAccountCommand() {
    try (var connection = connectionFactory.createConnection();
         var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
        Queue queue = session.createQueue("APP.CMD.CREATEACCOUNT.Q");
        TextMessage message = session.createTextMessage(commandJson);
        session.createProducer(queue).send(message);
    }
}
```

### Data Factories

Create builders for common test data:
```java
class OutboxTestData {
    static Outbox command(String topic, String payload) { ... }
    static Outbox reply(String topic, String payload) { ... }
    static Outbox event(String topic, String payload) { ... }
}

class PaymentTestData {
    static Account account() { ... }
    static AccountLimit limit() { ... }
    static Payment payment() { ... }
}
```

---

## Files to Create/Modify

### New Test Files

```
msg-platform-processor/src/test/java/com/acme/reliable/processor/
├── OutboxProcessorIntegrationTest.java
└── OutboxProcessorErrorHandlingTest.java

msg-platform-payments-worker/src/test/java/com/acme/payments/
├── integration/
│   ├── PaymentCommandConsumerIntegrationTest.java
│   ├── PaymentWorkflowIntegrationTest.java
│   ├── PaymentServiceIntegrationTest.java
│   └── testdata/
│       ├── OutboxTestData.java
│       └── PaymentTestData.java
└── e2e/
    └── FullPaymentFlowE2ETest.java

msg-platform-worker/src/test/java/com/acme/reliable/
├── CommandConsumerIntegrationTest.java
└── testdata/
    └── WorkerTestData.java
```

### Test Configuration Files

```
msg-platform-processor/src/test/resources/
└── db/migration/V_test_*.sql (if needed for test-specific migrations)

msg-platform-payments-worker/src/test/resources/
└── db/migration/V_test_*.sql (if needed)

msg-platform-worker/src/test/resources/
└── application-test.yml (ensure it exists)
```

### POM Updates

Ensure all modules have:
```xml
<!-- In dependencyManagement or direct dependency -->
<dependency>
    <groupId>io.micronaut.test</groupId>
    <artifactId>micronaut-test-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Execution & Verification

### Run Tests

```bash
# Run all integration tests with test profile
MICRONAUT_ENVIRONMENTS=test mvn clean test

# Run specific module
MICRONAUT_ENVIRONMENTS=test mvn clean test -pl msg-platform-processor

# Run specific test class
MICRONAUT_ENVIRONMENTS=test mvn clean test -Dtest=OutboxProcessorIntegrationTest
```

### Coverage Verification

```bash
# Run with coverage
MICRONAUT_ENVIRONMENTS=test mvn clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Success Criteria

- ✅ All integration tests pass
- ✅ No flakiness from message timing (use explicit waits, not Thread.sleep)
- ✅ 80% line coverage per module
- ✅ 70% branch coverage per module
- ✅ Tests run in < 5 seconds total
- ✅ No external dependencies (no Docker, no Redis, no actual brokers)

---

## Challenges & Solutions

### Challenge 1: Timing Issues

**Problem**: Messages sent via ActiveMQ arrive asynchronously

**Solution**: Use explicit synchronization primitives
```java
CountDownLatch messageReceived = new CountDownLatch(1);
// Send message
messageReceived.await(5, TimeUnit.SECONDS);  // Wait with timeout
assertTrue(messageReceived.getCount() == 0, "Message not received");
```

### Challenge 2: Database State Verification

**Problem**: Multiple threads may update database during test

**Solution**: Use transactional queries with explicit waits
```java
// Wait for database update
Awaitility.await()
    .atMost(Duration.ofSeconds(5))
    .untilAsserted(() -> {
        var account = accountRepository.findById(...);
        assertTrue(account.isPresent());
        assertEquals("ACTIVE", account.get().getStatus());
    });
```

### Challenge 3: Queue Isolation Between Tests

**Problem**: Embedded ActiveMQ reuses broker instance across tests

**Solution**: Use unique queue names per test or reset queue state
```java
// Either use unique queue name
String queueName = "TEST.QUEUE." + UUID.randomUUID();

// Or clear queue before test
void clearQueue(String queueName) {
    var queue = broker.getRegionBroker().getQueues()
        .get(new org.apache.activemq.command.ActiveMQQueue(queueName));
    if (queue != null) queue.purge();
}
```

### Challenge 4: Scheduled Tasks

**Problem**: OutboxRelay/Sweeper run on schedule, hard to test timing

**Solution**: Call methods directly in tests, don't rely on @Scheduled
```java
// Inject OutboxRelay/Sweeper
@Inject
private OutboxRelay relay;

@Test
void testPublishNow() {
    // Setup
    outboxRepository.save(new Outbox(...));

    // Action - call directly
    relay.publishNow(id);

    // Verify
    assertEquals(PUBLISHED, outboxRepository.findById(id).getStatus());
}
```

---

## Next Steps

1. **Phase 1**: Implement processor integration tests (2 classes)
2. **Phase 2**: Implement payments-worker integration tests (3 classes)
3. **Phase 3**: Implement worker integration tests (1 class)
4. **Validation**: Run full test suite, verify coverage targets, address gaps
5. **Documentation**: Update README with test execution instructions

---

## Success Metrics

- [ ] All 6 integration test classes created and passing
- [ ] OutboxRelay & OutboxSweeper: 85% coverage
- [ ] Payment services: 85% coverage
- [ ] Workflows: 80% coverage
- [ ] Worker: 90% coverage
- [ ] Overall: 80% line + branch coverage
- [ ] All tests complete in < 10 seconds
- [ ] Zero flaky tests
- [ ] No dependency on external services
