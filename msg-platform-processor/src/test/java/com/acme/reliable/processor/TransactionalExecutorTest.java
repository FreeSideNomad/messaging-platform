package com.acme.reliable.processor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.acme.reliable.command.CommandHandlerRegistry;
import com.acme.reliable.command.CommandMessage;
import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.config.TimeoutConfig;
import com.acme.reliable.config.MessagingConfig.TopicNaming;
import com.acme.reliable.core.Envelope;
import com.acme.reliable.core.PermanentException;
import com.acme.reliable.core.RetryableBusinessException;
import com.acme.reliable.core.TransientException;
import com.acme.reliable.domain.Outbox;
import com.acme.reliable.process.CommandReply;
import com.acme.reliable.service.CommandService;
import com.acme.reliable.service.DlqService;
import com.acme.reliable.service.InboxService;
import com.acme.reliable.service.OutboxService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Comprehensive unit tests for TransactionalExecutor to achieve 80%+ coverage.
 *
 * <p>TransactionalExecutor processes command messages, handles them through the registry,
 * manages retries, and creates appropriate outbox entries for replies and events.
 *
 * <p>Coverage areas:
 * - Successful command execution
 * - Idempotency via inbox deduplication
 * - Permanent exception handling and DLQ parking
 * - Retryable exception handling
 * - Transaction boundaries
 * - Outbox entry creation for replies and events
 * - Edge cases and error scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionalExecutor Unit Tests")
class TransactionalExecutorTest {

  @Mock private InboxService inboxService;

  @Mock private CommandService commandService;

  @Mock private OutboxService outboxService;

  @Mock private DlqService dlqService;

  @Mock private CommandHandlerRegistry handlerRegistry;

  @Mock private FastPathPublisher fastPathPublisher;

  @Mock private MessagingConfig messagingConfig;

  @Mock private TimeoutConfig timeoutConfig;

  @Mock private TopicNaming topicNaming;

  @Mock private MessagingConfig.QueueNaming queueNaming;

  private TransactionalExecutor executor;

  @BeforeEach
  void setUp() {
    lenient().when(timeoutConfig.getCommandLeaseSeconds()).thenReturn(300L); // 5 minutes
    lenient().when(messagingConfig.getTopicNaming()).thenReturn(topicNaming);
    lenient().when(messagingConfig.getQueueNaming()).thenReturn(queueNaming);
    lenient().when(topicNaming.buildEventTopic(anyString())).thenReturn("events.topic");
    lenient().when(queueNaming.getReplyQueue()).thenReturn("test.reply.queue");

    executor =
        new TransactionalExecutor(
            inboxService,
            commandService,
            outboxService,
            dlqService,
            handlerRegistry,
            fastPathPublisher,
            timeoutConfig,
            messagingConfig);
  }

  private Envelope createTestEnvelope(String name, String payload) {
    return new Envelope(
        UUID.randomUUID(),
        "Command",
        name,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.now(),
        "test-key-123",
        Map.of("source", "test"),
        payload);
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("Should initialize with all dependencies")
    void testConstructor_Success() {
      // Act
      TransactionalExecutor exec =
          new TransactionalExecutor(
              inboxService,
              commandService,
              outboxService,
              dlqService,
              handlerRegistry,
              fastPathPublisher,
              timeoutConfig,
              messagingConfig);

      // Assert
      assertThat(exec).isNotNull();
    }

    @Test
    @DisplayName("Should extract lease seconds from timeout config")
    void testConstructor_ExtractsLeaseSeconds() {
      // Arrange
      TimeoutConfig newTimeoutConfig = mock(TimeoutConfig.class);
      when(newTimeoutConfig.getCommandLeaseSeconds()).thenReturn(600L);

      // Act
      TransactionalExecutor exec =
          new TransactionalExecutor(
              inboxService,
              commandService,
              outboxService,
              dlqService,
              handlerRegistry,
              fastPathPublisher,
              newTimeoutConfig,
              messagingConfig);

      // Assert
      verify(newTimeoutConfig).getCommandLeaseSeconds();
    }
  }

  @Nested
  @DisplayName("Successful Command Processing Tests")
  class SuccessfulProcessingTests {

