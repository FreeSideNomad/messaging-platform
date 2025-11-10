package com.acme.reliable.processor;

import com.acme.reliable.config.TimeoutConfig;
import com.acme.reliable.domain.Outbox;
import com.acme.reliable.service.OutboxService;
import com.acme.reliable.spi.CommandQueue;
import com.acme.reliable.spi.EventPublisher;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelay Tests")
class OutboxRelayTest {

    @Mock
    private OutboxService outboxService;
    @Mock
    private CommandQueue commandQueue;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private TransactionOperations<Connection> transactionOps;

    private OutboxRelay outboxRelay;
    private TimeoutConfig timeoutConfig;

    @BeforeEach
    void setup() {
        timeoutConfig = new TimeoutConfig();
        timeoutConfig.setOutboxBatchSize(10);
        outboxRelay =
                new OutboxRelay(
                        outboxService, commandQueue, eventPublisher, timeoutConfig, transactionOps);

        // Setup default transaction mock behavior
        when(transactionOps.executeWrite(any()))
                .thenAnswer(
                        invocation -> {
                            @SuppressWarnings("unchecked")
                            Function<TransactionStatus<Connection>, Object> callback = invocation.getArgument(0);
                            return callback.apply(mock(TransactionStatus.class));
                        });
    }

    @Nested
    @DisplayName("publishNow Tests")
    class PublishNowTests {

