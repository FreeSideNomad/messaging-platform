# Test Strategy Plan - Messaging Platform

## Executive Summary

Comprehensive testing strategy for the multi-module messaging platform ensuring:

- **Unit Testing**: >90% code and branch coverage
- **Integration Testing**: Database, JMS, Kafka integration
- **E2E Testing**: Full flow from API → Worker → Database/MQ/Kafka
- **Static Code Analysis**: SonarQube quality gates
- **Performance Testing**: Load tests with assertions

## Testing Pyramid

```
                    ┌─────────────┐
                    │  E2E Tests  │  ~10 tests  (slowest)
                    │  (< 5%)     │
                ┌───┴─────────────┴───┐
                │ Integration Tests   │  ~50 tests
                │     (15-20%)        │
            ┌───┴─────────────────────┴───┐
            │      Unit Tests             │  ~200 tests (fastest)
            │        (75-80%)             │
            └─────────────────────────────┘
```

## Phase 1: Unit Testing Strategy

### 1.1 Coverage Requirements

**Target**: >90% code coverage, >90% branch coverage

**Tools**:

- JaCoCo for coverage measurement
- Maven Surefire for test execution
- Coverage reports in HTML + XML (for SonarQube)

### 1.2 msg-platform-core Unit Tests

#### Domain Layer Tests

**CommandRepository Tests** (`CommandRepositoryTest.java`)

```java
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommandRepositoryTest {

    @Inject CommandRepository repository;
    @Inject TransactionOperations<Connection> tx;

    @Test
    void insertPending_shouldCreateCommandWithPendingStatus() {
        // Given
        UUID id = UUID.randomUUID();
        String payload = "{\"username\":\"test\"}";

        // When
        tx.executeWrite(status -> {
            repository.insertPending(id, "CreateUser", "biz-key", payload, "idem-key", "{}");
            return null;
        });

        // Then
        Optional<Command> cmd = repository.findById(id);
        assertThat(cmd).isPresent();
        assertThat(cmd.get().getStatus()).isEqualTo("PENDING");
        assertThat(cmd.get().getName()).isEqualTo("CreateUser");
    }

    @Test
    void updateToRunning_shouldSetStatusAndLease() { /* ... */ }

    @Test
    void updateToSucceeded_shouldUpdateStatus() { /* ... */ }

    @Test
    void updateToFailed_shouldSetErrorAndStatus() { /* ... */ }

    @Test
    void incrementRetries_shouldBumpRetryCount() { /* ... */ }

    @Test
    void existsByIdempotencyKey_shouldReturnTrueWhenExists() { /* ... */ }
}
```

**Coverage Target**: 100% (all repository methods tested)

**InboxRepository Tests** (`InboxRepositoryTest.java`)

```java
@Test
void insertIfAbsent_shouldInsertNewEntry() {
    // Given
    String messageId = "msg-123";
    String handler = "CreateUser";

    // When
    int rows = tx.executeWrite(s -> repository.insertIfAbsent(messageId, handler));

    // Then
    assertThat(rows).isEqualTo(1);
}

@Test
void insertIfAbsent_shouldNotInsertDuplicate() {
    // Given - insert once
    tx.executeWrite(s -> repository.insertIfAbsent("msg-123", "CreateUser"));

    // When - try duplicate
    int rows = tx.executeWrite(s -> repository.insertIfAbsent("msg-123", "CreateUser"));

    // Then
    assertThat(rows).isEqualTo(0); // ON CONFLICT DO NOTHING
}
```

**OutboxRepository Tests** (`OutboxRepositoryTest.java`)

```java
@Test
void insert_shouldCreateOutboxEntry() { /* ... */ }

@Test
void markPublished_shouldUpdateStatus() { /* ... */ }

@Test
void reschedule_shouldSetRetryTime() { /* ... */ }

@Test
void claimBatch_shouldReturnUnpublishedEntries() { /* ... */ }
```

**DlqRepository Tests** (`DlqRepositoryTest.java`)

```java
@Test
void insertDlqEntry_shouldStoreFailedCommand() { /* ... */ }
```

#### Service Layer Tests

**CommandService Tests** (`CommandServiceTest.java`)

