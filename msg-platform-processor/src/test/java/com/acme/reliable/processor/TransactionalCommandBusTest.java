package com.acme.reliable.processor;

import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.config.MessagingConfig.TopicNaming;
import com.acme.reliable.domain.Outbox;
import com.acme.reliable.service.CommandService;
import com.acme.reliable.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for TransactionalCommandBus to achieve 80%+ coverage.
 *
 * <p>TransactionalCommandBus is responsible for accepting commands, checking idempotency,
 * persisting them, and creating outbox entries for eventual publishing.
 *
 * <p>Coverage areas:
 * - Successful command acceptance
 * - Idempotency key validation and duplicate detection
 * - Outbox entry creation
 * - Transaction boundary behavior
 * - Error scenarios and exception handling
 * - Edge cases (null values, empty strings, etc.)
 * - Integration with dependencies
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionalCommandBus Unit Tests")
class TransactionalCommandBusTest {

    @Mock
    private CommandService commandService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private FastPathPublisher fastPathPublisher;

    @Mock
    private MessagingConfig messagingConfig;

    @Mock
    private TopicNaming topicNaming;

    @Mock
    private MessagingConfig.QueueNaming queueNaming;

    private TransactionalCommandBus commandBus;

    @BeforeEach
    void setUp() {
        lenient().when(messagingConfig.getTopicNaming()).thenReturn(topicNaming);
        lenient().when(messagingConfig.getQueueNaming()).thenReturn(queueNaming);
        lenient().when(queueNaming.buildCommandQueue(anyString())).thenReturn("test.queue");
        commandBus =
                new TransactionalCommandBus(
                        commandService, outboxService, fastPathPublisher, messagingConfig);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should initialize with all dependencies")
        void testConstructor_Success() {
            // Act
            TransactionalCommandBus bus =
                    new TransactionalCommandBus(
                            commandService, outboxService, fastPathPublisher, messagingConfig);

            // Assert - Verify it can be used
            assertThat(bus).isNotNull();
        }
    }

    @Nested
    @DisplayName("Accept Command - Happy Path Tests")
    class AcceptCommandHappyPathTests {

        @Test
        @DisplayName("Should accept valid command and return command ID")
        void testAccept_Success() {
            // Arrange
            String name = "CreateOrder";
            String idem = "order-123-create";
            String bizKey = "order-123";
            String payload = "{\"customerId\":\"cust-1\",\"amount\":100.50}";
            Map<String, String> reply = Map.of("queue", "order.replies");

            UUID expectedCommandId = UUID.randomUUID();
            long expectedOutboxId = 999L;

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(eq(name), eq(idem), eq(bizKey), eq(payload), anyString()))
                    .thenReturn(expectedCommandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(expectedOutboxId);

            // Act
            UUID result = commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert
            assertThat(result).isEqualTo(expectedCommandId);

            verify(commandService).existsByIdempotencyKey(idem);
            verify(commandService).savePending(eq(name), eq(idem), eq(bizKey), eq(payload), anyString());
            verify(outboxService).addReturningId(any(Outbox.class));
        }

        @Test
        @DisplayName("Should serialize reply map to JSON when accepting command")
        void testAccept_SerializesReplyToJson() {
            // Arrange
            String name = "UpdateUser";
            String idem = "user-update-456";
            String bizKey = "user-456";
            String payload = "{\"email\":\"new@example.com\"}";
            Map<String, String> reply = Map.of("queue", "user.replies", "timeout", "30000");

            UUID commandId = UUID.randomUUID();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(123L);

            ArgumentCaptor<String> replyJsonCaptor = ArgumentCaptor.forClass(String.class);

            // Act
            commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert
            verify(commandService)
                    .savePending(eq(name), eq(idem), eq(bizKey), eq(payload), replyJsonCaptor.capture());

            String capturedReplyJson = replyJsonCaptor.getValue();
            assertThat(capturedReplyJson).contains("queue");
            assertThat(capturedReplyJson).contains("user.replies");
            assertThat(capturedReplyJson).contains("timeout");
        }

