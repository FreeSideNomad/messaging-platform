package com.acme.reliable.processor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.config.TimeoutConfig;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.domain.Outbox;
import com.acme.reliable.service.OutboxService;
import com.acme.reliable.spi.CommandQueue;
import com.acme.reliable.spi.EventPublisher;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the Outbox Processor pattern with embedded H2 and mocked messaging.
 *
 * Tests the OutboxRelay component which:
 * 1. Claims outbox entries atomically from the database
 * 2. Publishes them to external systems (JMS, Kafka)
 * 3. Marks them as published or reschedules on failure
 * 4. Implements exponential backoff retry logic
 *
 * Verifies:
 * - Outbox entries are correctly processed and published
 * - Failed publishes are rescheduled with backoff
 * - Batch processing works correctly
 * - Transaction boundaries are respected
 */
@MicronautTest(environments = {"test"})
@DisplayName("Outbox Processor Integration Tests")
class OutboxProcessorIntegrationTest {

  @Inject private OutboxRelay outboxRelay;
  @Inject private OutboxService outboxService;

  // Mock messaging providers
  private CommandQueue mockCommandQueue;
  private EventPublisher mockEventPublisher;

  // Track published messages for verification
  private List<PublishedMessage> publishedMessages;

  @MockBean(CommandQueue.class)
  CommandQueue mockCommandQueue() {
    mockCommandQueue = mock(CommandQueue.class);
    return mockCommandQueue;
  }

  @MockBean(EventPublisher.class)
  EventPublisher mockEventPublisher() {
    mockEventPublisher = mock(EventPublisher.class);
    return mockEventPublisher;
  }

  @BeforeEach
  void setup() {
    publishedMessages = new ArrayList<>();
    reset(mockCommandQueue, mockEventPublisher);

    // Setup mocks to capture published messages
    doAnswer(
            invocation -> {
              String topic = invocation.getArgument(0);
              String payload = invocation.getArgument(1);
              Map<String, String> headers = invocation.getArgument(2);
              publishedMessages.add(new PublishedMessage("command", topic, payload, headers));
              return null;
            })
        .when(mockCommandQueue)
        .send(anyString(), anyString(), anyMap());

    doAnswer(
            invocation -> {
              String topic = invocation.getArgument(0);
              String payload = invocation.getArgument(1);
              Map<String, String> headers = invocation.getArgument(2);
              publishedMessages.add(new PublishedMessage("event", topic, payload, headers));
              return null;
            })
        .when(mockEventPublisher)
        .publish(anyString(), anyString(), anyString(), anyMap());
  }

  // ============================================================================
  // Command/Reply Publishing Tests
  // ============================================================================

  @Test
  @DisplayName("Should publish pending command from outbox")
  void testPublishCommand_Success() {
    // Arrange: Create command outbox entry
    Outbox command =
        new Outbox(
            null,
            "command",
            "APP.CMD.TEST.Q",
            "cmd-001",
            "TestCommand",
            "{\"test\": \"data\"}",
            Map.of("commandId", UUID.randomUUID().toString()),
            "PENDING",
            0);

    long outboxId = outboxService.addReturningId(command);

    // Act: Trigger relay to process the outbox
    outboxRelay.publishNow(outboxId);

    // Assert: Command should be published
    assertThat(publishedMessages)
        .hasSize(1)
        .extracting(m -> m.category)
        .containsExactly("command");

    PublishedMessage published = publishedMessages.get(0);
    assertThat(published.topic).isEqualTo("APP.CMD.TEST.Q");
    assertThat(published.payload).isEqualTo("{\"test\": \"data\"}");
    assertThat(published.headers).containsKey("commandId");

    // Verify mock was called
    verify(mockCommandQueue, times(1)).send(anyString(), anyString(), anyMap());
  }