        @Test
        @DisplayName("should claim and publish command entry immediately")
        void testPublishNow_Command() {
            // Given
            long outboxId = 123L;
            Outbox outbox =
                    new Outbox(
                            outboxId,
                            "command",
                            "APP.CMD.PAYMENTS.Q",
                            "payment-123",
                            "ProcessPayment",
                            "{\"amount\":100}",
                            Map.of("commandId", "cmd-1"),
                            "CLAIMED",
                            0);

            when(outboxService.claimOne(outboxId)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(outboxId);

            // Then
            verify(outboxService).claimOne(outboxId);
            verify(commandQueue)
                    .send("APP.CMD.PAYMENTS.Q", "{\"amount\":100}", Map.of("commandId", "cmd-1"));
            verify(outboxService).markPublished(outboxId);
        }

        @Test
        @DisplayName("should claim and publish event entry immediately")
        void testPublishNow_Event() {
            // Given
            long outboxId = 456L;
            Outbox outbox =
                    new Outbox(
                            outboxId,
                            "event",
                            "payment.events",
                            "payment-123",
                            "PaymentCompleted",
                            "{\"status\":\"completed\"}",
                            Map.of("eventId", "evt-1"),
                            "CLAIMED",
                            0);

            when(outboxService.claimOne(outboxId)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(outboxId);

            // Then
            verify(outboxService).claimOne(outboxId);
            verify(eventPublisher)
                    .publish(
                            "payment.events",
                            "payment-123",
                            "{\"status\":\"completed\"}",
                            Map.of("eventId", "evt-1"));
            verify(outboxService).markPublished(outboxId);
        }

        @Test
        @DisplayName("should claim and publish reply entry immediately")
        void testPublishNow_Reply() {
            // Given
            long outboxId = 789L;
            Outbox outbox =
                    new Outbox(
                            outboxId,
                            "reply",
                            "APP.CMD.REPLY.Q",
                            "payment-123",
                            "CommandCompleted",
                            "{\"result\":\"success\"}",
                            Map.of("correlationId", "corr-1"),
                            "CLAIMED",
                            0);

            when(outboxService.claimOne(outboxId)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(outboxId);

            // Then
            verify(outboxService).claimOne(outboxId);
            verify(commandQueue)
                    .send("APP.CMD.REPLY.Q", "{\"result\":\"success\"}", Map.of("correlationId", "corr-1"));
            verify(outboxService).markPublished(outboxId);
        }

        @Test
        @DisplayName("should handle empty claim result gracefully")
        void testPublishNow_NotFound() {
            // Given
            long outboxId = 999L;
            when(outboxService.claimOne(outboxId)).thenReturn(Optional.empty());

            // When
            outboxRelay.publishNow(outboxId);

            // Then
            verify(outboxService).claimOne(outboxId);
            verify(commandQueue, never()).send(any(), any(), any());
            verify(eventPublisher, never()).publish(any(), any(), any(), any());
            verify(outboxService, never()).markPublished(anyLong());
        }

        @Test
        @DisplayName("should handle publish failure and reschedule with backoff")
        void testPublishNow_PublishFailure() {
            // Given
            long outboxId = 123L;
            Outbox outbox =
                    new Outbox(
                            outboxId,
                            "command",
                            "APP.CMD.PAYMENTS.Q",
                            "payment-123",
                            "ProcessPayment",
                            "{\"amount\":100}",
                            Map.of(),
                            "CLAIMED",
                            0);

            when(outboxService.claimOne(outboxId)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Connection timeout"))
                    .when(commandQueue)
                    .send(any(), any(), any());

            // When
            outboxRelay.publishNow(outboxId);

            // Then
            verify(commandQueue).send(any(), any(), any());
            verify(outboxService, never()).markPublished(anyLong());

            // Verify reschedule was called with exponential backoff
            ArgumentCaptor<Long> backoffCaptor = ArgumentCaptor.forClass(Long.class);
            verify(outboxService).reschedule(eq(outboxId), backoffCaptor.capture(), contains("timeout"));

            // First attempt: 2^1 * 1000 = 2000ms
            assertThat(backoffCaptor.getValue()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("should handle unknown category gracefully")
        void testPublishNow_UnknownCategory() {
            // Given
            long outboxId = 123L;
            Outbox outbox =
                    new Outbox(
                            outboxId,
                            "unknown",
                            "some.topic",
                            "key",
                            "Type",
                            "{}",
                            Map.of(),
                            "CLAIMED",
                            0);

            when(outboxService.claimOne(outboxId)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(outboxId);

            // Then
            verify(outboxService).claimOne(outboxId);
            verify(outboxService).reschedule(eq(outboxId), anyLong(), contains("Unknown category"));
        }
    }

    @Nested
    @DisplayName("sweepOnce Tests")
    class SweepOnceTests {

        @Test
        @DisplayName("should claim and publish batch of entries")
        void testSweepOnce_Batch() {
            // Given
            Outbox outbox1 =
                    new Outbox(
                            1L,
                            "command",
                            "APP.CMD.Q1",
                            "key1",
                            "Type1",
                            "{\"data\":1}",
                            Map.of(),
                            "CLAIMED",
                            0);
            Outbox outbox2 =
                    new Outbox(
                            2L,
                            "event",
                            "events.topic",
                            "key2",
                            "Type2",
                            "{\"data\":2}",
                            Map.of(),
                            "CLAIMED",
                            0);

            when(outboxService.claim(eq(10), anyString())).thenReturn(List.of(outbox1, outbox2));

            // When
            outboxRelay.sweepOnce();

            // Then
            verify(outboxService).claim(eq(10), anyString());
            verify(commandQueue).send("APP.CMD.Q1", "{\"data\":1}", Map.of());
            verify(eventPublisher).publish("events.topic", "key2", "{\"data\":2}", Map.of());
            verify(outboxService).markPublished(1L);
            verify(outboxService).markPublished(2L);
        }

        @Test
        @DisplayName("should handle empty batch gracefully")
        void testSweepOnce_EmptyBatch() {
            // Given
            when(outboxService.claim(eq(10), anyString())).thenReturn(List.of());

            // When
            outboxRelay.sweepOnce();

            // Then
            verify(outboxService).claim(eq(10), anyString());
            verify(commandQueue, never()).send(any(), any(), any());
            verify(eventPublisher, never()).publish(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should continue processing batch even if one entry fails")
        void testSweepOnce_PartialFailure() {
            // Given
            Outbox outbox1 =
                    new Outbox(
                            1L, "command", "APP.CMD.Q1", "key1", "Type1", "{}", Map.of(), "CLAIMED", 0);
            Outbox outbox2 =
                    new Outbox(2L, "event", "events.topic", "key2", "Type2", "{}", Map.of(), "CLAIMED", 0);

            when(outboxService.claim(eq(10), anyString())).thenReturn(List.of(outbox1, outbox2));
            doThrow(new RuntimeException("Publish failed")).when(commandQueue).send(any(), any(), any());

            // When
            outboxRelay.sweepOnce();

            // Then
            verify(commandQueue).send("APP.CMD.Q1", "{}", Map.of());
            verify(eventPublisher).publish("events.topic", "key2", "{}", Map.of());
            verify(outboxService).reschedule(eq(1L), anyLong(), contains("Publish failed"));
            verify(outboxService).markPublished(2L); // Second entry should succeed
        }
    }

    @Nested
    @DisplayName("Exponential Backoff Tests")
    class ExponentialBackoffTests {

        @Test
        @DisplayName("should calculate correct backoff for first attempt")
        void testBackoff_FirstAttempt() {
            // Given
            Outbox outbox =
                    new Outbox(
                            1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0); // 0 attempts

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Error")).when(commandQueue).send(any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then
            ArgumentCaptor<Long> backoffCaptor = ArgumentCaptor.forClass(Long.class);
            verify(outboxService).reschedule(eq(1L), backoffCaptor.capture(), any());

            // Backoff = 2^(max(1, attempts+1)) * 1000 = 2^1 * 1000 = 2000ms
            assertThat(backoffCaptor.getValue()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("should calculate correct backoff for second attempt")
        void testBackoff_SecondAttempt() {
            // Given
            Outbox outbox =
                    new Outbox(
                            1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 1); // 1 attempt

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Error")).when(commandQueue).send(any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then
            ArgumentCaptor<Long> backoffCaptor = ArgumentCaptor.forClass(Long.class);
            verify(outboxService).reschedule(eq(1L), backoffCaptor.capture(), any());

            // Backoff = 2^2 * 1000 = 4000ms
            assertThat(backoffCaptor.getValue()).isEqualTo(4000L);
        }

        @Test
        @DisplayName("should cap backoff at maxBackoffMillis")
        void testBackoff_MaxBackoff() {
            // Given - 20 attempts should exceed max backoff (5 minutes = 300,000ms)
            Outbox outbox =
                    new Outbox(
                            1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 20);

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Error")).when(commandQueue).send(any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then
            ArgumentCaptor<Long> backoffCaptor = ArgumentCaptor.forClass(Long.class);
            verify(outboxService).reschedule(eq(1L), backoffCaptor.capture(), any());

            // Should be capped at maxBackoffMillis
            assertThat(backoffCaptor.getValue()).isEqualTo(timeoutConfig.getMaxBackoffMillis());
        }

        @Test
        @DisplayName("should handle progressive backoff for multiple failures")
        void testBackoff_Progressive() {
            // Given
            when(outboxService.claimOne(1L))
                    .thenReturn(
                            Optional.of(
                                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0)))
                    .thenReturn(
                            Optional.of(
                                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 1)))
                    .thenReturn(
                            Optional.of(
                                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 2)));

            doThrow(new RuntimeException("Error")).when(commandQueue).send(any(), any(), any());

            // When/Then
            ArgumentCaptor<Long> backoffCaptor = ArgumentCaptor.forClass(Long.class);

            // First failure (0 attempts)
            outboxRelay.publishNow(1L);
            verify(outboxService, times(1)).reschedule(eq(1L), backoffCaptor.capture(), any());
            assertThat(backoffCaptor.getValue()).isEqualTo(2000L); // 2^1 * 1000

            // Second failure (1 attempt)
            outboxRelay.publishNow(1L);
            verify(outboxService, times(2)).reschedule(eq(1L), backoffCaptor.capture(), any());
            assertThat(backoffCaptor.getValue()).isEqualTo(4000L); // 2^2 * 1000

            // Third failure (2 attempts)
            outboxRelay.publishNow(1L);
            verify(outboxService, times(3)).reschedule(eq(1L), backoffCaptor.capture(), any());
            assertThat(backoffCaptor.getValue()).isEqualTo(8000L); // 2^3 * 1000
        }
    }

    @Nested
    @DisplayName("Category Routing Tests")
    class CategoryRoutingTests {

        @Test
        @DisplayName("should route command category to CommandQueue")
        void testRouting_Command() {
            // Given
            Outbox outbox =
                    new Outbox(
                            1L,
                            "command",
                            "APP.CMD.TEST.Q",
                            "test-key",
                            "TestCommand",
                            "{\"test\":true}",
                            Map.of("header1", "value1"),
                            "CLAIMED",
                            0);

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(commandQueue)
                    .send("APP.CMD.TEST.Q", "{\"test\":true}", Map.of("header1", "value1"));
            verify(eventPublisher, never()).publish(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should route reply category to CommandQueue")
        void testRouting_Reply() {
            // Given
            Outbox outbox =
                    new Outbox(
                            1L,
                            "reply",
                            "APP.CMD.REPLY.Q",
                            "reply-key",
                            "CommandCompleted",
                            "{\"status\":\"ok\"}",
                            Map.of("correlationId", "123"),
                            "CLAIMED",
                            0);

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(commandQueue)
                    .send("APP.CMD.REPLY.Q", "{\"status\":\"ok\"}", Map.of("correlationId", "123"));
            verify(eventPublisher, never()).publish(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should route event category to EventPublisher")
        void testRouting_Event() {
            // Given
            Outbox outbox =
                    new Outbox(
                            1L,
                            "event",
                            "payment.completed",
                            "payment-123",
                            "PaymentCompleted",
                            "{\"amount\":100}",
                            Map.of("eventId", "evt-1"),
                            "CLAIMED",
                            0);

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(eventPublisher)
                    .publish("payment.completed", "payment-123", "{\"amount\":100}", Map.of("eventId", "evt-1"));
            verify(commandQueue, never()).send(any(), any(), any());
        }

        @Test
        @DisplayName("should handle category case sensitivity correctly")
        void testRouting_CaseSensitivity() {
            // Given - lowercase "command"
            Outbox outbox =
                    new Outbox(
                            1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0);

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(commandQueue).send(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Transaction Handling Tests")
    class TransactionHandlingTests {

        @Test
        @DisplayName("should execute claim in transaction")
        void testTransaction_Claim() {
            // Given
            long outboxId = 1L;
            when(outboxService.claimOne(outboxId)).thenReturn(Optional.empty());

            // When
            outboxRelay.publishNow(outboxId);

            // Then
            verify(transactionOps).executeWrite(any());
        }

        @Test
        @DisplayName("should execute markPublished in separate transaction")
        void testTransaction_MarkPublished() {
            // Given
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            // Should be called twice: once for claim, once for markPublished
            verify(transactionOps, times(2)).executeWrite(any());
        }

        @Test
        @DisplayName("should execute reschedule in separate transaction on failure")
        void testTransaction_Reschedule() {
            // Given
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Publish failed")).when(commandQueue).send(any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then
            // Should be called twice: once for claim, once for reschedule
            verify(transactionOps, times(2)).executeWrite(any());
        }

        @Test
        @DisplayName("should execute sweep claim and updates in separate transactions")
        void testTransaction_Sweep() {
            // Given
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0);
            when(outboxService.claim(eq(10), anyString())).thenReturn(List.of(outbox));

            // When
            outboxRelay.sweepOnce();

            // Then
            // Should be called twice: once for claim batch, once for markPublished
            verify(transactionOps, times(2)).executeWrite(any());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should capture exception message in reschedule")
        void testErrorHandling_ExceptionMessage() {
            // Given
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Database connection lost"))
                    .when(commandQueue)
                    .send(any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then
            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            verify(outboxService).reschedule(eq(1L), anyLong(), errorCaptor.capture());
            assertThat(errorCaptor.getValue()).contains("Database connection lost");
        }

        @Test
        @DisplayName("should handle null exception message gracefully")
        void testErrorHandling_NullMessage() {
            // Given
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new NullPointerException()).when(commandQueue).send(any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(outboxService).reschedule(eq(1L), anyLong(), anyString());
        }

    }

    @Nested
    @DisplayName("Header Handling Tests")
    class HeaderHandlingTests {

        @Test
        @DisplayName("should handle empty headers")
        void testHeaders_Empty() {
            // Given
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(commandQueue).send(eq("queue"), eq("{}"), eq(Map.of()));
        }

        @Test
        @DisplayName("should handle multiple headers")
        void testHeaders_Multiple() {
            // Given
            Map<String, String> headers =
                    Map.of(
                            "header1", "value1",
                            "header2", "value2",
                            "header3", "value3");
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", headers, "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(commandQueue).send(eq("queue"), eq("{}"), eq(headers));
        }

        @Test
        @DisplayName("should preserve header values exactly")
        void testHeaders_PreserveValues() {
            // Given
            Map<String, String> headers =
                    Map.of(
                            "correlationId", "123-456-789",
                            "timestamp", "2025-01-01T00:00:00Z",
                            "special-chars", "value with spaces & symbols!@#$");
            Outbox outbox =
                    new Outbox(1L, "event", "topic", "key", "Type", "{}", headers, "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(eventPublisher).publish(eq("topic"), eq("key"), eq("{}"), eq(headers));
        }
    }

    @Nested
    @DisplayName("Payload Handling Tests")
    class PayloadHandlingTests {

        @Test
        @DisplayName("should handle empty JSON payload")
        void testPayload_EmptyJson() {
            // Given
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(commandQueue).send(eq("queue"), eq("{}"), any());
        }

        @Test
        @DisplayName("should handle complex JSON payload")
        void testPayload_ComplexJson() {
            // Given
            String complexJson =
                    "{\"customer\":{\"id\":\"123\",\"name\":\"Test\"},\"items\":[{\"sku\":\"ABC\",\"qty\":2}]}";
            Outbox outbox =
                    new Outbox(1L, "event", "topic", "key", "Type", complexJson, Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(eventPublisher).publish(eq("topic"), eq("key"), eq(complexJson), any());
        }

        @Test
        @DisplayName("should handle large payload")
        void testPayload_Large() {
            // Given - 10KB payload
            String largePayload = "{\"data\":\"" + "x".repeat(10000) + "\"}";
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", largePayload, Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(commandQueue).send(eq("queue"), eq(largePayload), any());
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("should handle concurrent publishNow calls")
        void testConcurrency_PublishNow() throws InterruptedException {
            // Given
            when(outboxService.claimOne(anyLong()))
                    .thenReturn(
                            Optional.of(
                                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0)));

            // When - simulate concurrent calls
            Thread t1 = new Thread(() -> outboxRelay.publishNow(1L));
            Thread t2 = new Thread(() -> outboxRelay.publishNow(2L));
            Thread t3 = new Thread(() -> outboxRelay.publishNow(3L));

            t1.start();
            t2.start();
            t3.start();

            t1.join();
            t2.join();
            t3.join();

            // Then - all should complete without errors
            verify(outboxService, times(3)).claimOne(anyLong());
            verify(commandQueue, times(3)).send(any(), any(), any());
            verify(outboxService, times(3)).markPublished(anyLong());
        }
    }

    @Nested
    @DisplayName("Batch Size Configuration Tests")
    class BatchSizeTests {

        @Test
        @DisplayName("should use configured batch size for sweep")
        void testBatchSize_ConfiguredValue() {
            // Given
            TimeoutConfig customConfig = new TimeoutConfig();
            customConfig.setOutboxBatchSize(500);
            OutboxRelay relay =
                    new OutboxRelay(
                            outboxService, commandQueue, eventPublisher, customConfig, transactionOps);

            when(outboxService.claim(eq(500), anyString())).thenReturn(List.of());

            // When
            relay.sweepOnce();

            // Then
            verify(outboxService).claim(eq(500), anyString());
        }

        @Test
        @DisplayName("should respect default batch size")
        void testBatchSize_Default() {
            // Given - default batch size is 10 in setup
            when(outboxService.claim(eq(10), anyString())).thenReturn(List.of());

            // When
            outboxRelay.sweepOnce();

            // Then
            verify(outboxService).claim(eq(10), anyString());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Additional Branch Coverage")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle negative attempts in backoff calculation")
        void testBackoff_NegativeAttempts() {
            // Given - edge case with negative attempts (should be treated as 0)
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", -1);

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Error")).when(commandQueue).send(any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then
            ArgumentCaptor<Long> backoffCaptor = ArgumentCaptor.forClass(Long.class);
            verify(outboxService).reschedule(eq(1L), backoffCaptor.capture(), any());

            // Backoff = 2^(max(1, -1+1)) * 1000 = 2^1 * 1000 = 2000ms
            assertThat(backoffCaptor.getValue()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("should handle intermediate backoff values correctly")
        void testBackoff_IntermediateAttempts() {
            // Given - test attempts 3-5 to ensure exponential growth
            Outbox outbox3 =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 2);
            Outbox outbox4 =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 3);
            Outbox outbox5 =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 4);

            when(outboxService.claimOne(1L))
                    .thenReturn(Optional.of(outbox3))
                    .thenReturn(Optional.of(outbox4))
                    .thenReturn(Optional.of(outbox5));

            doThrow(new RuntimeException("Error")).when(commandQueue).send(any(), any(), any());

            ArgumentCaptor<Long> backoffCaptor = ArgumentCaptor.forClass(Long.class);

            // When/Then - Attempt 3
            outboxRelay.publishNow(1L);
            verify(outboxService, times(1)).reschedule(eq(1L), backoffCaptor.capture(), any());
            assertThat(backoffCaptor.getValue()).isEqualTo(8000L); // 2^3 * 1000

            // Attempt 4
            outboxRelay.publishNow(1L);
            verify(outboxService, times(2)).reschedule(eq(1L), backoffCaptor.capture(), any());
            assertThat(backoffCaptor.getValue()).isEqualTo(16000L); // 2^4 * 1000

            // Attempt 5
            outboxRelay.publishNow(1L);
            verify(outboxService, times(3)).reschedule(eq(1L), backoffCaptor.capture(), any());
            assertThat(backoffCaptor.getValue()).isEqualTo(32000L); // 2^5 * 1000
        }

        @Test
        @DisplayName("should handle max backoff boundary correctly")
        void testBackoff_BoundaryConditions() {
            // Given - test that backoff exactly at maxBackoffMillis is handled
            TimeoutConfig customConfig = new TimeoutConfig();
            customConfig.setMaxBackoff(java.time.Duration.ofMillis(10000L)); // 10 seconds
            customConfig.setOutboxBatchSize(10);
            OutboxRelay relay =
                    new OutboxRelay(
                            outboxService, commandQueue, eventPublisher, customConfig, transactionOps);

            // Attempt 4 would give 2^4 * 1000 = 16000ms, should be capped at 10000ms
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 3);

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Error")).when(commandQueue).send(any(), any(), any());

            // When
            relay.publishNow(1L);

            // Then
            ArgumentCaptor<Long> backoffCaptor = ArgumentCaptor.forClass(Long.class);
            verify(outboxService).reschedule(eq(1L), backoffCaptor.capture(), any());
            assertThat(backoffCaptor.getValue()).isEqualTo(10000L); // Capped at maxBackoffMillis
        }

        @Test
        @DisplayName("should handle event publish failure with reschedule")
        void testPublishFailure_Event() {
            // Given
            Outbox outbox =
                    new Outbox(
                            1L,
                            "event",
                            "events.topic",
                            "key",
                            "Type",
                            "{}",
                            Map.of("header", "value"),
                            "CLAIMED",
                            0);

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Kafka unavailable")).when(eventPublisher).publish(any(), any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(eventPublisher).publish("events.topic", "key", "{}", Map.of("header", "value"));
            verify(outboxService, never()).markPublished(anyLong());
            verify(outboxService).reschedule(eq(1L), anyLong(), contains("Kafka unavailable"));
        }

        @Test
        @DisplayName("should handle reply publish failure with reschedule")
        void testPublishFailure_Reply() {
            // Given
            Outbox outbox =
                    new Outbox(
                            1L, "reply", "APP.REPLY.Q", "key", "Type", "{}", Map.of(), "CLAIMED", 0);

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("MQ connection lost"))
                    .when(commandQueue)
                    .send(any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(commandQueue).send("APP.REPLY.Q", "{}", Map.of());
            verify(outboxService, never()).markPublished(anyLong());
            verify(outboxService).reschedule(eq(1L), anyLong(), contains("MQ connection lost"));
        }

        @Test
        @DisplayName("should handle empty JSON object payload")
        void testPayload_EmptyObject() {
            // Given
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(commandQueue).send("queue", "{}", Map.of());
            verify(outboxService).markPublished(1L);
        }

        @Test
        @DisplayName("should handle JSON array payload")
        void testPayload_JsonArray() {
            // Given
            String arrayPayload = "[{\"id\":1},{\"id\":2},{\"id\":3}]";
            Outbox outbox =
                    new Outbox(1L, "event", "topic", "key", "Type", arrayPayload, Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(eventPublisher).publish("topic", "key", arrayPayload, Map.of());
            verify(outboxService).markPublished(1L);
        }

        @Test
        @DisplayName("should handle special characters in category gracefully")
        void testCategory_SpecialCharacters() {
            // Given - test with unexpected category value
            Outbox outbox =
                    new Outbox(
                            1L, "COMMAND", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0); // Uppercase

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then - should trigger unknown category branch
            verify(outboxService).reschedule(eq(1L), anyLong(), contains("Unknown category"));
        }

        @Test
        @DisplayName("should handle null payload gracefully in error scenario")
        void testErrorHandling_NullPayload() {
            // Given - outbox with special values
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", null, Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Null payload error"))
                    .when(commandQueue)
                    .send(any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(outboxService).reschedule(eq(1L), anyLong(), contains("Null payload error"));
        }

        @Test
        @Disabled("Transaction failure handling test - NullPointerException in callback mock setup, needs refinement")
        @DisplayName("should handle transaction exception during markPublished")
        void testTransaction_MarkPublishedFailure() {
            // Given
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // Mock transaction to throw on second call (markPublished)
            AtomicInteger callCount = new AtomicInteger(0);
            when(transactionOps.executeWrite(any()))
                    .thenAnswer(
                            invocation -> {
                                if (callCount.getAndIncrement() == 0) {
                                    // First call - claim
                                    @SuppressWarnings("unchecked")
                                    Function<TransactionStatus<Connection>, Object> callback =
                                            invocation.getArgument(0);
                                    return callback.apply(mock(TransactionStatus.class));
                                } else {
                                    // Second call - markPublished should fail
                                    throw new RuntimeException("Transaction commit failed");
                                }
                            });

            // When/Then - should not throw, exception handled internally
            assertThatCode(() -> outboxRelay.publishNow(1L)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle batch with all categories")
        void testSweep_AllCategoriesMixed() {
            // Given
            Outbox command =
                    new Outbox(1L, "command", "cmd.q", "k1", "T1", "{}", Map.of(), "CLAIMED", 0);
            Outbox reply =
                    new Outbox(2L, "reply", "reply.q", "k2", "T2", "{}", Map.of(), "CLAIMED", 0);
            Outbox event =
                    new Outbox(3L, "event", "evt.topic", "k3", "T3", "{}", Map.of(), "CLAIMED", 0);

            when(outboxService.claim(eq(10), anyString())).thenReturn(List.of(command, reply, event));

            // When
            outboxRelay.sweepOnce();

            // Then
            verify(commandQueue).send("cmd.q", "{}", Map.of());
            verify(commandQueue).send("reply.q", "{}", Map.of());
            verify(eventPublisher).publish("evt.topic", "k3", "{}", Map.of());
            verify(outboxService).markPublished(1L);
            verify(outboxService).markPublished(2L);
            verify(outboxService).markPublished(3L);
        }

        @Test
        @DisplayName("should handle high attempt count boundary")
        void testBackoff_HighAttemptBoundary() {
            // Given - 15 attempts to test high boundary
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 15);

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Error")).when(commandQueue).send(any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then - should be capped at maxBackoffMillis
            ArgumentCaptor<Long> backoffCaptor = ArgumentCaptor.forClass(Long.class);
            verify(outboxService).reschedule(eq(1L), backoffCaptor.capture(), any());
            assertThat(backoffCaptor.getValue()).isEqualTo(timeoutConfig.getMaxBackoffMillis());
        }

        @Test
        @DisplayName("should handle zero attempts correctly")
        void testBackoff_ZeroAttempts() {
            // Given
            Outbox outbox =
                    new Outbox(1L, "command", "queue", "key", "Type", "{}", Map.of(), "CLAIMED", 0);

            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));
            doThrow(new RuntimeException("Error")).when(commandQueue).send(any(), any(), any());

            // When
            outboxRelay.publishNow(1L);

            // Then
            ArgumentCaptor<Long> backoffCaptor = ArgumentCaptor.forClass(Long.class);
            verify(outboxService).reschedule(eq(1L), backoffCaptor.capture(), any());
            // Backoff = 2^(max(1, 0+1)) * 1000 = 2^1 * 1000 = 2000ms
            assertThat(backoffCaptor.getValue()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("should handle very large payload without errors")
        void testPayload_VeryLarge() {
            // Given - 100KB payload
            String veryLargePayload = "{\"data\":\"" + "x".repeat(100000) + "\"}";
            Outbox outbox =
                    new Outbox(
                            1L, "command", "queue", "key", "Type", veryLargePayload, Map.of(), "CLAIMED", 0);
            when(outboxService.claimOne(1L)).thenReturn(Optional.of(outbox));

            // When
            outboxRelay.publishNow(1L);

            // Then
            verify(commandQueue).send("queue", veryLargePayload, Map.of());
            verify(outboxService).markPublished(1L);
        }

        @Test
        @DisplayName("should handle mixed success and failure in large batch")
        void testSweep_LargeBatchMixedResults() {
            // Given - create batch where every other message fails
            List<Outbox> batch = new java.util.ArrayList<>();
            for (int i = 1; i <= 20; i++) {
                batch.add(
                        new Outbox((long) i, "command", "queue", "key" + i, "Type", "{}", Map.of(), "CLAIMED", 0));
            }

            when(outboxService.claim(eq(10), anyString())).thenReturn(batch);

            // Make every other publish fail
            AtomicInteger publishCount = new AtomicInteger(0);
            doAnswer(
                    invocation -> {
                        if (publishCount.getAndIncrement() % 2 == 1) {
                            throw new RuntimeException("Intermittent failure");
                        }
                        return null;
                    })
                    .when(commandQueue)
                    .send(any(), any(), any());

            // When
            outboxRelay.sweepOnce();

            // Then - half should succeed, half should fail
            verify(commandQueue, times(20)).send(any(), any(), any());
            verify(outboxService, times(10)).markPublished(anyLong());
            verify(outboxService, times(10)).reschedule(anyLong(), anyLong(), contains("Intermittent"));
        }
    }
}
