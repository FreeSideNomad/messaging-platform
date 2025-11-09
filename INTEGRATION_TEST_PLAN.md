# Integration Test Planning Guide

## Quick Reference: Module Responsibilities

### msg-platform-processor (1120 test lines)
**Core:** Message publishing, process orchestration, command routing

| Class | Responsibility | Test Coverage | Gap |
|-------|-----------------|----------------|-----|
| TransactionalCommandBus | Entry point, idempotency check | Unit ✓ | Integration ✗ |
| OutboxRelay | Scheduled polling, batch publishing | Unit ✓ | Failure scenarios ✗ |
| NotifyPublisher | Redis-based fast publishing | Unit ✓ | Backpressure ✗ |
| ProcessManager | Auto-discovery, step execution | Integration ✓ | Cross-worker ✗ |
| ProcessReplyConsumer | Reply routing (STUB) | NONE ✗ | Implementation needed |
| ResponseRegistry | Future-based response tracking | Unit ✓ | Timeout ✗ |
| FastPathPublisher | Post-commit publishing (DISABLED) | NONE ✗ | Fix leak, then test |

---

### msg-platform-payments-worker (8911 test lines)
**Domain:** Payment processing, limits, FX operations

| Class | Responsibility | Test Coverage | Gap |
|-------|-----------------|----------------|-----|
| AccountService | Account CRUD, transactions | Unit ✓, Integration ✓ | Process flow ✗ |
| PaymentService | Payment CRUD | Unit ✓, Integration ✓ | Process flow ✗ |
| FxService | FX contracts, unwinding | Unit ✓, Integration ✓ | Process flow ✗ |
| LimitService | Limits, booking, reversal | Unit ✓, Integration ✓ | Expiration ✗, Concurrent ✗ |
| CreateAccountProcessDefinition | Account creation flow | Unit ✓ | E2E with Processor ✗ |
| SimplePaymentProcessDefinition | Payment flow with compensation | Unit ✓ | Compensation ✗, E2E ✗ |
| PaymentCommandConsumer | JMS entry point | Config only | Full test ✗ |

---

### msg-platform-worker (257 test lines)
**Framework:** Generic command processing, user service example

| Class | Responsibility | Test Coverage | Gap |
|-------|-----------------|----------------|-----|
| CommandConsumers | JMS listeners (generic + reply) | Config only | Full test ✗ |
| UserService | Example handler | Config only | Handler test ✗ |
| WorkerE2ETestBase | E2E infrastructure | Base ✓ | Usage ✗ |
| SingleCommandE2ETest | Single command E2E | E2E ✓ | Saga ✗ |

---

## Test Pyramid: Recommended Coverage

```
                    ▲
                    │  E2E Integration Tests (5 tests, ~500-800 LOC)
                    │  Level: Full stack - API → Processor → Worker(s) → DB
                    │  Scope: Complete workflows with compensation
                    │  
                   /│\
                  / │ \
                 /  │  \ Component Integration Tests (12 tests, ~1500-2000 LOC)
                /   │   \ Level: Module × Module (Processor ↔ Worker)
                /    │    \ Scope: Message flow, outbox, reply routing
               /     │     \
              /───────┼──────────\
             /        │         \  Unit Tests (existing 10K+ LOC)
            /         │          \ Level: Individual classes
           /          │           \ Scope: Service logic, domain models
          /────────────┼─────────────\
         /             │              \
   (Continue expanding pyramid downward)
```

---

## Five High-Priority Integration Tests to Implement

### Test 1: Single Command Submission E2E (Worker Module)
**Status:** PARTIAL - SingleCommandE2ETest exists, incomplete  
**Duration:** 1 day to complete  
**Scope:** API → Database only (no async processing)

**Test steps:**
1. Submit CreateUser command via API
2. Verify 202 Accepted response
3. Query database - verify PENDING status
4. Verify idempotency key prevents duplicate
5. Query outbox - verify message queued

**Assertions:**
```java
// Command persisted with correct state
command.status == PENDING
command.idempotencyKey == submitted key
command.payload matches submitted JSON

// Outbox entry created for publishing
outbox.category == "command"
outbox.topic == "APP.CMD.CREATEUSER.Q"
outbox.status == PENDING
```

**File:** `/msg-platform-worker/src/test/java/com/acme/reliable/e2e/SingleCommandE2ETest.java` (EXPAND)

---

### Test 2: Processor → MQ Publishing Integration (Processor Module)
**Status:** NONE  
**Duration:** 2-3 days  
**Scope:** Outbox relay publishing, failure recovery