```java
@MicronautTest
class CommandServiceTest {

    @MockBean(CommandRepository.class)
    CommandRepository mockRepository() { return mock(CommandRepository.class); }

    @Inject CommandService service;
    @Inject CommandRepository repository;

    @Test
    void savePending_shouldInsertCommandAndReturnId() {
        // Given
        UUID expectedId = UUID.randomUUID();

        // When
        UUID actualId = service.savePending("CreateUser", "idem-key", "biz-key", "{}", "{}");

        // Then
        assertThat(actualId).isNotNull();
        verify(repository).insertPending(any(), eq("CreateUser"), eq("biz-key"), eq("{}"), eq("idem-key"), eq("{}"));
    }

    @Test
    void markRunning_shouldUpdateCommandStatus() {
        // Given
        UUID id = UUID.randomUUID();
        Instant lease = Instant.now().plusSeconds(300);

        // When
        service.markRunning(id, lease);

        // Then
        verify(repository).updateToRunning(eq(id), any(Timestamp.class));
    }

    // Similar tests for markSucceeded, markFailed, bumpRetry, markTimedOut, existsByIdempotencyKey
}
```

**Coverage Target**: 100% (all service methods tested)

**OutboxService Tests** (`OutboxServiceTest.java`)

```java
@Test
void addReturningId_shouldInsertOutboxEntryAndReturnId() { /* ... */ }

@Test
void claimOne_shouldClaimAndReturnEntry() { /* ... */ }

@Test
void claimOne_shouldReturnEmptyWhenAlreadyClaimed() { /* ... */ }

@Test
void claim_shouldReturnBatchOfEntries() { /* ... */ }

@Test
void markPublished_shouldUpdateStatus() { /* ... */ }

@Test
void reschedule_shouldSetRetryTime() { /* ... */ }
```

#### Core Logic Tests

**CommandBus Tests** (`CommandBusTest.java`)

```java
@MicronautTest
class CommandBusTest {

    @MockBean(CommandService.class)
    CommandService mockCommandService() { return mock(CommandService.class); }

    @MockBean(OutboxService.class)
    OutboxService mockOutboxService() { return mock(OutboxService.class); }

    @MockBean(Outbox.class)
    Outbox mockOutbox() { return mock(Outbox.class); }

    @Inject CommandBus commandBus;
    @Inject CommandService commandService;
    @Inject OutboxService outboxService;

    @Test
    void submit_shouldCheckIdempotency() {
        // Given
        when(commandService.existsByIdempotencyKey("key")).thenReturn(true);

        // When/Then
        assertThrows(IllegalStateException.class,
            () -> commandBus.submit("CreateUser", "key", "biz-key", "{}"));
    }

    @Test
    void submit_shouldSaveCommandAndPublishToMq() {
        // Given
        UUID cmdId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        when(commandService.existsByIdempotencyKey("key")).thenReturn(false);
        when(commandService.savePending(any(), any(), any(), any(), any())).thenReturn(cmdId);
        when(outboxService.addReturningId(any())).thenReturn(outboxId);

        // When
        UUID result = commandBus.submit("CreateUser", "key", "biz-key", "{}");

        // Then
        assertThat(result).isEqualTo(cmdId);
        verify(commandService).savePending("CreateUser", "key", "biz-key", "{}", "{}");
        verify(outboxService).addReturningId(any());
    }
}
```

**Coverage Target**: >90% (all paths tested including error cases)

**Executor Tests** (`ExecutorTest.java`)

```java
@Test
void process_shouldCheckInboxForDuplicates() {
    // Given
    when(inboxService.tryInsert("msg-id", "CreateUser")).thenReturn(0); // duplicate

    // When
    executor.process(cmdId, "msg-id", "CreateUser", "{}");

    // Then
    verify(inboxService).tryInsert("msg-id", "CreateUser");
    verifyNoInteractions(commandService); // Should not process duplicate
}

@Test
void process_shouldExecuteHandlerAndMarkSuccess() {
    // Given
    when(inboxService.tryInsert(any(), any())).thenReturn(1); // new message
    when(commandService.find(cmdId)).thenReturn(Optional.of(record));
    Handler mockHandler = mock(Handler.class);
    when(handlerRegistry.find("CreateUser")).thenReturn(Optional.of(mockHandler));
    when(mockHandler.handle(any())).thenReturn("success");

    // When
    executor.process(cmdId, "msg-id", "CreateUser", "{}");

    // Then
    verify(mockHandler).handle(any());
    verify(commandService).markSucceeded(cmdId);
    verify(outboxService, times(2)).addReturningId(any()); // reply + event
}

@Test
void process_shouldHandleFailureAndMoveToDlq() {
    // Given
    when(handlerRegistry.find("CreateUser")).thenReturn(Optional.of(mockHandler));
    when(mockHandler.handle(any())).thenThrow(new RuntimeException("Handler failed"));
    when(commandService.find(cmdId)).thenReturn(Optional.of(record));

    // When
    executor.process(cmdId, "msg-id", "CreateUser", "{}");

    // Then
    verify(commandService).markFailed(eq(cmdId), contains("Handler failed"));
    verify(dlqService).insertDlqEntry(any(), any(), any(), any(), any(), any(), any(), anyInt(), any());
}

@Test
void process_shouldHandleTimeout() { /* ... */ }

@Test
void process_shouldRetryOnTransientFailure() { /* ... */ }
```