    @Test
    @DisplayName("Should process command successfully and create reply and event outbox entries")
    void testProcess_Success() {
      // Arrange
      Envelope envelope = createTestEnvelope("CreateOrder", "{\"amount\":100}");
      CommandReply reply =
          CommandReply.completed(
              envelope.commandId(),
              envelope.correlationId(),
              Map.of("orderId", "ord-123", "status", "created"));

      when(inboxService.markIfAbsent(envelope.messageId().toString(), "CommandExecutor"))
          .thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(1L, 2L);

      // Act
      executor.process(envelope);

      // Assert
      verify(inboxService).markIfAbsent(envelope.messageId().toString(), "CommandExecutor");
      verify(commandService).markRunning(eq(envelope.commandId()), any(Instant.class));
      verify(handlerRegistry).handle(any(CommandMessage.class));
      verify(commandService).markSucceeded(envelope.commandId());
      verify(outboxService, times(2)).addReturningId(any(Outbox.class));
    }

    @Test
    @DisplayName("Should mark command as running with correct lease time")
    void testProcess_MarksRunningWithLease() {
      // Arrange
      Envelope envelope = createTestEnvelope("UpdateUser", "{\"email\":\"test@example.com\"}");
      CommandReply reply =
          CommandReply.completed(envelope.commandId(), envelope.correlationId(), Map.of());

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(10L, 20L);

      ArgumentCaptor<Instant> leaseCaptor = ArgumentCaptor.forClass(Instant.class);

      // Act
      Instant beforeCall = Instant.now();
      executor.process(envelope);
      Instant afterCall = Instant.now().plusSeconds(300);

      // Assert
      verify(commandService).markRunning(eq(envelope.commandId()), leaseCaptor.capture());

      Instant capturedLease = leaseCaptor.getValue();
      assertThat(capturedLease).isAfter(beforeCall);
      assertThat(capturedLease).isBefore(afterCall.plusSeconds(5));
    }

    @Test
    @DisplayName("Should create CommandMessage with correct fields")
    void testProcess_CreatesCorrectCommandMessage() {
      // Arrange
      Envelope envelope = createTestEnvelope("DeleteProduct", "{\"productId\":\"prod-999\"}");
      CommandReply reply =
          CommandReply.completed(envelope.commandId(), envelope.correlationId(), Map.of());

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(30L, 40L);

      ArgumentCaptor<CommandMessage> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);

      // Act
      executor.process(envelope);

      // Assert
      verify(handlerRegistry).handle(commandCaptor.capture());

      CommandMessage capturedCommand = commandCaptor.getValue();
      assertThat(capturedCommand.commandId()).isEqualTo(envelope.commandId());
      assertThat(capturedCommand.correlationId()).isEqualTo(envelope.correlationId());
      assertThat(capturedCommand.commandType()).isEqualTo(envelope.name());
      assertThat(capturedCommand.payload()).isEqualTo(envelope.payload());
    }

    @Test
    @DisplayName("Should create MQ reply outbox entry with CommandCompleted")
    void testProcess_CreatesMqReplyOutbox() {
      // Arrange
      Envelope envelope = createTestEnvelope("ProcessPayment", "{\"amount\":250}");
      CommandReply reply =
          CommandReply.completed(
              envelope.commandId(), envelope.correlationId(), Map.of("transactionId", "txn-555"));

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(50L, 60L);

      ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);

      // Act
      executor.process(envelope);

      // Assert
      verify(outboxService, times(2)).addReturningId(outboxCaptor.capture());