**Test scenario:**
1. Create outbox entries in PENDING state
2. Trigger OutboxRelay sweep
3. Verify messages published to IBM MQ
4. Verify status changed to PUBLISHED
5. Simulate publish failure → verify backoff/retry

**Assertions:**
```java
// Claim and publish
outbox.status == PENDING → outbox.status == PUBLISHED
mq.send() called with correct message
mq.send() called with category-based routing (command/reply/event)

// Failure recovery
publish() throws exception → status == SENDING (claimed)
After backoff, status == PENDING (reclaimed)
attempt count incremented
error message logged
```

**Architecture:**
```java
@MicronautTest(transactional=false, environments="test")
@Testcontainers
class OutboxRelayPublishingIntegrationTest {
  @Container static PostgreSQLContainer<?> postgres;
  @Container static GenericContainer<?> ibmmq; // or mock
  
  @Inject OutboxRepository outboxRepository;
  @Inject OutboxRelay relay;
  @MockBean(CommandQueue) CommandQueue mockMq;
  
  @Test
  void testOutboxRelayClaimsAndPublishes() {
    // 1. Arrange: create PENDING outbox entries
    // 2. Act: relay.sweepOnce()
    // 3. Assert: entries published, status updated
  }
}
```

---

### Test 3: Payment Account Creation with Limits (Payments-Worker Integration)
**Status:** PARTIAL - CreateAccountWithLimitsE2ETest exists (unit domain only)  
**Duration:** 3-4 days  
**Scope:** Full process orchestration with real ProcessManager

**Test scenario:**
1. Submit InitiateCreateAccountProcess command
2. ProcessManager receives and begins orchestration
3. Step 1: Execute CreateAccountCommand → accountService.handleCreateAccount()
4. Step 2: Execute CreateLimitsCommand → limitService.handleCreateLimits()
5. Step 3: Execute CompleteAccountCreationCommand
6. Process completes, verify all aggregates created

**Assertions:**
```java
// Account created
Account account = accountRepository.findById(accountId);
account.customerId == submitted.customerId
account.currencyCode == submitted.currencyCode

// Limits created
List<AccountLimit> limits = limitRepository.findActiveByAccountId(accountId);
limits.size() == 4 (HOUR, DAY, WEEK, MONTH)

// Process completed
ProcessInstance process = processRepository.findById(processId);
process.status == COMPLETED
process.currentStep == "COMPLETE"
```

**Architecture:**
```java
@MicronautTest(transactional=false, environments="test")
@Testcontainers
class CreateAccountProcessIntegrationTest {
  @Container static PostgreSQLContainer<?> postgres;
  
  @Inject ProcessManager processManager;
  @Inject AccountRepository accountRepository;
  @Inject AccountLimitRepository limitRepository;
  @Inject ProcessRepository processRepository;
  
  @Test
  @Transactional
  void testCreateAccountProcessWithLimits() {
    // 1. Arrange: prepare InitiateCreateAccountProcess
    // 2. Act: processManager.startProcess()
    // 3. Assert: account, limits, process in correct state
  }
}
```

---

### Test 4: Simple Payment with FX and Compensation (Payments-Worker Integration)
**Status:** NONE  
**Duration:** 4-5 days  
**Scope:** Complete saga with failure and compensation

**Test scenario - Happy path:**
1. Submit InitiateSimplePaymentCommand with requiresFx=true
2. Step 1: BookLimitsCommand
3. Step 2: BookFxCommand (conditional)
4. Step 3: CreateTransactionCommand
5. Step 4: CreatePaymentCommand (terminal)
6. Verify all aggregates created, process completed

**Test scenario - Compensation:**
1. Same setup, but mock CreateTransactionCommand to fail
2. ProcessManager catches failure
3. Executes compensation in reverse:
   - UnwindFxCommand
   - ReverseLimitsCommand
4. Verify compensation executed, process marked FAILED
5. Verify FX contract unwound, limits reversed

**Assertions (Happy Path):**
```java
// All steps executed
Payment payment = paymentRepository.findById(paymentId);
payment.status == COMPLETED

FxContract fx = fxContractRepository.findById(fxContractId);
fx.status == BOOKED
fx.rate == creditAmount / debitAmount

AccountLimit limit = limitRepository.findById(limitId);
limit.utilized > ZERO
limit.available = limit.limitAmount - limit.utilized

ProcessInstance process = processRepository.findById(processId);
process.status == COMPLETED
```