**Coverage Target**: >90% (all execution paths including error cases)

**Outbox Tests** (`OutboxTest.java`)

```java
@Test
void publishCommand_shouldAddCommandToOutbox() { /* ... */ }

@Test
void publishReply_shouldAddReplyToOutbox() { /* ... */ }

@Test
void publishEvent_shouldAddEventToOutbox() { /* ... */ }
```

**OutboxRelay Tests** (`OutboxRelayTest.java`)

```java
@Test
void sweepOnce_shouldClaimAndPublishBatch() {
    // Given
    List<OutboxRow> rows = List.of(
        new OutboxRow(UUID.randomUUID(), "command", "topic", "key", "type", "{}", Map.of(), 0)
    );
    when(outboxService.claim(2000, any())).thenReturn(rows);

    // When
    relay.sweepOnce();

    // Then
    verify(commandQueue).send("topic", "{}", Map.of());
    verify(outboxService).markPublished(rows.get(0).id());
}

@Test
void sweepOnce_shouldRescheduleOnFailure() {
    // Given
    OutboxRow row = new OutboxRow(UUID.randomUUID(), "command", "topic", "key", "type", "{}", Map.of(), 0);
    when(outboxService.claim(anyInt(), any())).thenReturn(List.of(row));
    doThrow(new RuntimeException("MQ down")).when(commandQueue).send(any(), any(), any());

    // When
    relay.sweepOnce();

    // Then
    verify(outboxService).reschedule(eq(row.id()), anyLong(), contains("MQ down"));
}
```

### 1.3 msg-platform-api Unit Tests

**CommandController Tests** (`CommandControllerTest.java`)

```java
@MicronautTest
class CommandControllerTest {

    @Inject @Client("/") HttpClient client;

    @MockBean(CommandBus.class)
    CommandBus mockCommandBus() { return mock(CommandBus.class); }

    @Inject CommandBus commandBus;

    @Test
    void submit_shouldReturn202Accepted() {
        // Given
        UUID cmdId = UUID.randomUUID();
        when(commandBus.submit(any(), any(), any(), any())).thenReturn(cmdId);

        // When
        HttpResponse<Map> response = client.toBlocking().exchange(
            HttpRequest.POST("/commands/CreateUser", Map.of("username", "test"))
                .header("Idempotency-Key", "test-key"),
            Map.class
        );

        // Then
        assertThat(response.status()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.header("X-Command-Id")).isEqualTo(cmdId.toString());
    }

    @Test
    void submit_shouldReturn400WhenMissingIdempotencyKey() { /* ... */ }

    @Test
    void submit_shouldReturn409WhenDuplicateIdempotencyKey() { /* ... */ }

    @Test
    void getStatus_shouldReturnCommandStatus() { /* ... */ }
}
```

### 1.4 msg-platform-worker Unit Tests

**CommandConsumers Tests** (`CommandConsumersTest.java`)

```java
@MicronautTest
class CommandConsumersTest {

    @MockBean(Executor.class)
    Executor mockExecutor() { return mock(Executor.class); }

    @Inject CommandConsumers consumers;
    @Inject Executor executor;

    @Test
    void onCreateUser_shouldExtractIdAndCallExecutor() {
        // Given
        String message = "{\"id\":\"" + UUID.randomUUID() + "\",\"username\":\"test\"}";
        String messageId = "msg-123";

        // When
        consumers.onCreateUser(message, messageId);

        // Then
        verify(executor).process(any(UUID.class), eq(messageId), eq("CreateUser"), eq(message));
    }

    @Test
    void onCreateUser_shouldHandleMalformedJson() { /* ... */ }
}
```

**Handler Tests** (`CreateUserHandlerTest.java`)

```java
@MicronautTest
class CreateUserHandlerTest {

    @Inject CreateUserHandler handler;

    @Test
    void handle_shouldProcessCreateUserCommand() {
        // Given
        String payload = "{\"username\":\"testuser\"}";

        // When
        String result = handler.handle(payload);

        // Then
        assertThat(result).contains("testuser");
        assertThat(result).contains("created");
    }

    @Test
    void handle_shouldValidatePayload() { /* ... */ }
}
```