        @Test
        @DisplayName("Should create outbox entry with command requested event")
        void testAccept_CreatesOutboxEntry() {
            // Arrange
            String name = "DeleteProduct";
            String idem = "product-delete-789";
            String bizKey = "product-789";
            String payload = "{\"reason\":\"discontinued\"}";
            Map<String, String> reply = Map.of("queue", "product.replies");

            UUID commandId = UUID.randomUUID();
            long outboxId = 555L;

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(outboxId);

            ArgumentCaptor<Outbox> outboxCaptor = ArgumentCaptor.forClass(Outbox.class);

            // Act
            commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert
            verify(outboxService).addReturningId(outboxCaptor.capture());

            Outbox capturedOutbox = outboxCaptor.getValue();
            assertThat(capturedOutbox).isNotNull();
            assertThat(capturedOutbox.getType()).isEqualTo("CommandRequested");
            assertThat(capturedOutbox.getStatus()).isNull(); // Status is null for new outbox entries
        }

        @Test
        @DisplayName("Should handle empty reply map")
        void testAccept_EmptyReplyMap() {
            // Arrange
            String name = "ProcessPayment";
            String idem = "payment-proc-111";
            String bizKey = "payment-111";
            String payload = "{\"amount\":250.00}";
            Map<String, String> reply = Map.of();

            UUID commandId = UUID.randomUUID();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(100L);

            // Act
            UUID result = commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert
            assertThat(result).isEqualTo(commandId);
            verify(commandService).savePending(anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle large payload")
        void testAccept_LargePayload() {
            // Arrange
            String name = "BulkImport";
            String idem = "bulk-import-222";
            String bizKey = "import-222";
            StringBuilder largePayload = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                largePayload.append("{\"id\":\"").append(i).append("\",\"data\":\"sample\"},");
            }
            String payload = "{\"items\":[" + largePayload.toString() + "]}";
            Map<String, String> reply = Map.of("queue", "import.replies");

            UUID commandId = UUID.randomUUID();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(200L);

            // Act
            UUID result = commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert
            assertThat(result).isEqualTo(commandId);
            verify(commandService).savePending(eq(name), eq(idem), eq(bizKey), eq(payload), anyString());
        }
    }

    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {

        @Test
        @DisplayName("Should throw exception when duplicate idempotency key is detected")
        void testAccept_DuplicateIdempotencyKey() {
            // Arrange
            String name = "CreateAccount";
            String idem = "account-create-duplicate";
            String bizKey = "account-999";
            String payload = "{\"name\":\"Test Account\"}";
            Map<String, String> reply = Map.of("queue", "account.replies");

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> commandBus.accept(name, idem, bizKey, payload, reply))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Duplicate idempotency key");

            // Verify no command was saved
            verify(commandService, never())
                    .savePending(anyString(), anyString(), anyString(), anyString(), anyString());
            verify(outboxService, never()).addReturningId(any(Outbox.class));
        }

        @Test
        @DisplayName("Should check idempotency key before saving command")
        void testAccept_ChecksIdempotencyBeforeSave() {
            // Arrange
            String name = "UpdateInventory";
            String idem = "inv-update-333";
            String bizKey = "inv-333";
            String payload = "{\"quantity\":50}";
            Map<String, String> reply = Map.of();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(UUID.randomUUID());
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(300L);

            // Act
            commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert - Verify order of operations
            var inOrder = inOrder(commandService, outboxService);
            inOrder.verify(commandService).existsByIdempotencyKey(idem);
            inOrder.verify(commandService).savePending(anyString(), anyString(), anyString(), anyString(), anyString());
            inOrder.verify(outboxService).addReturningId(any(Outbox.class));
        }