**Assertions (Compensation):**
```java
// Compensation executed
FxContract fx = fxContractRepository.findById(fxContractId);
fx.status == UNWOUND

AccountLimit limit = limitRepository.findById(limitId);
limit.utilized == ZERO // reversed

Payment payment = paymentRepository.findById(paymentId);
payment == null OR payment.status == FAILED // no creation

ProcessInstance process = processRepository.findById(processId);
process.status == FAILED
process.error contains "CreateTransaction failed"
```

**Architecture:**
```java
@MicronautTest(transactional=false, environments="test")
@Testcontainers
class SimplePaymentProcessIntegrationTest {
  @Container static PostgreSQLContainer<?> postgres;
  
  @Inject ProcessManager processManager;
  @Inject PaymentRepository paymentRepository;
  @Inject FxContractRepository fxRepository;
  @Inject AccountLimitRepository limitRepository;
  @Inject ProcessRepository processRepository;
  
  @MockBean(PaymentService)
  PaymentService paymentService; // can fail for compensation test
  
  @Nested
  class HappyPath {
    @Test
    @Transactional
    void testPaymentProcessCompletes() { }
  }
  
  @Nested
  class Compensation {
    @Test
    @Transactional
    void testPaymentProcessCompensatesOnFailure() {
      // Setup: Mock PaymentService to throw exception
      when(paymentService.createPayment(any()))
        .thenThrow(new RuntimeException("Payment creation failed"));
      
      // Act: ProcessManager executes
      // Assert: Compensation executed
    }
  }
}
```

---

### Test 5: ProcessReplyConsumer Route-Back Integration (Processor Module)
**Status:** STUB - NOT IMPLEMENTED  
**Duration:** 2-3 days  
**Scope:** Reply reception and ProcessManager update

**Prerequisite:** Implement ProcessReplyConsumer (uncomment JMS listener, wire properly)

**Test scenario:**
1. Start a payment process
2. Process waits for BookFxCommand reply
3. Worker sends reply to APP.CMD.REPLY.Q
4. ProcessReplyConsumer receives reply
5. Route to ProcessManager.handleReply()
6. ProcessManager advances to next step
7. Next command emitted

**Assertions:**
```java
// Reply received and routed
ProcessInstance before = processRepository.findById(processId);
before.currentStep == "WAITING_FOR_BOOKFX"

// After reply consumed:
ProcessInstance after = processRepository.findById(processId);
after.currentStep == "CREATE_TRANSACTION"
after.data.get("fxContractId") != null

// Next command created in outbox
List<Outbox> nextCommands = outboxRepository.findByProcessId(processId);
nextCommands[1].type == "CreateTransactionCommand"
```

**Architecture:**
```java
@MicronautTest(transactional=false, environments="test")
@Testcontainers
class ProcessReplyConsumerIntegrationTest {
  @Container static PostgreSQLContainer<?> postgres;
  
  @Inject ProcessManager processManager;
  @Inject ProcessReplyConsumer replyConsumer;
  @Inject OutboxRepository outboxRepository;
  @Inject ProcessRepository processRepository;
  
  @Test
  @Transactional
  void testReplyRoutesBackToProcessManager() {
    // 1. Start payment process
    // 2. Process waits for reply
    // 3. Send reply via replyConsumer.onReply()
    // 4. Assert: ProcessManager advanced state
    // 5. Assert: Next command in outbox
  }
}
```

---

## Test Implementation Checklist

### Phase 1: Foundation (Week 1-2)
- [ ] Fix FastPathPublisher transaction leak
- [ ] Implement ProcessReplyConsumer (uncomment, wire JMS listener)
- [ ] Setup shared test infrastructure (Testcontainers orchestration)
- [ ] Create test fixtures and data builders

### Phase 2: Component Integration (Week 2-3)
- [ ] Test 1: Expand SingleCommandE2ETest
- [ ] Test 2: Implement OutboxRelay publishing integration
- [ ] Add mock factories for IBM MQ, Kafka
- [ ] Database assertions helpers

### Phase 3: Domain Integration (Week 3-4)
- [ ] Test 3: Expand CreateAccountWithLimitsE2ETest with ProcessManager
- [ ] Test 4: Implement SimplePaymentProcessIntegrationTest (happy path)
- [ ] Test 4: Add compensation path variant

### Phase 4: Cross-Module (Week 4-5)
- [ ] Test 2: Add failure scenarios (publish errors)
- [ ] Test 4: Add timeout/retry scenarios
- [ ] Test 5: ProcessReplyConsumer integration
- [ ] Multi-worker orchestration test

### Phase 5: Polish (Week 5+)
- [ ] Concurrent command tests
- [ ] Idempotency validation tests
- [ ] Message corruption handling tests
- [ ] Performance/load tests (Vegeta integration)

---

## Test Data Builders