### 1.5 Configuration for Unit Tests

**Update Parent POM** - Add JaCoCo configuration:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>jacoco-check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>INSTRUCTION</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.90</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.90</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Run Unit Tests**:

```bash
# Run all unit tests with coverage
mvn clean test

# Generate coverage report
mvn jacoco:report

# Check coverage meets threshold (90%)
mvn verify

# View HTML report
open msg-platform-core/target/site/jacoco/index.html
```

## Phase 2: Integration Testing Strategy

### 2.1 Database Integration Tests

**CommandStoreIntegrationTest.java**

```java
@MicronautTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommandStoreIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @Inject CommandService commandService;
    @Inject TransactionOperations<Connection> tx;

    @BeforeEach
    void setUp() {
        // Clean tables
        tx.executeWrite(s -> {
            s.getConnection().createStatement().execute("TRUNCATE TABLE command CASCADE");
            return null;
        });
    }

    @Test
    void fullLifecycle_shouldProcessCommandFromPendingToSucceeded() {
        // Given
        UUID id = commandService.savePending("CreateUser", "idem-key", "biz-key", "{}", "{}");

        // When - mark running
        commandService.markRunning(id, Instant.now().plusSeconds(300));

        // Then - verify running
        var cmd = commandService.find(id);
        assertThat(cmd).isPresent();
        assertThat(cmd.get().status()).isEqualTo("RUNNING");

        // When - mark succeeded
        commandService.markSucceeded(id);

        // Then - verify succeeded
        cmd = commandService.find(id);
        assertThat(cmd.get().status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void idempotency_shouldPreventDuplicateCommands() {
        // Given
        commandService.savePending("CreateUser", "same-key", "biz-key", "{}", "{}");

        // When/Then
        boolean exists = commandService.existsByIdempotencyKey("same-key");
        assertThat(exists).isTrue();
    }

    @Test
    void retry_shouldIncrementRetryCount() { /* ... */ }

    @Test
    void timeout_shouldMarkCommandAsTimedOut() { /* ... */ }
}
```

**OutboxStoreIntegrationTest.java**

```java
@Test
void claim_shouldReturnUnpublishedEntriesOnly() {
    // Given - insert 5 entries
    UUID id1 = outboxService.addReturningId(createRow("NEW"));
    UUID id2 = outboxService.addReturningId(createRow("NEW"));
    UUID id3 = outboxService.addReturningId(createRow("NEW"));
    UUID id4 = outboxService.addReturningId(createRow("NEW"));
    UUID id5 = outboxService.addReturningId(createRow("NEW"));

    // Mark 2 as published
    outboxService.markPublished(id1);
    outboxService.markPublished(id2);

    // When - claim batch of 10
    List<OutboxRow> claimed = outboxService.claim(10, "test-host");

    // Then - should return only 3 NEW entries
    assertThat(claimed).hasSize(3);
    assertThat(claimed).extracting(OutboxRow::id)
        .containsExactlyInAnyOrder(id3, id4, id5);
}

@Test
void claimOne_shouldUseForUpdateSkipLocked() {
    // Given
    UUID id = outboxService.addReturningId(createRow("NEW"));

    // When - claim in two concurrent transactions
    CompletableFuture<Optional<OutboxRow>> future1 = CompletableFuture.supplyAsync(() ->
        tx.executeRead(s -> outboxService.claimOne(id))
    );
    CompletableFuture<Optional<OutboxRow>> future2 = CompletableFuture.supplyAsync(() ->
        tx.executeRead(s -> outboxService.claimOne(id))
    );

    // Then - only one should succeed (SKIP LOCKED)
    Optional<OutboxRow> result1 = future1.join();
    Optional<OutboxRow> result2 = future2.join();

    assertThat(result1.isPresent() ^ result2.isPresent()).isTrue(); // XOR - exactly one present
}
```

**InboxStoreIntegrationTest.java**

```java
@Test
void deduplication_shouldPreventDuplicateProcessing() {
    // Given - insert once
    int rows1 = inboxService.tryInsert("msg-123", "CreateUser");

    // When - try duplicate
    int rows2 = inboxService.tryInsert("msg-123", "CreateUser");

    // Then
    assertThat(rows1).isEqualTo(1); // Inserted
    assertThat(rows2).isEqualTo(0); // Duplicate rejected
}
```

### 2.2 JMS Integration Tests

**JmsIntegrationTest.java**

