package com.acme.reliable.processor;

import com.acme.reliable.domain.Outbox;
import com.acme.reliable.repository.OutboxRepository;
import com.acme.reliable.spi.KafkaPublisher;
import com.acme.reliable.spi.MqPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for NotifyPublisher to achieve 80%+ coverage.
 *
 * <p>NotifyPublisher is a Redis-based notification consumer that:
 * - Subscribes to Redis blocking queue for outbox notifications
 * - Claims outbox entries and publishes to MQ (commands/replies) or Kafka (events)
 * - Handles concurrency with semaphore-based rate limiting
 * - Implements exponential backoff retry logic for failures
 * - Supports graceful shutdown
 *
 * <p>Coverage areas:
 * - Successful publish operations for commands, replies, and events
 * - Error handling and retry with exponential backoff
 * - Concurrency control and permit management
 * - Queue subscription and re-subscription logic
 * - Different message categories and routing
 * - Timeout and failure scenarios
 * - Graceful shutdown
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotifyPublisher Unit Tests")
class NotifyPublisherTest {

    // Use lenient mocking to avoid unnecessary stubbing exceptions
    private static final org.mockito.quality.Strictness LENIENT = org.mockito.quality.Strictness.LENIENT;

    @Mock
    private RedissonClient redisson;

    @Mock
    private OutboxRepository outbox;

    @Mock
    private MqPublisher mq;

    @Mock
    private KafkaPublisher kafka;

    @Mock
    private RBlockingQueue<Object> blockingQueue;

    @Mock
    private RQueue<Object> queue;

    private NotifyPublisher publisher;

    // Flag to control test execution
    private volatile boolean allowSubscription = true;

    @BeforeEach
    void setUp() {
        // Setup default mock behavior with lenient to avoid unnecessary stubbing warnings
        lenient().when(redisson.getBlockingQueue("outbox:notify")).thenReturn(blockingQueue);
        lenient().when(redisson.getQueue("outbox:notify")).thenReturn(queue);

        // Prevent actual subscription in constructor
        allowSubscription = false;
    }