### Example: PaymentCommandBuilder
```java
public class PaymentCommandBuilder {
  public InitiateSimplePaymentCommand buildWithFx(UUID debitAccountId) {
    return new InitiateSimplePaymentCommand(
      UUID.randomUUID(),               // customerId
      debitAccountId,
      UUID.randomUUID(),               // creditAccountId
      Money.of(1000, "USD"),           // debitAmount
      Money.of(900, "EUR"),            // creditAmount
      LocalDate.now().plusDays(1),     // valueDate
      new Beneficiary(...),
      true                             // requiresFx
    );
  }
  
  public InitiateSimplePaymentCommand buildWithoutFx(UUID debitAccountId) {
    return buildWithFx(debitAccountId)
      .withRequiresFx(false);
  }
}
```

---

## Database State Validators

### Example: PaymentStateValidator
```java
public class PaymentStateValidator {
  private final PaymentRepository paymentRepository;
  private final FxContractRepository fxRepository;
  private final AccountLimitRepository limitRepository;
  
  public void assertPaymentCompleted(UUID paymentId, UUID accountId) {
    Payment payment = paymentRepository.findById(paymentId)
      .orElseThrow(() -> new AssertionError("Payment not found"));
    
    assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment.debitAccountId()).isEqualTo(accountId);
  }
  
  public void assertLimitsReversed(UUID accountId) {
    List<AccountLimit> limits = limitRepository
      .findActiveByAccountId(accountId);
    
    assertThat(limits)
      .allMatch(l -> l.utilized().isZero(),
        "All limits should be reversed");
  }
  
  public void assertFxUnwound(UUID fxContractId) {
    FxContract fx = fxRepository.findById(fxContractId)
      .orElseThrow(() -> new AssertionError("FX contract not found"));
    
    assertThat(fx.status()).isEqualTo(FxStatus.UNWOUND);
  }
}
```

---

## Mock Factory Pattern

### Example: MqMockFactory
```java
@Singleton
public class MqMockFactory {
  private final Queue<MqMessage> sentMessages = new LinkedBlockingQueue<>();
  private final AtomicBoolean failNextPublish = new AtomicBoolean(false);
  
  @MockBean(CommandQueue.class)
  public CommandQueue mockCommandQueue() {
    return (topic, payload, headers) -> {
      if (failNextPublish.getAndSet(false)) {
        throw new RuntimeException("Simulated MQ failure");
      }
      sentMessages.offer(new MqMessage(topic, payload, headers));
    };
  }
  
  public MqMessage getNextSentMessage() throws InterruptedException {
    return sentMessages.poll(5, TimeUnit.SECONDS);
  }
  
  public void failNextPublish() {
    failNextPublish.set(true);
  }
}
```

---

## Continuous Integration Setup

### GitHub Actions Test Configuration
```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  integration-tests:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: test
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432
      
      ibmmq:
        image: icr.io/ibm-messaging/mq:9.4
        env:
          LICENSE: accept
        ports:
          - 1414:1414
    
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: 21
          distribution: temurin
      
      - name: Run integration tests
        run: |
          mvn clean verify \
            -Dgroups="integration|e2e" \
            -DskipUnitTests=true \
            -pl msg-platform-processor,msg-platform-payments-worker,msg-platform-worker
```

---

## Estimated Test LOC & Timeline

| Phase | Component | Tests | Est. LOC | Effort |
|-------|-----------|-------|----------|--------|
| 1 | Fixes (FastPath, ReplyConsumer) | 2 | 200 | 3 days |
| 2 | OutboxRelay publishing | 3 | 400 | 2 days |
| 2 | SingleCommand E2E | 2 | 300 | 1 day |
| 3 | Account creation process | 3 | 500 | 2 days |
| 3 | Payment process (happy) | 4 | 600 | 3 days |
| 4 | Payment process (compensation) | 3 | 400 | 2 days |
| 4 | Reply consumer integration | 3 | 400 | 2 days |
| 5 | Scenarios & Polish | 6 | 600 | 3 days |
| **TOTAL** | | **26** | **3400** | **18 days** |

---

## Success Criteria

✓ All 26 integration tests passing  
✓ Coverage of all critical workflows (account creation, payments)  
✓ Compensation flows tested (happy path + failure)  
✓ Reply routing from worker back to ProcessManager  
✓ Message publishing with failure recovery  
✓ Database state validation for all operations  
✓ Idempotency enforcement verified  
✓ E2E tests runnable in CI/CD pipeline  

---

**Next Step:** Begin with Test 1 (expand SingleCommandE2ETest) and Test 2 (OutboxRelay integration)