```java
@MicronautTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JmsIntegrationTest {

    // Use Testcontainers or embedded ActiveMQ for testing
    @Container
    static GenericContainer<?> activemq = new GenericContainer<>("apache/activemq-artemis:latest")
        .withExposedPorts(61616);

    @Inject CommandQueue commandQueue;

    @Test
    void send_shouldPublishMessageToQueue() throws Exception {
        // Given
        String queueName = "TEST.QUEUE";
        String payload = "{\"test\":\"data\"}";
        Map<String, String> headers = Map.of("correlation-id", "123");

        // When
        commandQueue.send(queueName, payload, headers);

        // Then - consume message and verify
        // (use JMS consumer to verify message arrived)
    }
}
```

### 2.3 Kafka Integration Tests

**KafkaIntegrationTest.java**

```java
@MicronautTest
@Testcontainers
class KafkaIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @Inject EventPublisher eventPublisher;

    @Test
    void publish_shouldSendEventToKafkaTopic() {
        // Given
        String topic = "test.events";
        String key = "user-123";
        String payload = "{\"event\":\"created\"}";

        // When
        eventPublisher.publish(topic, key, payload, Map.of());

        // Then - consume and verify
        // (use Kafka consumer to verify event arrived)
    }
}
```

### 2.4 End-to-End Integration Test

**EndToEndTest.java**

```java
@MicronautTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @Inject CommandBus commandBus;
    @Inject Executor executor;
    @Inject CommandService commandService;
    @Inject OutboxService outboxService;

    @Test
    void fullFlow_apiToWorkerToDatabase() {
        // Given - submit command via CommandBus (simulating API)
        String idempotencyKey = "e2e-test-" + UUID.randomUUID();
        UUID commandId = commandBus.submit("CreateUser", idempotencyKey, "biz-key", "{\"username\":\"e2euser\"}");

        // Then - command should be PENDING
        var cmd = commandService.find(commandId);
        assertThat(cmd).isPresent();
        assertThat(cmd.get().status()).isEqualTo("PENDING");

        // When - worker processes command
        executor.process(commandId, "msg-" + UUID.randomUUID(), "CreateUser", "{\"username\":\"e2euser\"}");

        // Then - command should be SUCCEEDED
        cmd = commandService.find(commandId);
        assertThat(cmd.get().status()).isEqualTo("SUCCEEDED");

        // And - outbox should have reply and event
        List<OutboxRow> outboxEntries = outboxService.claim(100, "test");
        assertThat(outboxEntries).hasSizeGreaterThanOrEqualTo(2); // reply + event
        assertThat(outboxEntries).extracting(OutboxRow::category)
            .contains("reply", "event");
    }

    @Test
    void deduplication_shouldPreventDuplicateProcessing() {
        // Given - process once
        String messageId = "dedup-test-msg";
        UUID commandId = UUID.randomUUID();
        executor.process(commandId, messageId, "CreateUser", "{}");

        // When - try to process again with same message ID
        executor.process(commandId, messageId, "CreateUser", "{}");

        // Then - inbox should have only one entry
        // (verified by inbox service returning 0 on second insert)
    }

    @Test
    void errorHandling_shouldMoveToDlqAfterFailure() { /* ... */ }
}
```

**Run Integration Tests**:

```bash
# Run integration tests (includes Testcontainers)
mvn verify -Pintegration

# Or run specific integration test
mvn test -Dtest=EndToEndTest
```

## Phase 3: E2E Testing Strategy

### 3.1 E2E Test Setup

**E2E tests run against real infrastructure** (Docker Compose):

- PostgreSQL
- IBM MQ
- Kafka
- API (port 8080)
- Worker (port 9090)

**ReliableMessagingE2ETest.java**

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("e2e")
class ReliableMessagingE2ETest {

    private HttpClient apiClient;
    private Connection dbConnection;