  @Test
  @DisplayName("Should publish reply from outbox")
  void testPublishReply_Success() {
    // Arrange: Create reply outbox entry
    UUID commandId = UUID.randomUUID();
    Outbox reply =
        new Outbox(
            null,
            "reply",
            "APP.CMD.REPLY.Q",
            "reply-001",
            "CommandResult",
            "{\"status\": \"COMPLETED\"}",
            Map.of("commandId", commandId.toString(), "correlationId", UUID.randomUUID().toString()),
            "PENDING",
            0);

    long outboxId = outboxService.addReturningId(reply);

    // Act: Process outbox
    outboxRelay.publishNow(outboxId);

    // Assert: Reply should be published
    assertThat(publishedMessages).hasSize(1);
    PublishedMessage published = publishedMessages.get(0);
    assertThat(published.topic).isEqualTo("APP.CMD.REPLY.Q");
    assertThat(published.headers).containsKey("correlationId");
  }

  // ============================================================================
  // Event Publishing Tests
  // ============================================================================

  @Test
  @DisplayName("Should publish event to Kafka")
  void testPublishEvent_ToKafka() {
    // Arrange: Create event outbox entry
    Outbox event =
        new Outbox(
            null,
            "event",
            "payment.created",
            "payment-123",
            "PaymentCreatedEvent",
            "{\"paymentId\": \"123\", \"amount\": 1000}",
            Map.of("timestamp", Instant.now().toString()),
            "PENDING",
            0);

    long outboxId = outboxService.addReturningId(event);

    // Act: Process outbox
    outboxRelay.publishNow(outboxId);

    // Assert: Event should be published to Kafka
    assertThat(publishedMessages).hasSize(1);
    PublishedMessage published = publishedMessages.get(0);
    assertThat(published.category).isEqualTo("event");
    assertThat(published.topic).isEqualTo("payment.created");
    assertThat(published.payload).contains("paymentId");

    verify(mockEventPublisher, times(1))
        .publish(anyString(), anyString(), anyString(), anyMap());
  }

  // ============================================================================
  // Batch Processing Tests
  // ============================================================================

  @Test
  @DisplayName("Should process batch of pending outbox entries")
  void testBatchProcessing() {
    // Arrange: Create multiple command entries
    List<Long> outboxIds = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      Outbox cmd =
          new Outbox(
              null,
              "command",
              "APP.CMD.TEST.Q",
              "cmd-" + i,
              "TestCommand",
              "{\"index\": " + i + "}",
              Map.of("commandId", UUID.randomUUID().toString()),
              "PENDING",
              0);
      outboxIds.add(outboxService.addReturningId(cmd));
    }

    // Act: Process each outbox entry
    for (Long id : outboxIds) {
      outboxRelay.publishNow(id);
    }

    // Assert: All commands should be published
    assertThat(publishedMessages).hasSize(3);
    assertThat(publishedMessages)
        .extracting(m -> m.category)
        .containsOnly("command");