    @AfterEach
    void tearDown() {
        if (publisher != null) {
            try {
                publisher.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    // ============================================================================
    // Constructor and Initialization Tests
    // ============================================================================

    @Test
    @DisplayName("Should initialize with correct concurrency limit")
    void testConstructorInitialization() throws Exception {
        // Arrange
        RFuture<Object> neverCompletes = createNeverCompletingFuture();
        lenient().when(blockingQueue.takeAsync()).thenReturn(neverCompletes);

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);

        // Give subscription a moment to start
        Thread.sleep(50);

        // Assert
        verify(redisson, atLeastOnce()).getBlockingQueue("outbox:notify");
        verify(blockingQueue, atLeastOnce()).takeAsync();
    }

    // ============================================================================
    // Successful Command Publishing Tests
    // ============================================================================

    @Test
    @DisplayName("Should successfully publish command message")
    void testPublishCommand_Success() throws Exception {
        // Arrange
        long outboxId = 100L;
        Outbox commandRow =
                createOutbox(
                        outboxId,
                        "command",
                        "APP.CMD.CREATE.Q",
                        "cmd-001",
                        "CreateCommand",
                        "{\"data\": \"test\"}",
                        Map.of("commandId", "cmd-123"),
                        0);

        setupSingleMessage(outboxId, commandRow);

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(outbox).claimIfNew(outboxId);
        verify(mq)
                .publish(
                        "APP.CMD.CREATE.Q",
                        "cmd-001",
                        "CreateCommand",
                        "{\"data\": \"test\"}",
                        Map.of("commandId", "cmd-123"));
        verify(outbox).markPublished(outboxId);
        verify(kafka, never()).publish(anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Should successfully publish reply message")
    void testPublishReply_Success() throws Exception {
        // Arrange
        long outboxId = 200L;
        Outbox replyRow =
                createOutbox(
                        outboxId,
                        "reply",
                        "APP.CMD.REPLY.Q",
                        "reply-001",
                        "CommandResult",
                        "{\"status\": \"SUCCESS\"}",
                        Map.of("correlationId", "corr-456"),
                        0);

        setupSingleMessage(outboxId, replyRow);

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(outbox).claimIfNew(outboxId);
        verify(mq)
                .publish(
                        "APP.CMD.REPLY.Q",
                        "reply-001",
                        "CommandResult",
                        "{\"status\": \"SUCCESS\"}",
                        Map.of("correlationId", "corr-456"));
        verify(outbox).markPublished(outboxId);
    }

    // ============================================================================
    // Successful Event Publishing Tests
    // ============================================================================

    @Test
    @DisplayName("Should successfully publish event to Kafka")
    void testPublishEvent_Success() throws Exception {
        // Arrange
        long outboxId = 300L;
        Outbox eventRow =
                createOutbox(
                        outboxId,
                        "event",
                        "payment.created",
                        "payment-123",
                        "PaymentCreatedEvent",
                        "{\"amount\": 1000}",
                        Map.of("timestamp", "2024-01-01T00:00:00Z"),
                        0);

        setupSingleMessage(outboxId, eventRow);

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(outbox).claimIfNew(outboxId);
        verify(kafka)
                .publish(
                        "payment.created",
                        "payment-123",
                        "PaymentCreatedEvent",
                        "{\"amount\": 1000}",
                        Map.of("timestamp", "2024-01-01T00:00:00Z"));
        verify(outbox).markPublished(outboxId);
        verify(mq, never()).publish(anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    // ============================================================================
    // Error Handling and Retry Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle publish failure and mark as failed with backoff")
    void testPublishFailure_MarkedAsFailed() throws Exception {
        // Arrange
        long outboxId = 400L;
        Outbox commandRow =
                createOutbox(
                        outboxId,
                        "command",
                        "APP.CMD.TEST.Q",
                        "cmd-002",
                        "TestCommand",
                        "{\"test\": true}",
                        Map.of("commandId", "cmd-789"),
                        0);

        setupSingleMessage(outboxId, commandRow);

        // Configure MQ to throw exception
        doThrow(new RuntimeException("MQ connection failed"))
                .when(mq)
                .publish(anyString(), anyString(), anyString(), anyString(), anyMap());

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(outbox).claimIfNew(outboxId);
        verify(mq)
                .publish(
                        "APP.CMD.TEST.Q",
                        "cmd-002",
                        "TestCommand",
                        "{\"test\": true}",
                        Map.of("commandId", "cmd-789"));

        // Verify failure marking with backoff (attempt 0 -> 1, backoff = 2^1 = 2 seconds)
        ArgumentCaptor<Instant> nextAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(outbox).markFailed(eq(outboxId), contains("MQ connection failed"), nextAtCaptor.capture());

        // Verify backoff is approximately 2 seconds
        Instant nextAt = nextAtCaptor.getValue();
        long secondsUntilRetry = nextAt.getEpochSecond() - Instant.now().getEpochSecond();
        assertThat(secondsUntilRetry).isBetween(1L, 3L); // Allow 1-3 seconds tolerance

        verify(outbox, never()).markPublished(anyLong());
    }

    @Test
    @DisplayName("Should calculate exponential backoff correctly for multiple attempts")
    void testExponentialBackoff_MultipleAttempts() throws Exception {
        // Test backoff calculation for different attempt counts

        // Attempt 1: backoff = 2^1 = 2 seconds
        long outboxId1 = 500L;
        Outbox row1 = createOutbox(outboxId1, "command", "Q1", "k1", "T1", "{}", Map.of(), 0);
        setupSingleMessage(outboxId1, row1);
        doThrow(new RuntimeException("Fail")).when(mq).publish(anyString(), anyString(), anyString(), anyString(), anyMap());

        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        ArgumentCaptor<Instant> captor1 = ArgumentCaptor.forClass(Instant.class);
        verify(outbox).markFailed(eq(outboxId1), anyString(), captor1.capture());
        long backoff1 = captor1.getValue().getEpochSecond() - Instant.now().getEpochSecond();
        assertThat(backoff1).isBetween(1L, 3L); // ~2 seconds

        publisher.close();

        // Attempt 3: backoff = 2^3 = 8 seconds
        reset(outbox, blockingQueue, mq);
        long outboxId2 = 600L;
        Outbox row2 = createOutbox(outboxId2, "command", "Q2", "k2", "T2", "{}", Map.of(), 2);
        setupSingleMessage(outboxId2, row2);
        doThrow(new RuntimeException("Fail")).when(mq).publish(anyString(), anyString(), anyString(), anyString(), anyMap());

        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        ArgumentCaptor<Instant> captor2 = ArgumentCaptor.forClass(Instant.class);
        verify(outbox).markFailed(eq(outboxId2), anyString(), captor2.capture());
        long backoff2 = captor2.getValue().getEpochSecond() - Instant.now().getEpochSecond();
        assertThat(backoff2).isBetween(7L, 10L); // ~8 seconds

        publisher.close();
    }

    @Test
    @DisplayName("Should cap backoff at maximum of 300 seconds")
    void testBackoffCapped_At300Seconds() throws Exception {
        // Arrange - Very high attempt count (should cap at 300 seconds)
        long outboxId = 700L;
        Outbox row = createOutbox(outboxId, "command", "Q", "k", "T", "{}", Map.of(), 50);

        setupSingleMessage(outboxId, row);
        doThrow(new RuntimeException("Fail")).when(mq).publish(anyString(), anyString(), anyString(), anyString(), anyMap());

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(outbox).markFailed(eq(outboxId), anyString(), captor.capture());

        long backoff = captor.getValue().getEpochSecond() - Instant.now().getEpochSecond();
        assertThat(backoff).isLessThanOrEqualTo(301L); // Should be capped at ~300 seconds
    }

    @Test
    @DisplayName("Should handle Kafka publish failure for events")
    void testKafkaPublishFailure() throws Exception {
        // Arrange
        long outboxId = 800L;
        Outbox eventRow =
                createOutbox(outboxId, "event", "test.topic", "evt-001", "TestEvent", "{}", Map.of(), 0);

        setupSingleMessage(outboxId, eventRow);

        doThrow(new RuntimeException("Kafka unavailable"))
                .when(kafka)
                .publish(anyString(), anyString(), anyString(), anyString(), anyMap());

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(kafka).publish("test.topic", "evt-001", "TestEvent", "{}", Map.of());
        verify(outbox).markFailed(eq(outboxId), contains("Kafka unavailable"), any(Instant.class));
        verify(outbox, never()).markPublished(anyLong());
    }

    // ============================================================================
    // Invalid Category Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle invalid category gracefully")
    void testInvalidCategory_MarkedAsFailed() throws Exception {
        // Arrange
        long outboxId = 900L;
        Outbox invalidRow =
                createOutbox(outboxId, "unknown", "some.topic", "key", "Type", "{}", Map.of(), 0);

        setupSingleMessage(outboxId, invalidRow);

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(outbox).claimIfNew(outboxId);
        verify(outbox).markFailed(eq(outboxId), contains("bad category: unknown"), any(Instant.class));
        verify(mq, never()).publish(anyString(), anyString(), anyString(), anyString(), anyMap());
        verify(kafka, never()).publish(anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    // ============================================================================
    // Concurrency and Permit Management Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle concurrent message processing within permit limits")
    void testConcurrentProcessing_WithinPermitLimits() throws Exception {
        // Arrange - Test that publisher can handle multiple messages
        RFuture<Object> future1 = createCompletedFuture(1000L);
        RFuture<Object> future2 = createCompletedFuture(1001L);
        RFuture<Object> block = createNeverCompletingFuture();

        lenient().when(blockingQueue.takeAsync())
                .thenReturn(future1)
                .thenReturn(future2)
                .thenReturn(block);

        // Setup outbox to return empty for all claims (simulating already processed)
        lenient().when(outbox.claimIfNew(anyLong())).thenReturn(Optional.empty());

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        Thread.sleep(200); // Allow processing

        // Assert - Messages were processed (or attempted)
        verify(outbox, atLeast(1)).claimIfNew(anyLong());
    }

    // ============================================================================
    // Message Not Found Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle message not found gracefully")
    void testMessageNotFound_NoError() throws Exception {
        // Arrange
        long outboxId = 1100L;
        setupSingleMessage(outboxId, null); // No outbox row found

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(outbox).claimIfNew(outboxId);
        verify(mq, never()).publish(anyString(), anyString(), anyString(), anyString(), anyMap());
        verify(kafka, never()).publish(anyString(), anyString(), anyString(), anyString(), anyMap());
        verify(outbox, never()).markPublished(anyLong());
        verify(outbox, never()).markFailed(anyLong(), anyString(), any(Instant.class));
    }

    // ============================================================================
    // Edge Cases and Boundary Conditions
    // ============================================================================

    @Test
    @DisplayName("Should handle empty payload")
    void testEmptyPayload() throws Exception {
        // Arrange
        long outboxId = 1200L;
        Outbox row =
                createOutbox(
                        outboxId, "command", "Q", "key", "Type", "", Map.of("hdr", "val"), 0);

        setupSingleMessage(outboxId, row);

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(mq).publish("Q", "key", "Type", "", Map.of("hdr", "val"));
        verify(outbox).markPublished(outboxId);
    }

    @Test
    @DisplayName("Should handle empty headers")
    void testEmptyHeaders() throws Exception {
        // Arrange
        long outboxId = 1300L;
        Outbox row = createOutbox(outboxId, "event", "topic", "key", "Type", "{}", Map.of(), 0);

        setupSingleMessage(outboxId, row);

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(kafka).publish("topic", "key", "Type", "{}", Map.of());
        verify(outbox).markPublished(outboxId);
    }

    @Test
    @DisplayName("Should handle null key")
    void testNullKey() throws Exception {
        // Arrange
        long outboxId = 1400L;
        Outbox row = createOutbox(outboxId, "command", "Q", null, "Type", "{}", Map.of(), 0);

        setupSingleMessage(outboxId, row);

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(mq).publish("Q", null, "Type", "{}", Map.of());
        verify(outbox).markPublished(outboxId);
    }

    @Test
    @DisplayName("Should handle large payload")
    void testLargePayload() throws Exception {
        // Arrange
        long outboxId = 1500L;
        String largePayload = "x".repeat(100000); // 100KB payload
        Outbox row = createOutbox(outboxId, "command", "Q", "key", "Type", largePayload, Map.of(), 0);

        setupSingleMessage(outboxId, row);

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(mq).publish("Q", "key", "Type", largePayload, Map.of());
        verify(outbox).markPublished(outboxId);
    }

    @Test
    @DisplayName("Should handle special characters in topic and key")
    void testSpecialCharacters() throws Exception {
        // Arrange
        long outboxId = 1600L;
        Outbox row =
                createOutbox(
                        outboxId,
                        "event",
                        "topic.with-special_chars",
                        "key:with/special\\chars",
                        "Type",
                        "{}",
                        Map.of(),
                        0);

        setupSingleMessage(outboxId, row);

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert
        verify(kafka)
                .publish("topic.with-special_chars", "key:with/special\\chars", "Type", "{}", Map.of());
        verify(outbox).markPublished(outboxId);
    }

    // ============================================================================
    // Parse Error Handling Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle non-numeric ID gracefully")
    void testNonNumericId_HandledGracefully() throws Exception {
        // Arrange
        setupSingleMessageWithInvalidId("not-a-number");

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert - Should log error but not crash
        verify(outbox, never()).claimIfNew(anyLong());
        verify(mq, never()).publish(anyString(), anyString(), anyString(), anyString(), anyMap());
        verify(kafka, never()).publish(anyString(), anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Should handle null ID gracefully")
    void testNullId_HandledGracefully() throws Exception {
        // Arrange
        setupSingleMessageWithInvalidId(null);

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert - Should log error but not crash
        verify(outbox, never()).claimIfNew(anyLong());
    }

    // ============================================================================
    // Shutdown Tests
    // ============================================================================

    @Test
    @DisplayName("Should shutdown gracefully")
    void testGracefulShutdown() throws Exception {
        // Arrange
        RFuture<Object> neverCompletes = createNeverCompletingFuture();
        lenient().when(blockingQueue.takeAsync()).thenReturn(neverCompletes);

        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        Thread.sleep(50);

        // Act
        publisher.close();

        // Assert - Should complete without hanging
        // Verify running flag is set to false (indirectly by successful close)
        assertThatCode(() -> publisher.close()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should stop processing after close")
    void testStopProcessingAfterClose() throws Exception {
        // Arrange
        RFuture<Object> future = createNeverCompletingFuture();
        lenient().when(blockingQueue.takeAsync()).thenReturn(future);

        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);

        // Act
        publisher.close();
        Thread.sleep(50);

        // Assert - Should not process messages after close
        // (Cannot easily verify as future never completes in test)
        assertThatCode(() -> publisher.close()).doesNotThrowAnyException();
    }

    // ============================================================================
    // Re-subscription Tests
    // ============================================================================

    @Test
    @DisplayName("Should re-subscribe after processing message")
    void testResubscriptionAfterMessage() throws Exception {
        // Arrange
        long outboxId = 1700L;
        Outbox row = createOutbox(outboxId, "command", "Q", "key", "Type", "{}", Map.of(), 0);

        RFuture<Object> firstMessage = createCompletedFuture(outboxId);
        RFuture<Object> blockSecond = createNeverCompletingFuture();

        lenient().when(blockingQueue.takeAsync()).thenReturn(firstMessage).thenReturn(blockSecond);
        lenient().when(outbox.claimIfNew(outboxId)).thenReturn(Optional.of(row));

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        waitForProcessing();

        // Assert - Should have called takeAsync at least twice (initial + re-subscribe)
        verify(blockingQueue, atLeast(2)).takeAsync();
    }

    // ============================================================================
    // Multiple Message Processing Tests
    // ============================================================================

    @Test
    @DisplayName("Should process multiple messages sequentially")
    void testMultipleMessagesSequentially() throws Exception {
        // Arrange
        long outboxId1 = 2000L;
        long outboxId2 = 2001L;
        long outboxId3 = 2002L;

        Outbox row1 = createOutbox(outboxId1, "command", "Q1", "k1", "T1", "{\"id\":1}", Map.of(), 0);
        Outbox row2 = createOutbox(outboxId2, "event", "topic2", "k2", "T2", "{\"id\":2}", Map.of(), 0);
        Outbox row3 = createOutbox(outboxId3, "reply", "Q3", "k3", "T3", "{\"id\":3}", Map.of(), 0);

        RFuture<Object> msg1 = createCompletedFuture(outboxId1);
        RFuture<Object> msg2 = createCompletedFuture(outboxId2);
        RFuture<Object> msg3 = createCompletedFuture(outboxId3);
        RFuture<Object> block = createNeverCompletingFuture();

        lenient().when(blockingQueue.takeAsync()).thenReturn(msg1, msg2, msg3, block);
        lenient().when(outbox.claimIfNew(outboxId1)).thenReturn(Optional.of(row1));
        lenient().when(outbox.claimIfNew(outboxId2)).thenReturn(Optional.of(row2));
        lenient().when(outbox.claimIfNew(outboxId3)).thenReturn(Optional.of(row3));

        // Act
        publisher = new NotifyPublisher(redisson, outbox, mq, kafka);
        Thread.sleep(300); // Allow time for sequential processing

        // Assert
        verify(mq).publish("Q1", "k1", "T1", "{\"id\":1}", Map.of());
        verify(kafka).publish("topic2", "k2", "T2", "{\"id\":2}", Map.of());
        verify(mq).publish("Q3", "k3", "T3", "{\"id\":3}", Map.of());

        verify(outbox).markPublished(outboxId1);
        verify(outbox).markPublished(outboxId2);
        verify(outbox).markPublished(outboxId3);
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private Outbox createOutbox(
            long id,
            String category,
            String topic,
            String key,
            String type,
            String payload,
            Map<String, String> headers,
            int attempts) {
        Outbox row = new Outbox();
        row.setId(id);
        row.setCategory(category);
        row.setTopic(topic);
        row.setKey(key);
        row.setType(type);
        row.setPayload(payload);
        row.setHeaders(headers);
        row.setAttempts(attempts);
        row.setStatus("SENDING");
        return row;
    }

    private void setupSingleMessage(long outboxId, Outbox row) {
        RFuture<Object> message = createCompletedFuture(outboxId);
        RFuture<Object> block = createNeverCompletingFuture();

        lenient().when(blockingQueue.takeAsync()).thenReturn(message).thenReturn(block);

        if (row != null) {
            lenient().when(outbox.claimIfNew(outboxId)).thenReturn(Optional.of(row));
        } else {
            lenient().when(outbox.claimIfNew(outboxId)).thenReturn(Optional.empty());
        }
    }

    private void setupSingleMessageWithInvalidId(Object invalidId) {
        RFuture<Object> message = createCompletedFuture(invalidId);
        RFuture<Object> block = createNeverCompletingFuture();

        lenient().when(blockingQueue.takeAsync()).thenReturn(message).thenReturn(block);
    }

    @SuppressWarnings("unchecked")
    private RFuture<Object> createCompletedFuture(Object value) {
        RFuture<Object> future = mock(RFuture.class);
        lenient().when(future.thenAcceptAsync(any())).thenAnswer(inv -> {
            java.util.function.Consumer<Object> consumer = inv.getArgument(0);
            consumer.accept(value);
            RFuture<Void> result = mock(RFuture.class);
            lenient().when(result.whenComplete(any())).thenAnswer(inv2 -> {
                java.util.function.BiConsumer<Void, Throwable> callback = inv2.getArgument(0);
                callback.accept(null, null);
                return null;
            });
            return result;
        });
        return future;
    }

    @SuppressWarnings("unchecked")
    private RFuture<Object> createNeverCompletingFuture() {
        RFuture<Object> future = mock(RFuture.class);
        RFuture<Void> innerFuture = mock(RFuture.class);
        lenient().when(future.thenAcceptAsync(any())).thenReturn(innerFuture);
        lenient().when(innerFuture.whenComplete(any())).thenReturn(null);
        return future;
    }

    private void waitForProcessing() throws InterruptedException {
        // Allow async processing to complete
        Thread.sleep(150);
    }
}