    @BeforeAll
    void setUp() throws Exception {
        // Verify infrastructure is running
        waitForApi("http://localhost:8080/health", Duration.ofMinutes(2));
        waitForWorker("http://localhost:9090/health", Duration.ofMinutes(2));

        // Setup HTTP client
        apiClient = HttpClient.newHttpClient();

        // Setup DB connection
        dbConnection = DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/reliable",
            "postgres",
            "postgres"
        );
    }

    @Test
    @Order(1)
    void submitCommand_shouldReturn202Accepted() throws Exception {
        // Given
        String payload = "{\"username\":\"e2euser\"}";
        String idempotencyKey = "e2e-test-" + System.currentTimeMillis();

        // When
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/commands/CreateUser"))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", idempotencyKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = apiClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.headers().firstValue("X-Command-Id")).isPresent();

        String commandId = response.headers().firstValue("X-Command-Id").get();

        // And - wait for processing
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            try (PreparedStatement ps = dbConnection.prepareStatement(
                "SELECT status FROM command WHERE id = ?::uuid")) {
                ps.setString(1, commandId);
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("SUCCEEDED");
            }
        });
    }

    @Test
    @Order(2)
    void submitCommand_shouldPublishToOutbox() throws Exception {
        // Given
        String idempotencyKey = "e2e-outbox-" + System.currentTimeMillis();
        submitCommand("CreateUser", idempotencyKey, "{\"username\":\"outboxtest\"}");

        // When - wait for outbox processing
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            try (PreparedStatement ps = dbConnection.prepareStatement(
                "SELECT category, status FROM outbox WHERE status = 'PUBLISHED' ORDER BY created_at DESC LIMIT 3")) {
                ResultSet rs = ps.executeQuery();

                List<String> categories = new ArrayList<>();
                while (rs.next()) {
                    categories.add(rs.getString("category"));
                }

                // Then - should have command, reply, event
                assertThat(categories).contains("command", "reply", "event");
            }
        });
    }

    @Test
    @Order(3)
    void idempotency_shouldRejectDuplicateKey() throws Exception {
        // Given - submit once
        String idempotencyKey = "e2e-duplicate-" + System.currentTimeMillis();
        HttpResponse<String> response1 = submitCommand("CreateUser", idempotencyKey, "{}");
        assertThat(response1.statusCode()).isEqualTo(202);

        // When - submit duplicate
        HttpResponse<String> response2 = submitCommand("CreateUser", idempotencyKey, "{}");

        // Then - should reject
        assertThat(response2.statusCode()).isEqualTo(409); // Conflict
    }

    @Test
    @Order(4)
    void highLoad_shouldProcessMultipleCommandsInParallel() throws Exception {
        // Given - submit 100 commands
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String key = "e2e-load-" + System.currentTimeMillis() + "-" + i;
            CompletableFuture<HttpResponse<String>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return submitCommand("CreateUser", key, "{\"username\":\"user" + key + "\"}");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // When - wait for all
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Then - all should succeed
        long successCount = futures.stream()
            .map(CompletableFuture::join)
            .filter(r -> r.statusCode() == 202)
            .count();

        assertThat(successCount).isEqualTo(100);

        // And - all should be processed within reasonable time
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            try (PreparedStatement ps = dbConnection.prepareStatement(
                "SELECT COUNT(*) FROM command WHERE status = 'SUCCEEDED' AND idempotency_key LIKE 'e2e-load-%'")) {
                ResultSet rs = ps.executeQuery();
                rs.next();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(100);
            }
        });
    }

    @Test
    @Order(5)
    void errorHandling_shouldMoveToDlqOnFailure() throws Exception {
        // Given - submit command that will fail (invalid payload)
        String key = "e2e-fail-" + System.currentTimeMillis();
        submitCommand("CreateUser", key, "INVALID JSON");

        // When - wait for processing
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            try (PreparedStatement ps = dbConnection.prepareStatement(
                "SELECT COUNT(*) FROM command_dlq WHERE command_name = 'CreateUser'")) {
                ResultSet rs = ps.executeQuery();
                rs.next();
                assertThat(rs.getInt(1)).isGreaterThan(0);
            }
        });
    }

    private HttpResponse<String> submitCommand(String commandName, String idempotencyKey, String payload)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/commands/" + commandName))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", idempotencyKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        return apiClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void waitForApi(String url, Duration timeout) { /* ... */ }
    private void waitForWorker(String url, Duration timeout) { /* ... */ }
}
```

**Run E2E Tests**:

```bash
# Start infrastructure
cd /Users/igormusic/code/ref-app/reliable-messaging
docker-compose up -d

# Wait for services
sleep 40

# Start API
cd /Users/igormusic/code/ref-app/messaging-platform/msg-platform-api
mvn mn:run &
API_PID=$!

# Start Worker
cd /Users/igormusic/code/ref-app/messaging-platform/msg-platform-worker
mvn mn:run &
WORKER_PID=$!

# Wait for startup
sleep 30

# Run E2E tests
cd /Users/igormusic/code/ref-app/messaging-platform
mvn test -Pe2e

# Cleanup
kill $API_PID $WORKER_PID
cd /Users/igormusic/code/ref-app/reliable-messaging
docker-compose down
```

## Phase 4: Static Code Analysis with SonarQube

### 4.1 SonarQube Setup

**Docker Compose for SonarQube**:

```yaml
# sonarqube-docker-compose.yml
version: '3.8'