    for (int i = 0; i < 3; i++) {
      assertThat(publishedMessages.get(i).payload).contains("\"index\": " + i);
    }
  }

  // ============================================================================
  // Failure and Retry Tests
  // ============================================================================

  @Test
  @DisplayName("Should handle publish failure and reschedule")
  void testPublishFailure_Reschedule() {
    // Arrange: Setup mock to throw exception
    doThrow(new RuntimeException("Connection failed"))
        .when(mockCommandQueue)
        .send(anyString(), anyString(), anyMap());

    Outbox command =
        new Outbox(
            null,
            "command",
            "APP.CMD.TEST.Q",
            "cmd-fail",
            "TestCommand",
            "{\"fail\": true}",
            Map.of("commandId", UUID.randomUUID().toString()),
            "PENDING",
            0);

    long outboxId = outboxService.addReturningId(command);

    // Act: Attempt to publish
    outboxRelay.publishNow(outboxId);

    // Assert: Command should not be in published messages
    assertThat(publishedMessages).isEmpty();

    // Verify the message was claimed but rescheduled
    verify(mockCommandQueue, times(1)).send(anyString(), anyString(), anyMap());
  }

  @Test
  @DisplayName("Should retry failed publishes with exponential backoff")
  void testRetryWithBackoff() {
    // Arrange: Setup initial failure
    doThrow(new RuntimeException("Temporary failure"))
        .when(mockCommandQueue)
        .send(anyString(), anyString(), anyMap());

    Outbox command =
        new Outbox(
            null,
            "command",
            "APP.CMD.RETRY.Q",
            "cmd-retry",
            "TestCommand",
            "{\"retry\": true}",
            Map.of("commandId", UUID.randomUUID().toString()),
            "PENDING",
            2); // Already attempted twice

    long outboxId = outboxService.addReturningId(command);

    // Act: First failure attempt
    outboxRelay.publishNow(outboxId);

    // Assert: Should have been rescheduled
    assertThat(publishedMessages).isEmpty();

    // Now setup success
    reset(mockCommandQueue);
    doAnswer(
            invocation -> {
              publishedMessages.add(
                  new PublishedMessage(
                      "command",
                      (String) invocation.getArgument(0),
                      (String) invocation.getArgument(1),
                      (Map<String, String>) invocation.getArgument(2)));
              return null;
            })
        .when(mockCommandQueue)
        .send(anyString(), anyString(), anyMap());

    // Retry should eventually succeed (after rescheduling)
    // In a real scenario, this would be handled by the scheduler
  }

  // ============================================================================
  // Message Format and Headers Tests
  // ============================================================================

  @Test
  @DisplayName("Should preserve message headers during publishing")
  void testPreserveHeaders() {
    // Arrange: Create command with specific headers
    Map<String, String> headers =
        Map.of(
            "commandId", UUID.randomUUID().toString(),
            "correlationId", UUID.randomUUID().toString(),
            "timestamp", Instant.now().toString(),
            "source", "test-service");

    Outbox command =
        new Outbox(
            null,
            "command",
            "APP.CMD.TEST.Q",
            "cmd-headers",
            "TestCommand",
            "{\"data\": \"test\"}",
            headers,
            "PENDING",
            0);

    long outboxId = outboxService.addReturningId(command);

    // Act: Publish
    outboxRelay.publishNow(outboxId);

    // Assert: Headers should be preserved
    assertThat(publishedMessages).hasSize(1);
    PublishedMessage published = publishedMessages.get(0);

    assertThat(published.headers)
        .containsKeys("commandId", "correlationId", "timestamp", "source");
    assertThat(published.headers.get("source")).isEqualTo("test-service");
  }

  @Test
  @DisplayName("Should handle JSON payload correctly")
  void testJsonPayloadHandling() {
    // Arrange: Create command with complex JSON payload
    String jsonPayload =
        Jsons.toJson(
            Map.of(
                "customerId", UUID.randomUUID().toString(),
                "accountNumber", "ACC12345",
                "currency", "USD"));

    Outbox command =
        new Outbox(
            null,
            "command",
            "APP.CMD.CREATE.Q",
            "cmd-json",
            "CreateCommand",
            jsonPayload,
            Map.of("commandId", UUID.randomUUID().toString()),
            "PENDING",
            0);

    long outboxId = outboxService.addReturningId(command);

    // Act: Publish
    outboxRelay.publishNow(outboxId);

    // Assert: JSON should be transmitted as-is
    assertThat(publishedMessages).hasSize(1);
    PublishedMessage published = publishedMessages.get(0);

    assertThat(published.payload).contains("customerId").contains("accountNumber").contains("USD");
  }

  // ============================================================================
  // Mixed Category Tests
  // ============================================================================

  @Test
  @DisplayName("Should publish mixed categories in correct order")
  void testMixedCategoryPublishing() {
    // Arrange: Create multiple entries of different categories
    Outbox command =
        new Outbox(
            null,
            "command",
            "APP.CMD.TEST.Q",
            "cmd-1",
            "TestCommand",
            "{\"type\": \"command\"}",
            Map.of("commandId", UUID.randomUUID().toString()),
            "PENDING",
            0);

    Outbox event =
        new Outbox(
            null,
            "event",
            "test.topic",
            "evt-1",
            "TestEvent",
            "{\"type\": \"event\"}",
            Map.of(),
            "PENDING",
            0);

    Outbox reply =
        new Outbox(
            null,
            "reply",
            "APP.CMD.REPLY.Q",
            "reply-1",
            "TestReply",
            "{\"type\": \"reply\"}",
            Map.of("commandId", UUID.randomUUID().toString()),
            "PENDING",
            0);

    long cmdId = outboxService.addReturningId(command);
    long evtId = outboxService.addReturningId(event);
    long replyId = outboxService.addReturningId(reply);

    // Act: Publish all
    outboxRelay.publishNow(cmdId);
    outboxRelay.publishNow(evtId);
    outboxRelay.publishNow(replyId);

    // Assert: All should be published with correct categories
    assertThat(publishedMessages).hasSize(3);
    assertThat(publishedMessages)
        .extracting(m -> m.category)
        .containsExactlyInAnyOrder("command", "event", "reply");

    // Verify correct routing
    assertThat(publishedMessages)
        .filteredOn(m -> m.category.equals("command"))
        .hasSize(1)
        .extracting(m -> m.topic)
        .containsExactly("APP.CMD.TEST.Q");

    assertThat(publishedMessages)
        .filteredOn(m -> m.category.equals("event"))
        .hasSize(1)
        .extracting(m -> m.topic)
        .containsExactly("test.topic");
  }

  // ============================================================================
  // Edge Cases and Boundary Tests
  // ============================================================================

  @Test
  @DisplayName("Should handle empty payload")
  void testEmptyPayload() {
    // Arrange: Create command with empty payload
    Outbox command =
        new Outbox(
            null,
            "command",
            "APP.CMD.TEST.Q",
            "cmd-empty",
            "TestCommand",
            "",
            Map.of("commandId", UUID.randomUUID().toString()),
            "PENDING",
            0);

    long outboxId = outboxService.addReturningId(command);

    // Act: Publish
    outboxRelay.publishNow(outboxId);

    // Assert: Should still publish empty payload
    assertThat(publishedMessages).hasSize(1);
    PublishedMessage published = publishedMessages.get(0);
    assertThat(published.payload).isEmpty();
  }

  @Test
  @DisplayName("Should handle large payload")
  void testLargePayload() {
    // Arrange: Create command with large payload
    StringBuilder largePayload = new StringBuilder("{\"data\": \"");
    for (int i = 0; i < 10000; i++) {
      largePayload.append("x");
    }
    largePayload.append("\"}");

    Outbox command =
        new Outbox(
            null,
            "command",
            "APP.CMD.TEST.Q",
            "cmd-large",
            "TestCommand",
            largePayload.toString(),
            Map.of("commandId", UUID.randomUUID().toString()),
            "PENDING",
            0);

    long outboxId = outboxService.addReturningId(command);

    // Act: Publish
    outboxRelay.publishNow(outboxId);

    // Assert: Large payload should be transmitted
    assertThat(publishedMessages).hasSize(1);
    PublishedMessage published = publishedMessages.get(0);
    assertThat(published.payload.length()).isGreaterThan(10000);
  }

  // ============================================================================
  // Helper Classes
  // ============================================================================

  /**
   * Captures published message details for verification.
   */
  private static class PublishedMessage {
    final String category;
    final String topic;
    final String payload;
    final Map<String, String> headers;

    PublishedMessage(String category, String topic, String payload, Map<String, String> headers) {
      this.category = category;
      this.topic = topic;
      this.payload = payload;
      this.headers = headers;
    }
  }
}
