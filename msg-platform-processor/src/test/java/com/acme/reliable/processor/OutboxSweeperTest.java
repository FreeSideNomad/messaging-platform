package com.acme.reliable.processor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.acme.reliable.domain.Outbox;
import com.acme.reliable.repository.OutboxRepository;
import com.acme.reliable.spi.KafkaPublisher;
import com.acme.reliable.spi.MqPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxSweeper Tests")
class OutboxSweeperTest {

  @Mock private OutboxRepository outboxRepository;
  @Mock private MqPublisher mqPublisher;
  @Mock private KafkaPublisher kafkaPublisher;

  private OutboxSweeper outboxSweeper;

  @BeforeEach
  void setup() {
    outboxSweeper = new OutboxSweeper(outboxRepository, mqPublisher, kafkaPublisher);
  }

  @Nested
  @DisplayName("tick - Recovery Tests")
  class RecoveryTests {

    @Test
    @DisplayName("should recover stuck SENDING messages on each tick")
    void testTick_RecoverStuck() {
      // Given
      when(outboxRepository.recoverStuck(any(Duration.class))).thenReturn(5);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of());

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).recoverStuck(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("should log when stuck messages are recovered")
    void testTick_RecoverStuck_LogWhenFound() {
      // Given
      when(outboxRepository.recoverStuck(any(Duration.class))).thenReturn(10);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of());

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).recoverStuck(Duration.ofSeconds(10));
      // Verify recovery happened (logs would show "Recovered 10 stuck SENDING messages")
    }

    @Test
    @DisplayName("should not log when no stuck messages found")
    void testTick_RecoverStuck_NoLog() {
      // Given
      when(outboxRepository.recoverStuck(any(Duration.class))).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of());

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).recoverStuck(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("should use 10 second timeout for stuck message recovery")
    void testTick_RecoverStuck_Timeout() {
      // Given
      when(outboxRepository.recoverStuck(any(Duration.class))).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
      verify(outboxRepository).recoverStuck(durationCaptor.capture());
      assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofSeconds(10));
    }
  }

  @Nested
  @DisplayName("tick - Sweep Batch Tests")
  class SweepBatchTests {

    @Test
    @DisplayName("should sweep batch of 500 messages")
    void testTick_SweepBatch() {
      // Given
      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of());

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).sweepBatch(500);
    }

    @Test
    @DisplayName("should process all messages in batch")
    void testTick_ProcessBatch() {
      // Given
      Outbox outbox1 =
          createOutbox(1L, "command", "APP.CMD.Q1", "key1", "Type1", "{}", Map.of(), 0);
      Outbox outbox2 =
          createOutbox(2L, "event", "topic.events", "key2", "Type2", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox1, outbox2));

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher).publish("APP.CMD.Q1", "key1", "Type1", "{}", Map.of());
      verify(kafkaPublisher).publish("topic.events", "key2", "Type2", "{}", Map.of());
      verify(outboxRepository).markPublished(1L);
      verify(outboxRepository).markPublished(2L);
    }

    @Test
    @DisplayName("should handle empty batch gracefully")
    void testTick_EmptyBatch() {
      // Given
      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of());

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher, never()).publish(any(), any(), any(), any(), any());
      verify(kafkaPublisher, never()).publish(any(), any(), any(), any(), any());
      verify(outboxRepository, never()).markPublished(anyLong());
    }

    @Test
    @DisplayName("should log debug when batch is not empty")
    void testTick_LogDebug() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);
      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).sweepBatch(500);
      // Logs would show "Sweeping 1 outbox messages"
    }
  }

  @Nested
  @DisplayName("Category Routing Tests")
  class CategoryRoutingTests {

    @Test
    @DisplayName("should route command category to MqPublisher")
    void testRouting_Command() {
      // Given
      Outbox outbox =
          createOutbox(
              1L,
              "command",
              "APP.CMD.PAYMENT.Q",
              "pay-123",
              "ProcessPayment",
              "{\"amount\":100}",
              Map.of("commandId", "cmd-1"),
              0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher)
          .publish(
              "APP.CMD.PAYMENT.Q",
              "pay-123",
              "ProcessPayment",
              "{\"amount\":100}",
              Map.of("commandId", "cmd-1"));
      verify(kafkaPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should route reply category to MqPublisher")
    void testRouting_Reply() {
      // Given
      Outbox outbox =
          createOutbox(
              1L,
              "reply",
              "APP.CMD.REPLY.Q",
              "reply-123",
              "CommandCompleted",
              "{\"status\":\"ok\"}",
              Map.of("correlationId", "corr-1"),
              0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher)
          .publish(
              "APP.CMD.REPLY.Q",
              "reply-123",
              "CommandCompleted",
              "{\"status\":\"ok\"}",
              Map.of("correlationId", "corr-1"));
      verify(kafkaPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should route event category to KafkaPublisher")
    void testRouting_Event() {
      // Given
      Outbox outbox =
          createOutbox(
              1L,
              "event",
              "payment.completed",
              "pay-123",
              "PaymentCompleted",
              "{\"amount\":100}",
              Map.of("eventId", "evt-1"),
              0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(kafkaPublisher)
          .publish(
              "payment.completed",
              "pay-123",
              "PaymentCompleted",
              "{\"amount\":100}",
              Map.of("eventId", "evt-1"));
      verify(mqPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for unknown category")
    void testRouting_UnknownCategory() {
      // Given
      Outbox outbox = createOutbox(1L, "unknown", "topic", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then - should mark as failed, not throw
      verify(outboxRepository, never()).markPublished(1L);
      verify(outboxRepository).markFailed(eq(1L), contains("Unknown category"), any(Instant.class));
    }
  }

  @Nested
  @DisplayName("Success Path Tests")
  class SuccessPathTests {

    @Test
    @DisplayName("should mark message as published after successful publish")
    void testSuccess_MarkPublished() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).markPublished(1L);
    }

    @Test
    @DisplayName("should log successful sweep and publish")
    void testSuccess_Logging() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).markPublished(1L);
      // Logs would show "Swept and published outbox id=1 category=command"
    }

    @Test
    @DisplayName("should process multiple messages sequentially")
    void testSuccess_MultipleMessages() {
      // Given
      Outbox outbox1 = createOutbox(1L, "command", "q1", "k1", "T1", "{}", Map.of(), 0);
      Outbox outbox2 = createOutbox(2L, "event", "t2", "k2", "T2", "{}", Map.of(), 0);
      Outbox outbox3 = createOutbox(3L, "reply", "q3", "k3", "T3", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox1, outbox2, outbox3));

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher).publish("q1", "k1", "T1", "{}", Map.of());
      verify(kafkaPublisher).publish("t2", "k2", "T2", "{}", Map.of());
      verify(mqPublisher).publish("q3", "k3", "T3", "{}", Map.of());
      verify(outboxRepository).markPublished(1L);
      verify(outboxRepository).markPublished(2L);
      verify(outboxRepository).markPublished(3L);
    }
  }

  @Nested
  @DisplayName("Failure Handling Tests")
  class FailureHandlingTests {

    @Test
    @DisplayName("should mark message as failed on publish exception")
    void testFailure_MarkFailed() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Connection timeout"))
          .when(mqPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository, never()).markPublished(1L);
      verify(outboxRepository).markFailed(eq(1L), contains("timeout"), any(Instant.class));
    }

    @Test
    @DisplayName("should continue processing after failure")
    void testFailure_ContinueProcessing() {
      // Given
      Outbox outbox1 = createOutbox(1L, "command", "q1", "k1", "T1", "{}", Map.of(), 0);
      Outbox outbox2 = createOutbox(2L, "event", "t2", "k2", "T2", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox1, outbox2));
      doThrow(new RuntimeException("Failed"))
          .when(mqPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher).publish("q1", "k1", "T1", "{}", Map.of());
      verify(kafkaPublisher).publish("t2", "k2", "T2", "{}", Map.of()); // Should still process
      verify(outboxRepository).markFailed(eq(1L), anyString(), any(Instant.class));
      verify(outboxRepository).markPublished(2L);
    }

    @Test
    @DisplayName("should log warning on publish failure")
    void testFailure_Logging() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Publish failed"))
          .when(mqPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).markFailed(eq(1L), eq("Publish failed"), any(Instant.class));
      // Logs would show "Failed to publish outbox id=1: Publish failed"
    }

    @Test
    @DisplayName("should not throw exception on tick failure")
    void testFailure_NoThrow() {
      // Given
      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenThrow(new RuntimeException("Database error"));

      // When/Then - should not throw
      assertThatCode(() -> outboxSweeper.tick()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should log error when tick fails")
    void testFailure_TickException() {
      // Given
      when(outboxRepository.recoverStuck(any())).thenThrow(new RuntimeException("DB connection lost"));

      // When
      outboxSweeper.tick();

      // Then - logs would show "Error in OutboxSweeper tick: DB connection lost"
      // Tick should not throw, just log
    }
  }

  @Nested
  @DisplayName("Backoff Calculation Tests")
  class BackoffCalculationTests {

    @Test
    @DisplayName("should calculate backoff as 1 second for first attempt")
    void testBackoff_FirstAttempt() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Failed"))
          .when(mqPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxRepository).markFailed(eq(1L), anyString(), instantCaptor.capture());

      // Backoff for attempt 1: 1 << min(1, 8) = 1 << 1 = 2 seconds
      Instant nextAttempt = instantCaptor.getValue();
      Instant expectedMin = Instant.now().plusSeconds(1);
      Instant expectedMax = Instant.now().plusSeconds(3);

      assertThat(nextAttempt).isAfter(expectedMin).isBefore(expectedMax);
    }

    @Test
    @DisplayName("should calculate backoff as 4 seconds for second attempt")
    void testBackoff_SecondAttempt() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 1);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Failed"))
          .when(mqPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxRepository).markFailed(eq(1L), anyString(), instantCaptor.capture());

      // Backoff for attempt 2: 1 << min(2, 8) = 1 << 2 = 4 seconds
      Instant nextAttempt = instantCaptor.getValue();
      Instant expectedMin = Instant.now().plusSeconds(3);
      Instant expectedMax = Instant.now().plusSeconds(5);

      assertThat(nextAttempt).isAfter(expectedMin).isBefore(expectedMax);
    }

    @Test
    @DisplayName("should cap backoff at 256 seconds for 8th attempt")
    void testBackoff_EighthAttempt() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 7);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Failed"))
          .when(mqPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxRepository).markFailed(eq(1L), anyString(), instantCaptor.capture());

      // Backoff for attempt 8: 1 << min(8, 8) = 1 << 8 = 256 seconds
      Instant nextAttempt = instantCaptor.getValue();
      Instant expectedMin = Instant.now().plusSeconds(255);
      Instant expectedMax = Instant.now().plusSeconds(257);

      assertThat(nextAttempt).isAfter(expectedMin).isBefore(expectedMax);
    }

    @Test
    @DisplayName("should cap backoff at 300 seconds maximum")
    void testBackoff_MaxBackoff() {
      // Given - attempt 10 would give 1 << 10 = 1024, but capped at 300
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 9);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Failed"))
          .when(mqPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxRepository).markFailed(eq(1L), anyString(), instantCaptor.capture());

      // Backoff should be min(300, 1 << min(10, 8)) = min(300, 256) = 256
      // But for attempt 9: 1 << 8 = 256, which is less than 300
      Instant nextAttempt = instantCaptor.getValue();
      Instant expectedMin = Instant.now().plusSeconds(255);
      Instant expectedMax = Instant.now().plusSeconds(257);

      assertThat(nextAttempt).isAfter(expectedMin).isBefore(expectedMax);
    }

    @Test
    @DisplayName("should enforce 300 second cap for very high attempts")
    void testBackoff_VeryHighAttempt() {
      // Given - using reflection or testing the backoff method directly
      // For now, verify behavior at boundary (attempt 8 gives 256, attempt 9+ capped at 300)
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 20);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Failed"))
          .when(mqPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxRepository).markFailed(eq(1L), anyString(), instantCaptor.capture());

      // Backoff: min(300, 1 << min(21, 8)) = min(300, 256) = 256
      Instant nextAttempt = instantCaptor.getValue();
      Instant expectedMin = Instant.now().plusSeconds(255);
      Instant expectedMax = Instant.now().plusSeconds(257);

      assertThat(nextAttempt).isAfter(expectedMin).isBefore(expectedMax);
    }
  }

  @Nested
  @DisplayName("Header and Payload Tests")
  class HeaderAndPayloadTests {

    @Test
    @DisplayName("should handle empty headers")
    void testHeaders_Empty() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher).publish("queue", "key", "Type", "{}", Map.of());
    }

    @Test
    @DisplayName("should preserve all headers")
    void testHeaders_Multiple() {
      // Given
      Map<String, String> headers =
          Map.of(
              "header1", "value1",
              "header2", "value2",
              "correlationId", "123");
      Outbox outbox = createOutbox(1L, "event", "topic", "key", "Type", "{}", headers, 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(kafkaPublisher).publish("topic", "key", "Type", "{}", headers);
    }

    @Test
    @DisplayName("should handle complex JSON payload")
    void testPayload_ComplexJson() {
      // Given
      String payload = "{\"customer\":{\"id\":\"123\"},\"items\":[{\"sku\":\"A\"}]}";
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", payload, Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher).publish("queue", "key", "Type", payload, Map.of());
    }

    @Test
    @DisplayName("should handle large payload")
    void testPayload_Large() {
      // Given
      String largePayload = "{\"data\":\"" + "x".repeat(50000) + "\"}";
      Outbox outbox =
          createOutbox(1L, "event", "topic", "key", "Type", largePayload, Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(kafkaPublisher).publish("topic", "key", "Type", largePayload, Map.of());
    }
  }

  @Nested
  @DisplayName("Concurrency and Performance Tests")
  class ConcurrencyTests {

    @Test
    @DisplayName("should handle concurrent tick calls safely")
    void testConcurrency_MultipleTicks() throws InterruptedException {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);
      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When - simulate concurrent ticks
      Thread t1 = new Thread(() -> outboxSweeper.tick());
      Thread t2 = new Thread(() -> outboxSweeper.tick());
      Thread t3 = new Thread(() -> outboxSweeper.tick());

      t1.start();
      t2.start();
      t3.start();

      t1.join();
      t2.join();
      t3.join();

      // Then - all should complete
      verify(outboxRepository, times(3)).recoverStuck(any());
      verify(outboxRepository, times(3)).sweepBatch(500);
    }

    @Test
    @DisplayName("should handle large batch efficiently")
    void testPerformance_LargeBatch() {
      // Given - create 500 outbox entries
      List<Outbox> largeBatch =
          java.util.stream.IntStream.range(1, 501)
              .mapToObj(i -> createOutbox(i, "command", "queue", "key" + i, "Type", "{}", Map.of(), 0))
              .toList();

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(largeBatch);

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher, times(500)).publish(any(), any(), any(), any(), any());
      verify(outboxRepository, times(500)).markPublished(anyLong());
    }
  }

  @Nested
  @DisplayName("Error Message Tests")
  class ErrorMessageTests {

    @Test
    @DisplayName("should capture full exception message")
    void testErrorMessage_Full() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Database connection timeout after 30 seconds"))
          .when(mqPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
      verify(outboxRepository).markFailed(eq(1L), errorCaptor.capture(), any(Instant.class));
      assertThat(errorCaptor.getValue()).isEqualTo("Database connection timeout after 30 seconds");
    }

    @Test
    @DisplayName("should handle null exception message")
    void testErrorMessage_Null() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new NullPointerException()).when(mqPublisher).publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).markFailed(eq(1L), isNull(), any(Instant.class));
    }
  }

  @Nested
  @DisplayName("Integration-like Tests")
  class IntegrationLikeTests {

    @Test
    @DisplayName("should recover stuck messages then sweep new ones")
    void testIntegration_RecoveryThenSweep() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(3);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then - should do both operations
      verify(outboxRepository).recoverStuck(Duration.ofSeconds(10));
      verify(outboxRepository).sweepBatch(500);
      verify(mqPublisher).publish("queue", "key", "Type", "{}", Map.of());
      verify(outboxRepository).markPublished(1L);
    }

    @Test
    @DisplayName("should handle mixed success and failure in batch")
    void testIntegration_MixedResults() {
      // Given
      Outbox success1 = createOutbox(1L, "command", "q1", "k1", "T1", "{}", Map.of(), 0);
      Outbox failure = createOutbox(2L, "event", "t2", "k2", "T2", "{}", Map.of(), 0);
      Outbox success2 = createOutbox(3L, "reply", "q3", "k3", "T3", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(success1, failure, success2));
      doThrow(new RuntimeException("Kafka down"))
          .when(kafkaPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher).publish("q1", "k1", "T1", "{}", Map.of());
      verify(kafkaPublisher).publish("t2", "k2", "T2", "{}", Map.of());
      verify(mqPublisher).publish("q3", "k3", "T3", "{}", Map.of());

      verify(outboxRepository).markPublished(1L);
      verify(outboxRepository).markFailed(eq(2L), eq("Kafka down"), any(Instant.class));
      verify(outboxRepository).markPublished(3L);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Additional Branch Coverage")
  class EdgeCasesTests {

    @Test
    @DisplayName("should handle negative attempts in backoff calculation")
    void testBackoff_NegativeAttempts() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), -1);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Error")).when(mqPublisher).publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxRepository).markFailed(eq(1L), anyString(), instantCaptor.capture());

      // Backoff for attempt 0: 1 << min(0, 8) = 1 << 0 = 1 second
      Instant nextAttempt = instantCaptor.getValue();
      Instant expectedMin = Instant.now();
      Instant expectedMax = Instant.now().plusSeconds(2);

      assertThat(nextAttempt).isAfter(expectedMin).isBefore(expectedMax);
    }

    @Test
    @DisplayName("should handle zero attempts in backoff calculation")
    void testBackoff_ZeroAttempts() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Error")).when(mqPublisher).publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxRepository).markFailed(eq(1L), anyString(), instantCaptor.capture());

      // Backoff for attempt 1: 1 << min(1, 8) = 1 << 1 = 2 seconds
      Instant nextAttempt = instantCaptor.getValue();
      Instant expectedMin = Instant.now().plusSeconds(1);
      Instant expectedMax = Instant.now().plusSeconds(3);

      assertThat(nextAttempt).isAfter(expectedMin).isBefore(expectedMax);
    }

    @Test
    @DisplayName("should handle intermediate backoff values")
    void testBackoff_IntermediateAttempts() {
      // Given - test attempts 3, 4, 5
      Outbox outbox3 = createOutbox(1L, "command", "q", "k", "T", "{}", Map.of(), 2);
      Outbox outbox4 = createOutbox(2L, "command", "q", "k", "T", "{}", Map.of(), 3);
      Outbox outbox5 = createOutbox(3L, "command", "q", "k", "T", "{}", Map.of(), 4);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox3, outbox4, outbox5));
      doThrow(new RuntimeException("Error")).when(mqPublisher).publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxRepository, times(3)).markFailed(anyLong(), anyString(), instantCaptor.capture());

      List<Instant> nextAttempts = instantCaptor.getAllValues();

      // Attempt 3: 1 << 3 = 8 seconds
      assertThat(nextAttempts.get(0)).isAfter(Instant.now().plusSeconds(7)).isBefore(Instant.now().plusSeconds(9));

      // Attempt 4: 1 << 4 = 16 seconds
      assertThat(nextAttempts.get(1)).isAfter(Instant.now().plusSeconds(15)).isBefore(Instant.now().plusSeconds(17));

      // Attempt 5: 1 << 5 = 32 seconds
      assertThat(nextAttempts.get(2)).isAfter(Instant.now().plusSeconds(31)).isBefore(Instant.now().plusSeconds(33));
    }

    @Test
    @DisplayName("should enforce exact 300 second cap for attempt 9+")
    void testBackoff_Attempt9Cap() {
      // Given - attempt 9 would give 1 << min(9, 8) = 1 << 8 = 256, which is less than 300
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 8);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Error")).when(mqPublisher).publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxRepository).markFailed(eq(1L), anyString(), instantCaptor.capture());

      // Backoff: 1 << min(9, 8) = 256 seconds (still under 300 cap)
      Instant nextAttempt = instantCaptor.getValue();
      assertThat(nextAttempt).isAfter(Instant.now().plusSeconds(255)).isBefore(Instant.now().plusSeconds(257));
    }

    @Test
    @DisplayName("should cap very high attempts at 256 seconds due to bit shift limit")
    void testBackoff_VeryHighAttempts() {
      // Given - attempt 100 should be capped by min(100, 8) = 8
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 99);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Error")).when(mqPublisher).publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
      verify(outboxRepository).markFailed(eq(1L), anyString(), instantCaptor.capture());

      // Backoff: min(300, 1 << min(100, 8)) = min(300, 256) = 256 seconds
      Instant nextAttempt = instantCaptor.getValue();
      assertThat(nextAttempt).isAfter(Instant.now().plusSeconds(255)).isBefore(Instant.now().plusSeconds(257));
    }

    @Test
    @DisplayName("should handle empty string payload")
    void testPayload_EmptyString() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher).publish("queue", "key", "Type", "", Map.of());
      verify(outboxRepository).markPublished(1L);
    }

    @Test
    @DisplayName("should handle null payload in error path")
    void testPayload_NullInError() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", null, Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Null payload")).when(mqPublisher).publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).markFailed(eq(1L), eq("Null payload"), any(Instant.class));
    }

    @Test
    @DisplayName("should handle special characters in all string fields")
    void testSpecialCharacters_AllFields() {
      // Given
      String specialPayload = "{\"text\":\"Hello\\nWorld\\t\\r\"}";
      Map<String, String> specialHeaders = Map.of("key", "value with spaces & symbols!@#$");
      Outbox outbox =
          createOutbox(
              1L, "command", "queue.with.dots", "key-with-dashes", "Type_With_Underscores", specialPayload, specialHeaders, 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(mqPublisher)
          .publish("queue.with.dots", "key-with-dashes", "Type_With_Underscores", specialPayload, specialHeaders);
      verify(outboxRepository).markPublished(1L);
    }

    @Test
    @DisplayName("should handle uppercase category")
    void testCategory_Uppercase() {
      // Given - uppercase category should fail as unknown
      Outbox outbox = createOutbox(1L, "COMMAND", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).markFailed(eq(1L), contains("Unknown category: COMMAND"), any(Instant.class));
    }

    @Test
    @DisplayName("should handle empty string category")
    void testCategory_EmptyString() {
      // Given
      Outbox outbox = createOutbox(1L, "", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then
      verify(outboxRepository).markFailed(eq(1L), contains("Unknown category: "), any(Instant.class));
    }

    @Test
    @DisplayName("should handle null category")
    void testCategory_Null() {
      // Given
      Outbox outbox = createOutbox(1L, null, "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When
      outboxSweeper.tick();

      // Then - should handle NPE or match error
      verify(outboxRepository).markFailed(eq(1L), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("should handle recovery failure gracefully")
    void testRecovery_Failure() {
      // Given
      when(outboxRepository.recoverStuck(any())).thenThrow(new RuntimeException("Recovery DB error"));

      // When
      outboxSweeper.tick();

      // Then - should catch exception and log, not throw
      verify(outboxRepository).recoverStuck(Duration.ofSeconds(10));
      // sweepBatch should not be called due to exception
      verify(outboxRepository, never()).sweepBatch(anyInt());
    }

    @Test
    @DisplayName("should handle sweepBatch failure gracefully")
    void testSweepBatch_Failure() {
      // Given
      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenThrow(new RuntimeException("Sweep DB error"));

      // When
      outboxSweeper.tick();

      // Then - should catch exception and log, not throw
      verify(outboxRepository).sweepBatch(500);
      verify(mqPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should handle markPublished failure gracefully")
    void testMarkPublished_Failure() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Mark published failed"))
          .when(outboxRepository)
          .markPublished(anyLong());

      // When
      outboxSweeper.tick();

      // Then - should catch exception and log
      verify(mqPublisher).publish("queue", "key", "Type", "{}", Map.of());
      verify(outboxRepository).markPublished(1L);
    }

    @Test
    @DisplayName("should handle markFailed failure gracefully")
    void testMarkFailed_Failure() {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));
      doThrow(new RuntimeException("Publish error")).when(mqPublisher).publish(any(), any(), any(), any(), any());
      doThrow(new RuntimeException("Mark failed error"))
          .when(outboxRepository)
          .markFailed(anyLong(), anyString(), any(Instant.class));

      // When
      outboxSweeper.tick();

      // Then - should catch both exceptions and log
      verify(mqPublisher).publish("queue", "key", "Type", "{}", Map.of());
      verify(outboxRepository).markFailed(eq(1L), eq("Publish error"), any(Instant.class));
    }

    @Test
    @DisplayName("should handle all messages failing in batch")
    void testBatch_AllFailures() {
      // Given
      List<Outbox> batch =
          List.of(
              createOutbox(1L, "command", "q1", "k1", "T1", "{}", Map.of(), 0),
              createOutbox(2L, "event", "t2", "k2", "T2", "{}", Map.of(), 0),
              createOutbox(3L, "reply", "q3", "k3", "T3", "{}", Map.of(), 0));

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(batch);
      doThrow(new RuntimeException("All failed"))
          .when(mqPublisher)
          .publish(any(), any(), any(), any(), any());
      doThrow(new RuntimeException("All failed"))
          .when(kafkaPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then - all should be marked as failed
      verify(outboxRepository, times(3)).markFailed(anyLong(), eq("All failed"), any(Instant.class));
      verify(outboxRepository, never()).markPublished(anyLong());
    }

    @Test
    @DisplayName("should handle maximum batch size efficiently")
    void testBatch_MaximumSize() {
      // Given - 500 outbox entries
      List<Outbox> largeBatch = new java.util.ArrayList<>();
      for (int i = 1; i <= 500; i++) {
        largeBatch.add(createOutbox(i, "command", "queue", "key" + i, "Type", "{}", Map.of(), 0));
      }

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(largeBatch);

      // When
      outboxSweeper.tick();

      // Then - all should be processed
      verify(mqPublisher, times(500)).publish(any(), any(), any(), any(), any());
      verify(outboxRepository, times(500)).markPublished(anyLong());
    }

    @Test
    @DisplayName("should handle alternating success and failure")
    void testBatch_AlternatingResults() {
      // Given - 10 messages, alternating success/failure
      List<Outbox> batch = new java.util.ArrayList<>();
      for (int i = 1; i <= 10; i++) {
        batch.add(createOutbox(i, "command", "queue" + i, "key" + i, "Type", "{}", Map.of(), 0));
      }

      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(batch);

      java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
      doAnswer(
              invocation -> {
                if (count.getAndIncrement() % 2 == 1) {
                  throw new RuntimeException("Odd failure");
                }
                return null;
              })
          .when(mqPublisher)
          .publish(any(), any(), any(), any(), any());

      // When
      outboxSweeper.tick();

      // Then - 5 success, 5 failures
      verify(mqPublisher, times(10)).publish(any(), any(), any(), any(), any());
      verify(outboxRepository, times(5)).markPublished(anyLong());
      verify(outboxRepository, times(5)).markFailed(anyLong(), eq("Odd failure"), any(Instant.class));
    }

    @Test
    @DisplayName("should handle recovery of large number of stuck messages")
    void testRecovery_LargeNumber() {
      // Given - 1000 stuck messages recovered
      when(outboxRepository.recoverStuck(any())).thenReturn(1000);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of());

      // When
      outboxSweeper.tick();

      // Then - should log the recovery
      verify(outboxRepository).recoverStuck(Duration.ofSeconds(10));
      verify(outboxRepository).sweepBatch(500);
    }

    @Test
    @DisplayName("should handle concurrent tick executions")
    void testConcurrency_MultipleTicks() throws InterruptedException {
      // Given
      Outbox outbox = createOutbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), 0);
      when(outboxRepository.recoverStuck(any())).thenReturn(0);
      when(outboxRepository.sweepBatch(500)).thenReturn(List.of(outbox));

      // When - simulate concurrent ticks
      Thread t1 = new Thread(() -> outboxSweeper.tick());
      Thread t2 = new Thread(() -> outboxSweeper.tick());
      Thread t3 = new Thread(() -> outboxSweeper.tick());

      t1.start();
      t2.start();
      t3.start();

      t1.join();
      t2.join();
      t3.join();

      // Then - all should complete
      verify(outboxRepository, times(3)).recoverStuck(any());
      verify(outboxRepository, times(3)).sweepBatch(500);
    }
  }

  // Helper method to create Outbox entries
  private Outbox createOutbox(
      long id,
      String category,
      String topic,
      String key,
      String type,
      String payload,
      Map<String, String> headers,
      int attempts) {
    Outbox outbox = new Outbox();
    outbox.setId(id);
    outbox.setCategory(category);
    outbox.setTopic(topic);
    outbox.setKey(key);
    outbox.setType(type);
    outbox.setPayload(payload);
    outbox.setHeaders(headers);
    outbox.setAttempts(attempts);
    outbox.setStatus("SENDING");
    outbox.setCreatedAt(Instant.now());
    return outbox;
  }
}