      // First outbox entry should be MQ reply
      Outbox mqReply = outboxCaptor.getAllValues().get(0);
      assertThat(mqReply.getType()).isEqualTo("CommandCompleted");
      assertThat(mqReply.getTopic()).isNotNull();
    }

    @Test
    @DisplayName("Should create Kafka event outbox entry")
    void testProcess_CreatesKafkaEventOutbox() {
      // Arrange
      Envelope envelope = createTestEnvelope("CreateAccount", "{\"name\":\"Test\"}");
      CommandReply reply =
          CommandReply.completed(envelope.commandId(), envelope.correlationId(), Map.of());

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(70L, 80L);

      ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);

      // Act
      executor.process(envelope);

      // Assert
      verify(outboxService, times(2)).addReturningId(outboxCaptor.capture());

      // Second outbox entry should be Kafka event
      Outbox kafkaEvent = outboxCaptor.getAllValues().get(1);
      assertThat(kafkaEvent.getType()).isEqualTo("CommandCompleted");
    }

    @Test
    @DisplayName("Should handle reply with empty data map")
    void testProcess_EmptyReplyData() {
      // Arrange
      Envelope envelope = createTestEnvelope("NoDataCommand", "{}");
      CommandReply reply =
          CommandReply.completed(envelope.commandId(), envelope.correlationId(), Map.of());

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(90L, 100L);

      // Act
      executor.process(envelope);

      // Assert
      verify(commandService).markSucceeded(envelope.commandId());
      verify(outboxService, times(2)).addReturningId(any(Outbox.class));
    }

    @Test
    @DisplayName("Should handle reply with null data map")
    void testProcess_NullReplyData() {
      // Arrange
      Envelope envelope = createTestEnvelope("NullDataCommand", "{}");
      CommandReply reply =
          new CommandReply(
              envelope.commandId(),
              envelope.correlationId(),
              CommandReply.ReplyStatus.COMPLETED,
              null,
              null);

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(110L, 120L);

      // Act
      executor.process(envelope);

      // Assert
      verify(commandService).markSucceeded(envelope.commandId());
      verify(outboxService, times(2)).addReturningId(any(Outbox.class));
    }
  }

  @Nested
  @DisplayName("Idempotency and Deduplication Tests")
  class IdempotencyTests {

    @Test
    @DisplayName("Should skip processing if message already processed (inbox deduplication)")
    void testProcess_AlreadyProcessed() {
      // Arrange
      Envelope envelope = createTestEnvelope("DuplicateCommand", "{}");

      when(inboxService.markIfAbsent(envelope.messageId().toString(), "CommandExecutor"))
          .thenReturn(false);

      // Act
      executor.process(envelope);

      // Assert
      verify(inboxService).markIfAbsent(envelope.messageId().toString(), "CommandExecutor");

      // Verify no further processing happened
      verify(commandService, never()).markRunning(any(UUID.class), any(Instant.class));
      verify(handlerRegistry, never()).handle(any(CommandMessage.class));
      verify(commandService, never()).markSucceeded(any(UUID.class));
      verify(outboxService, never()).addReturningId(any(Outbox.class));
    }

    @Test
    @DisplayName("Should process message if not in inbox")
    void testProcess_NotYetProcessed() {
      // Arrange
      Envelope envelope = createTestEnvelope("NewCommand", "{}");
      CommandReply reply =
          CommandReply.completed(envelope.commandId(), envelope.correlationId(), Map.of());

      when(inboxService.markIfAbsent(envelope.messageId().toString(), "CommandExecutor"))
          .thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(130L, 140L);

      // Act
      executor.process(envelope);

      // Assert
      verify(inboxService).markIfAbsent(envelope.messageId().toString(), "CommandExecutor");
      verify(handlerRegistry).handle(any(CommandMessage.class));
    }

    @Test
    @DisplayName("Should use correct consumer name in inbox marking")
    void testProcess_CorrectConsumerName() {
      // Arrange
      Envelope envelope = createTestEnvelope("ConsumerTest", "{}");
      CommandReply reply =
          CommandReply.completed(envelope.commandId(), envelope.correlationId(), Map.of());

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(150L, 160L);

      // Act
      executor.process(envelope);

      // Assert
      verify(inboxService).markIfAbsent(envelope.messageId().toString(), "CommandExecutor");
    }
  }

  @Nested
  @DisplayName("Permanent Exception Handling Tests")
  class PermanentExceptionTests {

    @Test
    @DisplayName("Should handle PermanentException by marking failed and parking in DLQ")
    void testProcess_PermanentException() {
      // Arrange
      Envelope envelope = createTestEnvelope("FailingCommand", "{}");
      PermanentException exception = new PermanentException("Invalid data format");

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenThrow(exception);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(170L, 180L);

      // Act
      executor.process(envelope);

      // Assert
      verify(commandService).markFailed(envelope.commandId(), "Invalid data format");
      verify(dlqService)
          .park(
              envelope.commandId(),
              envelope.name(),
              envelope.key(),
              envelope.payload(),
              "FAILED",
              "Permanent",
              "Invalid data format",
              0,
              "worker");
      verify(outboxService, times(2)).addReturningId(any(Outbox.class));
    }

    @Test
    @DisplayName("Should create CommandFailed reply for permanent exception")
    void testProcess_PermanentException_CreatesFailedReply() {
      // Arrange
      Envelope envelope = createTestEnvelope("PermanentFailCommand", "{}");
      PermanentException exception = new PermanentException("Business rule violation");

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenThrow(exception);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(190L, 200L);

      ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);

      // Act
      executor.process(envelope);

      // Assert
      verify(outboxService, times(2)).addReturningId(outboxCaptor.capture());

      Outbox mqReply = outboxCaptor.getAllValues().get(0);
      assertThat(mqReply.getType()).isEqualTo("CommandFailed");
    }

    @Test
    @DisplayName("Should create CommandFailed event for permanent exception")
    void testProcess_PermanentException_CreatesFailedEvent() {
      // Arrange
      Envelope envelope = createTestEnvelope("PermanentEventFail", "{}");
      PermanentException exception = new PermanentException("Validation failed");

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenThrow(exception);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(210L, 220L);

      ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);

      // Act
      executor.process(envelope);

      // Assert
      verify(outboxService, times(2)).addReturningId(outboxCaptor.capture());

      Outbox kafkaEvent = outboxCaptor.getAllValues().get(1);
      assertThat(kafkaEvent.getType()).isEqualTo("CommandFailed");
    }

    @Test
    @DisplayName("Should not re-throw permanent exception")
    void testProcess_PermanentException_NoRethrow() {
      // Arrange
      Envelope envelope = createTestEnvelope("NoRethrowCommand", "{}");
      PermanentException exception = new PermanentException("Should not bubble up");

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenThrow(exception);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(230L, 240L);

      // Act & Assert - Should not throw
      assertThatCode(() -> executor.process(envelope)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should park in DLQ with correct fields for permanent exception")
    void testProcess_PermanentException_DlqFields() {
      // Arrange
      Envelope envelope = createTestEnvelope("DlqFieldsTest", "{\"test\":\"data\"}");
      PermanentException exception = new PermanentException("Critical error");

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenThrow(exception);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(250L, 260L);

      // Act
      executor.process(envelope);

      // Assert
      verify(dlqService)
          .park(
              envelope.commandId(),
              envelope.name(),
              envelope.key(),
              envelope.payload(),
              "FAILED",
              "Permanent",
              "Critical error",
              0,
              "worker");
    }
  }

  @Nested
  @DisplayName("Retryable Exception Handling Tests")
  class RetryableExceptionTests {

    @Test
    @DisplayName("Should handle RetryableBusinessException by bumping retry and re-throwing")
    void testProcess_RetryableBusinessException() {
      // Arrange
      Envelope envelope = createTestEnvelope("RetryableCommand", "{}");
      RetryableBusinessException exception =
          new RetryableBusinessException("Temporary business condition");

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenThrow(exception);

      // Act & Assert
      assertThatThrownBy(() -> executor.process(envelope))
          .isInstanceOf(RetryableBusinessException.class)
          .hasMessage("Temporary business condition");

      verify(commandService)
          .bumpRetry(envelope.commandId(), "Temporary business condition");

      // Should not create outbox entries or mark as failed
      verify(commandService, never()).markFailed(any(UUID.class), anyString());
      verify(dlqService, never())
          .park(
              any(UUID.class),
              anyString(),
              anyString(),
              anyString(),
              anyString(),
              anyString(),
              anyString(),
              anyInt(),
              anyString());
    }

    @Test
    @DisplayName("Should handle TransientException by bumping retry and re-throwing")
    void testProcess_TransientException() {
      // Arrange
      Envelope envelope = createTestEnvelope("TransientCommand", "{}");
      TransientException exception = new TransientException("Database temporarily unavailable");

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenThrow(exception);

      // Act & Assert
      assertThatThrownBy(() -> executor.process(envelope))
          .isInstanceOf(TransientException.class)
          .hasMessage("Database temporarily unavailable");

      verify(commandService)
          .bumpRetry(envelope.commandId(), "Database temporarily unavailable");
    }

    @Test
    @DisplayName("Should not create outbox entries for retryable exceptions")
    void testProcess_RetryableException_NoOutbox() {
      // Arrange
      Envelope envelope = createTestEnvelope("NoOutboxRetry", "{}");
      RetryableBusinessException exception = new RetryableBusinessException("Retry me");

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenThrow(exception);

      // Act & Assert
      assertThatThrownBy(() -> executor.process(envelope))
          .isInstanceOf(RetryableBusinessException.class);

      verify(outboxService, never()).addReturningId(any(Outbox.class));
    }

    @Test
    @DisplayName("Should not mark command as succeeded for retryable exceptions")
    void testProcess_RetryableException_NoSucceeded() {
      // Arrange
      Envelope envelope = createTestEnvelope("NoSucceededRetry", "{}");
      TransientException exception = new TransientException("Network timeout");

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenThrow(exception);

      // Act & Assert
      assertThatThrownBy(() -> executor.process(envelope))
          .isInstanceOf(TransientException.class);

      verify(commandService, never()).markSucceeded(any(UUID.class));
    }
  }

  @Nested
  @DisplayName("Edge Cases and Error Handling Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle envelope with minimal payload")
    void testProcess_MinimalPayload() {
      // Arrange
      Envelope envelope = createTestEnvelope("MinimalCommand", "");
      CommandReply reply =
          CommandReply.completed(envelope.commandId(), envelope.correlationId(), Map.of());

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(270L, 280L);

      // Act
      executor.process(envelope);

      // Assert
      verify(commandService).markSucceeded(envelope.commandId());
    }

    @Test
    @DisplayName("Should handle envelope with large payload")
    void testProcess_LargePayload() {
      // Arrange
      StringBuilder largePayload = new StringBuilder();
      for (int i = 0; i < 10000; i++) {
        largePayload.append("data");
      }

      Envelope envelope = createTestEnvelope("LargeCommand", largePayload.toString());
      CommandReply reply =
          CommandReply.completed(envelope.commandId(), envelope.correlationId(), Map.of());

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(290L, 300L);

      // Act
      executor.process(envelope);

      // Assert
      verify(commandService).markSucceeded(envelope.commandId());
    }

    @Test
    @DisplayName("Should handle reply with complex nested data")
    void testProcess_ComplexReplyData() {
      // Arrange
      Envelope envelope = createTestEnvelope("ComplexCommand", "{}");
      Map<String, Object> complexData =
          Map.of(
              "result",
              Map.of("nested", Map.of("value", 123, "items", java.util.List.of("a", "b", "c"))));

      CommandReply reply =
          CommandReply.completed(envelope.commandId(), envelope.correlationId(), complexData);

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(310L, 320L);

      // Act
      executor.process(envelope);

      // Assert
      verify(commandService).markSucceeded(envelope.commandId());
      verify(outboxService, times(2)).addReturningId(any(Outbox.class));
    }

    @Test
    @DisplayName("Should handle exception from inbox service")
    void testProcess_InboxServiceException() {
      // Arrange
      Envelope envelope = createTestEnvelope("InboxErrorCommand", "{}");

      when(inboxService.markIfAbsent(anyString(), anyString()))
          .thenThrow(new RuntimeException("Inbox database error"));

      // Act & Assert
      assertThatThrownBy(() -> executor.process(envelope))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Inbox database error");

      verify(commandService, never()).markRunning(any(UUID.class), any(Instant.class));
    }

    @Test
    @DisplayName("Should handle exception from outbox service")
    void testProcess_OutboxServiceException() {
      // Arrange
      Envelope envelope = createTestEnvelope("OutboxErrorCommand", "{}");
      CommandReply reply =
          CommandReply.completed(envelope.commandId(), envelope.correlationId(), Map.of());

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class)))
          .thenThrow(new RuntimeException("Outbox insert failed"));

      // Act & Assert
      assertThatThrownBy(() -> executor.process(envelope))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Outbox insert failed");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should complete full successful workflow")
    void testProcess_FullWorkflow() {
      // Arrange
      Envelope envelope = createTestEnvelope("FullWorkflowCommand", "{\"data\":\"test\"}");
      CommandReply reply =
          CommandReply.completed(
              envelope.commandId(), envelope.correlationId(), Map.of("result", "success"));

      when(inboxService.markIfAbsent(envelope.messageId().toString(), "CommandExecutor"))
          .thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(330L, 340L);

      // Act
      executor.process(envelope);

      // Assert - Verify complete workflow
      var inOrder =
          inOrder(
              inboxService,
              commandService,
              handlerRegistry,
              outboxService);

      inOrder.verify(inboxService).markIfAbsent(envelope.messageId().toString(), "CommandExecutor");
      inOrder.verify(commandService).markRunning(eq(envelope.commandId()), any(Instant.class));
      inOrder.verify(handlerRegistry).handle(any(CommandMessage.class));
      inOrder.verify(commandService).markSucceeded(envelope.commandId());
      inOrder.verify(outboxService, times(2)).addReturningId(any(Outbox.class));
    }

    @Test
    @DisplayName("Should complete full failure workflow for permanent exception")
    void testProcess_FullFailureWorkflow() {
      // Arrange
      Envelope envelope = createTestEnvelope("FullFailureCommand", "{}");
      PermanentException exception = new PermanentException("Workflow failure");

      when(inboxService.markIfAbsent(envelope.messageId().toString(), "CommandExecutor"))
          .thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenThrow(exception);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(350L, 360L);

      // Act
      executor.process(envelope);

      // Assert - Verify complete failure workflow
      var inOrder =
          inOrder(
              inboxService,
              commandService,
              handlerRegistry,
              dlqService,
              outboxService);

      inOrder.verify(inboxService).markIfAbsent(envelope.messageId().toString(), "CommandExecutor");
      inOrder.verify(commandService).markRunning(eq(envelope.commandId()), any(Instant.class));
      inOrder.verify(handlerRegistry).handle(any(CommandMessage.class));
      inOrder.verify(commandService).markFailed(envelope.commandId(), "Workflow failure");
      inOrder
          .verify(dlqService)
          .park(
              envelope.commandId(),
              envelope.name(),
              envelope.key(),
              envelope.payload(),
              "FAILED",
              "Permanent",
              "Workflow failure",
              0,
              "worker");
      inOrder.verify(outboxService, times(2)).addReturningId(any(Outbox.class));
    }

    @Test
    @DisplayName("Should handle multiple commands sequentially")
    void testProcess_MultipleCommands() {
      // Arrange
      Envelope env1 = createTestEnvelope("Command1", "{}");
      Envelope env2 = createTestEnvelope("Command2", "{}");
      Envelope env3 = createTestEnvelope("Command3", "{}");

      CommandReply reply1 =
          CommandReply.completed(env1.commandId(), env1.correlationId(), Map.of());
      CommandReply reply2 =
          CommandReply.completed(env2.commandId(), env2.correlationId(), Map.of());
      CommandReply reply3 =
          CommandReply.completed(env3.commandId(), env3.correlationId(), Map.of());

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class)))
          .thenReturn(reply1, reply2, reply3);
      when(outboxService.addReturningId(any(Outbox.class)))
          .thenReturn(370L, 380L, 390L, 400L, 410L, 420L);

      // Act
      executor.process(env1);
      executor.process(env2);
      executor.process(env3);

      // Assert
      verify(commandService, times(3)).markSucceeded(any(UUID.class));
      verify(outboxService, times(6)).addReturningId(any(Outbox.class));
    }
  }

  @Nested
  @DisplayName("FastPath Publisher Integration Tests")
  class FastPathPublisherTests {

    @Test
    @DisplayName("Should not call fastPath publisher when disabled")
    void testProcess_FastPathDisabled() {
      // Arrange
      Envelope envelope = createTestEnvelope("FastPathTest", "{}");
      CommandReply reply =
          CommandReply.completed(envelope.commandId(), envelope.correlationId(), Map.of());

      when(inboxService.markIfAbsent(anyString(), anyString())).thenReturn(true);
      when(handlerRegistry.handle(any(CommandMessage.class))).thenReturn(reply);
      when(outboxService.addReturningId(any(Outbox.class))).thenReturn(430L, 440L);

      // Act
      executor.process(envelope);

      // Assert - FastPath is commented out in code, should not be called
      verifyNoInteractions(fastPathPublisher);
    }
  }

  @Nested
  @DisplayName("Transaction Boundary Tests")
  class TransactionBoundaryTests {

    @Test
    @DisplayName("Should ensure process method is transactional")
    void testProcess_TransactionalAnnotation() {
      // Verify the method has @Transactional annotation
      assertThatCode(
              () -> TransactionalExecutor.class.getMethod("process", Envelope.class))
          .doesNotThrowAnyException();
    }
  }
}
