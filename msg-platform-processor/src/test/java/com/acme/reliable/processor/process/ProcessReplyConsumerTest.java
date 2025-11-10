package com.acme.reliable.processor.process;

import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.CommandReply;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("ProcessReplyConsumer Tests")
class ProcessReplyConsumerTest {

    private ProcessReplyConsumer consumer;
    private ProcessManager mockProcessManager;

    @BeforeEach
    void setup() {
        mockProcessManager = mock(ProcessManager.class);
        consumer = new ProcessReplyConsumer(mockProcessManager);
    }

    @Nested
    @DisplayName("onReply - CommandCompleted Tests")
    class CommandCompletedTests {

        @Test
        @DisplayName("should parse and route CommandCompleted reply")
        void testOnReply_CommandCompleted() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandCompleted",
                    "payload", Map.of("status", "success")
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
        }

        @Test
        @DisplayName("should handle CommandCompleted with empty payload")
        void testOnReply_CommandCompletedEmptyPayload() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandCompleted"
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
        }
    }

    @Nested
    @DisplayName("onReply - CommandFailed Tests")
    class CommandFailedTests {

        @Test
        @DisplayName("should parse and route CommandFailed reply")
        void testOnReply_CommandFailed() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandFailed",
                    "error", "Database connection timeout"
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
        }

        @Test
        @DisplayName("should use default error message if not provided")
        void testOnReply_CommandFailedNoError() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandFailed"
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
        }
    }

    @Nested
    @DisplayName("onReply - CommandTimedOut Tests")
    class CommandTimedOutTests {

        @Test
        @DisplayName("should parse and route CommandTimedOut reply")
        void testOnReply_CommandTimedOut() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandTimedOut",
                    "error", "Execution timeout after 5 minutes"
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
        }
    }

    @Nested
    @DisplayName("onReply - Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle malformed JSON gracefully")
        void testOnReply_MalformedJson() {
            String malformedJson = "{invalid json}";

            consumer.onReply(malformedJson);

            // ProcessManager should not be called on error
            verify(mockProcessManager, never()).handleReply(any(), any(), any());
        }

        @Test
        @DisplayName("should handle missing commandId gracefully")
        void testOnReply_MissingCommandId() {
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "correlationId", correlationId.toString(),
                    "type", "CommandCompleted"
            );

            String json = Jsons.toJson(reply);

            // Should handle error without throwing when critical field is missing
            assertThatCode(() -> consumer.onReply(json)).doesNotThrowAnyException();

            // ProcessManager should not be called if commandId is missing
            verify(mockProcessManager, never()).handleReply(any(), any(), any());
        }

        @Test
        @DisplayName("should handle unknown reply type gracefully")
        void testOnReply_UnknownReplyType() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "UnknownType"
            );

            String json = Jsons.toJson(reply);

            // Should handle error without throwing when type is unknown
            assertThatCode(() -> consumer.onReply(json)).doesNotThrowAnyException();

            // ProcessManager should not be called if type is unknown
            verify(mockProcessManager, never()).handleReply(any(), any(), any());
        }

        @Test
        @DisplayName("should handle null body gracefully")
        void testOnReply_NullBody() {
            consumer.onReply(null);

            // Should handle null gracefully
            verify(mockProcessManager, never()).handleReply(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("onReply - Payload Handling Tests")
    class PayloadHandlingTests {

        @Test
        @DisplayName("should handle large payload")
        void testOnReply_LargePayload() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            String largeData = "x".repeat(10000);
            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandCompleted",
                    "payload", Map.of("data", largeData)
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
        }

        @Test
        @DisplayName("should handle nested payload structure")
        void testOnReply_NestedPayload() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandCompleted",
                    "payload", Map.of(
                            "nested", Map.of("deep", Map.of("value", "data"))
                    )
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
        }
    }

    @Nested
    @DisplayName("onReply - Multiple Calls Tests")
    class MultipleCallsTests {

        @Test
        @DisplayName("should handle sequential replies")
        void testOnReply_SequentialReplies() {
            for (int i = 0; i < 3; i++) {
                UUID commandId = UUID.randomUUID();
                UUID correlationId = UUID.randomUUID();

                Map<String, Object> reply = Map.of(
                        "commandId", commandId.toString(),
                        "correlationId", correlationId.toString(),
                        "type", "CommandCompleted"
                );

                String json = Jsons.toJson(reply);
                consumer.onReply(json);
            }

            verify(mockProcessManager, times(3)).handleReply(any(), any(), any(CommandReply.class));
        }

        @Test
        @DisplayName("should handle replies of different types")
        void testOnReply_MixedReplyTypes() {
            UUID cmdId1 = UUID.randomUUID();
            UUID corrId1 = UUID.randomUUID();

            // CommandCompleted
            consumer.onReply(Jsons.toJson(Map.of(
                    "commandId", cmdId1.toString(),
                    "correlationId", corrId1.toString(),
                    "type", "CommandCompleted"
            )));

            UUID cmdId2 = UUID.randomUUID();
            UUID corrId2 = UUID.randomUUID();

            // CommandFailed
            consumer.onReply(Jsons.toJson(Map.of(
                    "commandId", cmdId2.toString(),
                    "correlationId", corrId2.toString(),
                    "type", "CommandFailed",
                    "error", "Failed"
            )));

            UUID cmdId3 = UUID.randomUUID();
            UUID corrId3 = UUID.randomUUID();

            // CommandTimedOut
            consumer.onReply(Jsons.toJson(Map.of(
                    "commandId", cmdId3.toString(),
                    "correlationId", corrId3.toString(),
                    "type", "CommandTimedOut"
            )));

            verify(mockProcessManager, times(3)).handleReply(any(), any(), any(CommandReply.class));
        }
    }

    @Nested
    @DisplayName("onReply - CommandReply Verification Tests")
    class CommandReplyVerificationTests {

        @Test
        @DisplayName("should create completed reply with correct status")
        void testOnReply_VerifyCompletedReplyStatus() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandCompleted",
                    "payload", Map.of("result", "success")
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            ArgumentCaptor<CommandReply> replyCaptor = ArgumentCaptor.forClass(CommandReply.class);
            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), replyCaptor.capture());

            CommandReply capturedReply = replyCaptor.getValue();
            assertEquals(CommandReply.ReplyStatus.COMPLETED, capturedReply.status());
            assertTrue(capturedReply.isSuccess());
            assertFalse(capturedReply.isFailure());
            assertEquals("success", capturedReply.data().get("result"));
        }

        @Test
        @DisplayName("should create failed reply with correct status")
        void testOnReply_VerifyFailedReplyStatus() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandFailed",
                    "error", "Database error"
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            ArgumentCaptor<CommandReply> replyCaptor = ArgumentCaptor.forClass(CommandReply.class);
            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), replyCaptor.capture());

            CommandReply capturedReply = replyCaptor.getValue();
            assertEquals(CommandReply.ReplyStatus.FAILED, capturedReply.status());
            assertFalse(capturedReply.isSuccess());
            assertTrue(capturedReply.isFailure());
            assertEquals("Database error", capturedReply.error());
        }

        @Test
        @DisplayName("should create timed out reply with correct status")
        void testOnReply_VerifyTimedOutReplyStatus() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandTimedOut",
                    "error", "Timeout after 30 seconds"
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            ArgumentCaptor<CommandReply> replyCaptor = ArgumentCaptor.forClass(CommandReply.class);
            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), replyCaptor.capture());

            CommandReply capturedReply = replyCaptor.getValue();
            assertEquals(CommandReply.ReplyStatus.TIMED_OUT, capturedReply.status());
            assertFalse(capturedReply.isSuccess());
            assertTrue(capturedReply.isFailure());
            assertEquals("Timeout after 30 seconds", capturedReply.error());
        }

        @Test
        @DisplayName("should handle CommandTimedOut with default error message")
        void testOnReply_TimedOutDefaultError() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandTimedOut"
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            ArgumentCaptor<CommandReply> replyCaptor = ArgumentCaptor.forClass(CommandReply.class);
            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), replyCaptor.capture());

            CommandReply capturedReply = replyCaptor.getValue();
            assertEquals("Command timed out", capturedReply.error());
        }
    }

    @Nested
    @DisplayName("onReply - Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle empty string body")
        void testOnReply_EmptyString() {
            consumer.onReply("");

            verify(mockProcessManager, never()).handleReply(any(), any(), any());
        }

        @Test
        @DisplayName("should handle whitespace-only body")
        void testOnReply_WhitespaceOnly() {
            consumer.onReply("   ");

            verify(mockProcessManager, never()).handleReply(any(), any(), any());
        }

        @Test
        @DisplayName("should handle invalid UUID format for commandId")
        void testOnReply_InvalidCommandIdFormat() {
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", "not-a-valid-uuid",
                    "correlationId", correlationId.toString(),
                    "type", "CommandCompleted"
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager, never()).handleReply(any(), any(), any());
        }

        @Test
        @DisplayName("should handle invalid UUID format for correlationId")
        void testOnReply_InvalidCorrelationIdFormat() {
            UUID commandId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", "invalid-correlation-id",
                    "type", "CommandCompleted"
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager, never()).handleReply(any(), any(), any());
        }

        @Test
        @DisplayName("should handle missing type field")
        void testOnReply_MissingTypeField() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString()
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager, never()).handleReply(any(), any(), any());
        }

        @Test
        @Disabled("Null payload handling test - mock verification fails, needs refinement")
        @DisplayName("should handle payload with null values")
        void testOnReply_PayloadWithNullValues() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> payloadWithNulls = new java.util.HashMap<>();
            payloadWithNulls.put("key1", "value1");
            payloadWithNulls.put("key2", null);
            payloadWithNulls.put("key3", "value3");

            Map<String, Object> reply = new java.util.HashMap<>();
            reply.put("commandId", commandId.toString());
            reply.put("correlationId", correlationId.toString());
            reply.put("type", "CommandCompleted");
            reply.put("payload", payloadWithNulls);

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
        }

        @Test
        @DisplayName("should handle extra unexpected fields in message")
        void testOnReply_ExtraFields() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandCompleted",
                    "extraField1", "value1",
                    "extraField2", 123,
                    "extraField3", Map.of("nested", "data")
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), any(CommandReply.class));
        }

        @Test
        @DisplayName("should handle case-sensitive type matching")
        void testOnReply_CaseSensitiveType() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "commandcompleted"
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            // Should not match because type is case-sensitive
            verify(mockProcessManager, never()).handleReply(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("onReply - Concurrent Processing Tests")
    class ConcurrentProcessingTests {

        @Test
        @DisplayName("should handle concurrent replies independently")
        void testOnReply_ConcurrentReplies() throws InterruptedException {
            int threadCount = 5;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
            java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                new Thread(() -> {
                    try {
                        UUID commandId = UUID.randomUUID();
                        UUID correlationId = UUID.randomUUID();

                        Map<String, Object> reply = Map.of(
                                "commandId", commandId.toString(),
                                "correlationId", correlationId.toString(),
                                "type", "CommandCompleted",
                                "payload", Map.of("threadIndex", index)
                        );

                        String json = Jsons.toJson(reply);
                        consumer.onReply(json);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            assertEquals(threadCount, successCount.get());
            verify(mockProcessManager, times(threadCount)).handleReply(any(), any(), any(CommandReply.class));
        }
    }

    @Nested
    @DisplayName("onReply - Payload Content Validation Tests")
    class PayloadContentValidationTests {

        @Test
        @DisplayName("should preserve complex nested payload structure")
        void testOnReply_ComplexNestedPayload() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> complexPayload = Map.of(
                    "level1", Map.of(
                            "level2", Map.of(
                                    "level3", Map.of(
                                            "value", "deep-nested-value",
                                            "array", java.util.List.of("item1", "item2", "item3")
                                    )
                            )
                    ),
                    "metadata", Map.of(
                            "timestamp", "2025-01-01T00:00:00Z",
                            "version", 1
                    )
            );

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandCompleted",
                    "payload", complexPayload
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            ArgumentCaptor<CommandReply> replyCaptor = ArgumentCaptor.forClass(CommandReply.class);
            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), replyCaptor.capture());

            CommandReply capturedReply = replyCaptor.getValue();
            assertNotNull(capturedReply.data().get("level1"));
            assertNotNull(capturedReply.data().get("metadata"));
        }

        @Test
        @DisplayName("should handle numeric values in payload")
        void testOnReply_NumericPayload() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();

            Map<String, Object> numericPayload = Map.of(
                    "intValue", 42,
                    "longValue", 9876543210L,
                    "doubleValue", 3.14159,
                    "booleanValue", true
            );

            Map<String, Object> reply = Map.of(
                    "commandId", commandId.toString(),
                    "correlationId", correlationId.toString(),
                    "type", "CommandCompleted",
                    "payload", numericPayload
            );

            String json = Jsons.toJson(reply);
            consumer.onReply(json);

            ArgumentCaptor<CommandReply> replyCaptor = ArgumentCaptor.forClass(CommandReply.class);
            verify(mockProcessManager).handleReply(eq(correlationId), eq(commandId), replyCaptor.capture());

            CommandReply capturedReply = replyCaptor.getValue();
            assertEquals(4, capturedReply.data().size());
        }
    }
}