services:
  sonarqube:
    image: sonarqube:10.3-community
    ports:
      - "9000:9000"
    environment:
      - SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true
    volumes:
      - sonarqube_data:/opt/sonarqube/data
      - sonarqube_extensions:/opt/sonarqube/extensions
      - sonarqube_logs:/opt/sonarqube/logs

volumes:
  sonarqube_data:
  sonarqube_extensions:
  sonarqube_logs:
```

**Start SonarQube**:

```bash
docker-compose -f sonarqube-docker-compose.yml up -d

# Wait for startup
sleep 60

# Login: http://localhost:9000
# Default credentials: admin/admin
# Change password on first login
```

### 4.2 SonarQube Quality Gates

**Create Custom Quality Gate** (via SonarQube UI):

Name: **Messaging Platform Quality Gate**

Conditions:

- **Coverage**: > 90%
- **Duplicated Lines**: < 3%
- **Maintainability Rating**: A
- **Reliability Rating**: A
- **Security Rating**: A
- **Security Hotspots Reviewed**: 100%
- **Code Smells**: < 50
- **Bugs**: 0
- **Vulnerabilities**: 0
- **Technical Debt Ratio**: < 5%

### 4.3 SonarQube Maven Configuration

**Add to parent POM**:

```xml
<properties>
    <!-- SonarQube -->
    <sonar.host.url>http://localhost:9000</sonar.host.url>
    <sonar.projectKey>messaging-platform</sonar.projectKey>
    <sonar.projectName>Messaging Platform</sonar.projectName>
    <sonar.coverage.jacoco.xmlReportPaths>
        ${project.basedir}/msg-platform-core/target/site/jacoco/jacoco.xml,
        ${project.basedir}/msg-platform-api/target/site/jacoco/jacoco.xml,
        ${project.basedir}/msg-platform-worker/target/site/jacoco/jacoco.xml
    </sonar.coverage.jacoco.xmlReportPaths>
    <sonar.qualitygate.wait>true</sonar.qualitygate.wait>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>org.sonarsource.scanner.maven</groupId>
            <artifactId>sonar-maven-plugin</artifactId>
            <version>3.10.0.2594</version>
        </plugin>
    </plugins>
</build>
```

### 4.4 Run SonarQube Analysis

```bash
# Generate project token in SonarQube UI
# Projects > Create Project > Manually
# Generate token: sqp_xxxxxxxxxxxxxxxxxxxxx

# Run analysis
mvn clean verify sonar:sonar \
  -Dsonar.login=sqp_xxxxxxxxxxxxxxxxxxxxx

# Check results at http://localhost:9000
```

### 4.5 SonarQube Quality Gate Enforcement

**Add to CI/CD pipeline**:

```bash
#!/bin/bash
set -e

# Run tests with coverage
mvn clean verify

# Run SonarQube analysis
mvn sonar:sonar -Dsonar.login=$SONAR_TOKEN

# Check quality gate status
QUALITY_GATE=$(curl -s -u $SONAR_TOKEN: \
  "http://localhost:9000/api/qualitygates/project_status?projectKey=messaging-platform" \
  | jq -r '.projectStatus.status')

if [ "$QUALITY_GATE" != "OK" ]; then
  echo "Quality gate FAILED: $QUALITY_GATE"
  exit 1
fi

echo "Quality gate PASSED"
```

## Phase 5: Test Execution Plan

### 5.1 Local Development Testing

```bash
# Quick unit tests (developers run frequently)
mvn test -DskipIntegrationTests

# Full test suite (before commit)
mvn verify

# E2E tests (before PR)
./scripts/run-e2e-tests.sh
```

### 5.2 CI/CD Pipeline Testing

**GitHub Actions / Jenkins Pipeline**:

```yaml
name: Test Pipeline

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run unit tests
        run: mvn clean test
      - name: Upload coverage
        uses: codecov/codecov-action@v3

  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - name: Run integration tests
        run: mvn verify -Pintegration

  e2e-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - name: Start infrastructure
        run: docker-compose up -d
      - name: Wait for services
        run: sleep 60
      - name: Run E2E tests
        run: mvn test -Pe2e
      - name: Cleanup
        run: docker-compose down

  sonarqube:
    runs-on: ubuntu-latest
    needs: [unit-tests, integration-tests]
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - name: Run SonarQube analysis
        run: |
          mvn clean verify sonar:sonar \
            -Dsonar.login=${{ secrets.SONAR_TOKEN }}