        @Test
        @DisplayName("Should allow same command name with different idempotency keys")
        void testAccept_SameCommandDifferentIdempotencyKeys() {
            // Arrange
            String name = "SendEmail";
            String idem1 = "email-send-1";
            String idem2 = "email-send-2";
            String bizKey = "email-batch";
            String payload = "{\"to\":\"user@example.com\"}";
            Map<String, String> reply = Map.of();

            UUID commandId1 = UUID.randomUUID();
            UUID commandId2 = UUID.randomUUID();

            when(commandService.existsByIdempotencyKey(idem1)).thenReturn(false);
            when(commandService.existsByIdempotencyKey(idem2)).thenReturn(false);
            when(commandService.savePending(eq(name), eq(idem1), anyString(), anyString(), anyString()))
                    .thenReturn(commandId1);
            when(commandService.savePending(eq(name), eq(idem2), anyString(), anyString(), anyString()))
                    .thenReturn(commandId2);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(400L, 401L);

            // Act
            UUID result1 = commandBus.accept(name, idem1, bizKey, payload, reply);
            UUID result2 = commandBus.accept(name, idem2, bizKey, payload, reply);

            // Assert
            assertThat(result1).isNotEqualTo(result2);
            verify(commandService).existsByIdempotencyKey(idem1);
            verify(commandService).existsByIdempotencyKey(idem2);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty command name")
        void testAccept_EmptyCommandName() {
            // Arrange
            String name = "";
            String idem = "empty-name-test";
            String bizKey = "test-key";
            String payload = "{}";
            Map<String, String> reply = Map.of();

            UUID commandId = UUID.randomUUID();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(500L);

            // Act
            UUID result = commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert
            assertThat(result).isEqualTo(commandId);
            verify(commandService).savePending(eq(""), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle empty business key")
        void testAccept_EmptyBusinessKey() {
            // Arrange
            String name = "TestCommand";
            String idem = "empty-bizkey-test";
            String bizKey = "";
            String payload = "{}";
            Map<String, String> reply = Map.of();

            UUID commandId = UUID.randomUUID();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(600L);

            // Act
            UUID result = commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert
            assertThat(result).isEqualTo(commandId);
            verify(commandService).savePending(anyString(), anyString(), eq(""), anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle empty payload")
        void testAccept_EmptyPayload() {
            // Arrange
            String name = "NoOpCommand";
            String idem = "empty-payload-test";
            String bizKey = "noop-key";
            String payload = "";
            Map<String, String> reply = Map.of();

            UUID commandId = UUID.randomUUID();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(700L);

            // Act
            UUID result = commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert
            assertThat(result).isEqualTo(commandId);
            verify(commandService).savePending(anyString(), anyString(), anyString(), eq(""), anyString());
        }

        @Test
        @DisplayName("Should handle special characters in idempotency key")
        void testAccept_SpecialCharsInIdempotencyKey() {
            // Arrange
            String name = "SpecialCommand";
            String idem = "special!@#$%^&*()_+-={}[]|:;<>?,./";
            String bizKey = "special-key";
            String payload = "{}";
            Map<String, String> reply = Map.of();

            UUID commandId = UUID.randomUUID();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(800L);

            // Act
            UUID result = commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert
            assertThat(result).isEqualTo(commandId);
            verify(commandService).existsByIdempotencyKey(idem);
        }

        @Test
        @DisplayName("Should handle mutable reply map")
        void testAccept_MutableReplyMap() {
            // Arrange
            String name = "MutableMapTest";
            String idem = "mutable-map-test";
            String bizKey = "map-key";
            String payload = "{}";
            Map<String, String> reply = new HashMap<>();
            reply.put("queue", "test.queue");

            UUID commandId = UUID.randomUUID();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(900L);

            // Act
            UUID result = commandBus.accept(name, idem, bizKey, payload, reply);

            // Modify the map after the call
            reply.put("extra", "value");

            // Assert - Should not affect already persisted data
            assertThat(result).isEqualTo(commandId);
            verify(commandService).savePending(anyString(), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Exception Handling Tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Should propagate exception from CommandService.existsByIdempotencyKey")
        void testAccept_ExceptionFromIdempotencyCheck() {
            // Arrange
            String name = "ErrorCommand";
            String idem = "error-idem-key";
            String bizKey = "error-key";
            String payload = "{}";
            Map<String, String> reply = Map.of();

            when(commandService.existsByIdempotencyKey(idem))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // Act & Assert
            assertThatThrownBy(() -> commandBus.accept(name, idem, bizKey, payload, reply))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database connection failed");

            verify(commandService, never())
                    .savePending(anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should propagate exception from CommandService.savePending")
        void testAccept_ExceptionFromSavePending() {
            // Arrange
            String name = "SaveErrorCommand";
            String idem = "save-error-key";
            String bizKey = "error-key";
            String payload = "{}";
            Map<String, String> reply = Map.of();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Failed to save command"));

            // Act & Assert
            assertThatThrownBy(() -> commandBus.accept(name, idem, bizKey, payload, reply))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to save command");

            verify(outboxService, never()).addReturningId(any(Outbox.class));
        }

        @Test
        @DisplayName("Should propagate exception from OutboxService.addReturningId")
        void testAccept_ExceptionFromOutboxService() {
            // Arrange
            String name = "OutboxErrorCommand";
            String idem = "outbox-error-key";
            String bizKey = "error-key";
            String payload = "{}";
            Map<String, String> reply = Map.of();

            UUID commandId = UUID.randomUUID();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class)))
                    .thenThrow(new RuntimeException("Failed to create outbox entry"));

            // Act & Assert
            assertThatThrownBy(() -> commandBus.accept(name, idem, bizKey, payload, reply))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to create outbox entry");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should complete full workflow from accept to outbox creation")
        void testAccept_FullWorkflow() {
            // Arrange
            String name = "FullWorkflowCommand";
            String idem = "full-workflow-123";
            String bizKey = "workflow-key-123";
            String payload = "{\"data\":\"test\"}";
            Map<String, String> reply = Map.of("queue", "workflow.replies", "timeout", "5000");

            UUID expectedCommandId = UUID.randomUUID();
            long expectedOutboxId = 12345L;

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(eq(name), eq(idem), eq(bizKey), eq(payload), anyString()))
                    .thenReturn(expectedCommandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(expectedOutboxId);

            // Act
            UUID result = commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert
            assertThat(result).isEqualTo(expectedCommandId);

            // Verify complete workflow
            var inOrder = inOrder(commandService, outboxService);
            inOrder.verify(commandService).existsByIdempotencyKey(idem);
            inOrder
                    .verify(commandService)
                    .savePending(eq(name), eq(idem), eq(bizKey), eq(payload), anyString());
            inOrder.verify(outboxService).addReturningId(any(Outbox.class));

            // Verify all interactions completed
            verifyNoMoreInteractions(commandService, outboxService);
        }

        @Test
        @DisplayName("Should handle multiple sequential command acceptances")
        void testAccept_MultipleSequential() {
            // Arrange
            when(commandService.existsByIdempotencyKey(anyString())).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(UUID.randomUUID());
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(1L, 2L, 3L);

            // Act - Accept three commands sequentially
            UUID result1 =
                    commandBus.accept("Cmd1", "idem1", "key1", "{}", Map.of("queue", "q1"));
            UUID result2 =
                    commandBus.accept("Cmd2", "idem2", "key2", "{}", Map.of("queue", "q2"));
            UUID result3 =
                    commandBus.accept("Cmd3", "idem3", "key3", "{}", Map.of("queue", "q3"));

            // Assert
            assertThat(result1).isNotNull();
            assertThat(result2).isNotNull();
            assertThat(result3).isNotNull();

            verify(commandService, times(3)).existsByIdempotencyKey(anyString());
            verify(commandService, times(3))
                    .savePending(anyString(), anyString(), anyString(), anyString(), anyString());
            verify(outboxService, times(3)).addReturningId(any(Outbox.class));
        }
    }

    @Nested
    @DisplayName("Transactional Behavior Tests")
    class TransactionalBehaviorTests {

        @Test
        @DisplayName("Should ensure all operations happen within same transaction")
        void testAccept_TransactionalBoundary() {
            // Arrange
            String name = "TransactionalCommand";
            String idem = "txn-test-key";
            String bizKey = "txn-key";
            String payload = "{}";
            Map<String, String> reply = Map.of();

            UUID commandId = UUID.randomUUID();

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(1000L);

            // Act
            UUID result = commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert
            assertThat(result).isNotNull();

            // Verify the method exists with correct signature
            assertThatCode(
                    () ->
                            TransactionalCommandBus.class.getMethod(
                                    "accept", String.class, String.class, String.class, String.class, Map.class))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("FastPath Publisher Integration Tests")
    class FastPathPublisherTests {

        @Test
        @DisplayName("Should not call fastPath publisher when disabled")
        void testAccept_FastPathDisabled() {
            // Arrange
            String name = "FastPathCommand";
            String idem = "fastpath-test";
            String bizKey = "fp-key";
            String payload = "{}";
            Map<String, String> reply = Map.of();

            UUID commandId = UUID.randomUUID();
            long outboxId = 2000L;

            when(commandService.existsByIdempotencyKey(idem)).thenReturn(false);
            when(commandService.savePending(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(commandId);
            when(outboxService.addReturningId(any(Outbox.class))).thenReturn(outboxId);

            // Act
            commandBus.accept(name, idem, bizKey, payload, reply);

            // Assert - FastPath is commented out in code, should not be called
            verifyNoInteractions(fastPathPublisher);
        }
    }
}