```

### 5.3 Test Reporting

**Generate Aggregated Report**:

```bash
# Run all tests
mvn clean verify

# Generate aggregate JaCoCo report
mvn jacoco:report-aggregate

# Generate HTML test report
mvn surefire-report:report

# View reports
open target/site/jacoco-aggregate/index.html
open target/site/surefire-report.html
```

## Phase 6: Performance Testing

### 6.1 Load Tests with Assertions

**LoadTest.java**

```java
@Tag("performance")
class LoadTest {

    @Test
    void highThroughput_shouldHandle1000TPS() throws Exception {
        // Given
        int targetTPS = 1000;
        int durationSeconds = 60;
        int totalRequests = targetTPS * durationSeconds;

        // When - submit requests at target TPS
        long startTime = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(50);
        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    HttpResponse<String> response = submitCommand(
                        "CreateUser",
                        "load-test-" + UUID.randomUUID(),
                        "{}"
                    );
                    if (response.statusCode() == 202) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            });

            // Rate limiting
            Thread.sleep(1000 / targetTPS);
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);
        long endTime = System.currentTimeMillis();

        // Then - assertions
        double actualTPS = (double) totalRequests / ((endTime - startTime) / 1000.0);
        double successRate = (double) successCount.get() / totalRequests;

        assertThat(actualTPS).isGreaterThan(targetTPS * 0.95); // Within 5% of target
        assertThat(successRate).isGreaterThan(0.99); // >99% success rate
        assertThat(failureCount.get()).isLessThan(totalRequests * 0.01); // <1% failures
    }

    @Test
    void latency_shouldMeetSLA() throws Exception {
        // Test P95 < 100ms, P99 < 200ms
    }
}
```

## Summary Checklist

### Unit Testing

- [ ] CommandRepository tests (100% coverage)
- [ ] InboxRepository tests (100% coverage)
- [ ] OutboxRepository tests (100% coverage)
- [ ] DlqRepository tests (100% coverage)
- [ ] CommandService tests (100% coverage)
- [ ] InboxService tests (100% coverage)
- [ ] OutboxService tests (100% coverage)
- [ ] DlqService tests (100% coverage)
- [ ] CommandBus tests (>90% coverage)
- [ ] Executor tests (>90% coverage)
- [ ] Outbox tests (>90% coverage)
- [ ] OutboxRelay tests (>90% coverage)
- [ ] CommandController tests (100% coverage)
- [ ] CommandConsumers tests (100% coverage)
- [ ] Handler tests (100% coverage)
- [ ] JaCoCo coverage >90% code, >90% branch

### Integration Testing

- [ ] Database integration tests
- [ ] JMS integration tests
- [ ] Kafka integration tests
- [ ] Transactional integration tests
- [ ] Testcontainers setup

### E2E Testing

- [ ] API submission test
- [ ] Worker processing test
- [ ] Outbox publishing test
- [ ] Idempotency test
- [ ] High load test
- [ ] Error handling test
- [ ] Infrastructure setup script

### Static Analysis

- [ ] SonarQube running
- [ ] Quality gate configured
- [ ] Coverage integrated
- [ ] Quality gate passing
- [ ] Zero critical issues

### Performance Testing

- [ ] Load tests (1000+ TPS)
- [ ] Latency tests (P95/P99)
- [ ] Throughput assertions
- [ ] Resource utilization tests

### CI/CD

- [ ] Pipeline configured
- [ ] All tests running
- [ ] Coverage reporting
- [ ] Quality gate enforcement
- [ ] Test reports generated

## Execution Commands

```bash
# 1. Unit tests only
mvn clean test

# 2. Unit + Integration tests
mvn clean verify

# 3. E2E tests
mvn test -Pe2e

# 4. All tests + SonarQube
mvn clean verify sonar:sonar -Dsonar.login=$SONAR_TOKEN

# 5. Performance tests
mvn test -Pperformance

# 6. Generate reports
mvn site

# 7. Check coverage threshold
mvn verify # Fails if <90% coverage
```

## Success Criteria

✅ **Unit Testing**: >90% code coverage, >90% branch coverage
✅ **Integration Testing**: All database/JMS/Kafka tests passing
✅ **E2E Testing**: Full flow tests passing
✅ **Static Analysis**: SonarQube quality gate passing
✅ **Performance**: 1000+ TPS with <1% failures, P95 < 100ms
✅ **Zero Bugs**: No critical/blocker issues in SonarQube
✅ **Zero Vulnerabilities**: No security vulnerabilities detected

---

**Document Version**: 1.0
**Created**: 2025-11-02
**Status**: Ready for Execution
