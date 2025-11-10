package com.acme.reliable.process;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CommandReply record
 */
class CommandReplyTest {

    @Nested
    @DisplayName("Factory Methods Tests")
    class FactoryMethodsTests {

        @Test
        @DisplayName("completed - should create completed reply")
        void testCompleted() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();
            Map<String, Object> data = Map.of("result", "success", "value", 42);

            CommandReply reply = CommandReply.completed(commandId, correlationId, data);

            assertThat(reply.commandId()).isEqualTo(commandId);
            assertThat(reply.correlationId()).isEqualTo(correlationId);
            assertThat(reply.status()).isEqualTo(CommandReply.ReplyStatus.COMPLETED);
            assertThat(reply.data()).containsEntry("result", "success");
            assertThat(reply.data()).containsEntry("value", 42);
            assertThat(reply.error()).isNull();
        }

        @Test
        @DisplayName("failed - should create failed reply")
        void testFailed() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();
            String error = "Operation failed";

            CommandReply reply = CommandReply.failed(commandId, correlationId, error);

            assertThat(reply.commandId()).isEqualTo(commandId);
            assertThat(reply.correlationId()).isEqualTo(correlationId);
            assertThat(reply.status()).isEqualTo(CommandReply.ReplyStatus.FAILED);
            assertThat(reply.data()).isEmpty();
            assertThat(reply.error()).isEqualTo("Operation failed");
        }

        @Test
        @DisplayName("timedOut - should create timed out reply")
        void testTimedOut() {
            UUID commandId = UUID.randomUUID();
            UUID correlationId = UUID.randomUUID();
            String error = "Command timed out";

            CommandReply reply = CommandReply.timedOut(commandId, correlationId, error);

            assertThat(reply.commandId()).isEqualTo(commandId);
            assertThat(reply.correlationId()).isEqualTo(correlationId);
            assertThat(reply.status()).isEqualTo(CommandReply.ReplyStatus.TIMED_OUT);
            assertThat(reply.data()).isEmpty();
            assertThat(reply.error()).isEqualTo("Command timed out");
        }
    }

    @Nested
    @DisplayName("Status Check Tests")
    class StatusCheckTests {

        @Test
        @DisplayName("isSuccess - should return true for completed")
        void testIsSuccessCompleted() {
            CommandReply reply = CommandReply.completed(UUID.randomUUID(), UUID.randomUUID(), Map.of());

            assertThat(reply.isSuccess()).isTrue();
            assertThat(reply.isFailure()).isFalse();
        }

        @Test
        @DisplayName("isFailure - should return true for failed")
        void testIsFailureFailed() {
            CommandReply reply = CommandReply.failed(UUID.randomUUID(), UUID.randomUUID(), "Error");

            assertThat(reply.isSuccess()).isFalse();
            assertThat(reply.isFailure()).isTrue();
        }

        @Test
        @DisplayName("isFailure - should return true for timed out")
        void testIsFailureTimedOut() {
            CommandReply reply = CommandReply.timedOut(UUID.randomUUID(), UUID.randomUUID(), "Timeout");

            assertThat(reply.isSuccess()).isFalse();
            assertThat(reply.isFailure()).isTrue();
        }
    }

    @Nested
    @DisplayName("Serialization Tests")
    class SerializationTests {

        @Test
        @DisplayName("toJson - should serialize to JSON")
        void testToJson() {
            CommandReply reply =
                    CommandReply.completed(UUID.randomUUID(), UUID.randomUUID(), Map.of("key", "value"));

            String json = reply.toJson();

            assertThat(json).contains("\"status\":\"COMPLETED\"");
            assertThat(json).contains("\"key\":\"value\"");
        }

        @Test
        @DisplayName("toJson - should handle failed reply")
        void testToJsonFailed() {
            CommandReply reply = CommandReply.failed(UUID.randomUUID(), UUID.randomUUID(), "Test error");

            String json = reply.toJson();

            assertThat(json).contains("\"status\":\"FAILED\"");
            assertThat(json).contains("\"error\":\"Test error\"");
        }
    }

    @Nested
    @DisplayName("Enum Tests")
    class EnumTests {

        @Test
        @DisplayName("ReplyStatus - should have all expected values")
        void testAllValues() {
            CommandReply.ReplyStatus[] values = CommandReply.ReplyStatus.values();

            assertThat(values).hasSize(3);
            assertThat(values)
                    .contains(
                            CommandReply.ReplyStatus.COMPLETED,
                            CommandReply.ReplyStatus.FAILED,
                            CommandReply.ReplyStatus.TIMED_OUT);
        }

        @Test
        @DisplayName("ReplyStatus - should support valueOf")
        void testValueOf() {
            assertThat(CommandReply.ReplyStatus.valueOf("COMPLETED"))
                    .isEqualTo(CommandReply.ReplyStatus.COMPLETED);
            assertThat(CommandReply.ReplyStatus.valueOf("FAILED"))
                    .isEqualTo(CommandReply.ReplyStatus.FAILED);
            assertThat(CommandReply.ReplyStatus.valueOf("TIMED_OUT"))
                    .isEqualTo(CommandReply.ReplyStatus.TIMED_OUT);
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("completed - should handle empty data")
        void testCompletedEmptyData() {
            CommandReply reply = CommandReply.completed(UUID.randomUUID(), UUID.randomUUID(), Map.of());

            assertThat(reply.data()).isEmpty();
            assertThat(reply.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("completed - should create defensive copy of data")
        void testCompletedDefensiveCopy() {
            Map<String, Object> original = new java.util.HashMap<>();
            original.put("key", "value");

            CommandReply reply = CommandReply.completed(UUID.randomUUID(), UUID.randomUUID(), original);

            // Modify original
            original.put("key", "modified");

            // Reply should have original value
            assertThat(reply.data()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("failed - should handle null error")
        void testFailedNullError() {
            CommandReply reply = CommandReply.failed(UUID.randomUUID(), UUID.randomUUID(), null);

            assertThat(reply.error()).isNull();
            assertThat(reply.isFailure()).isTrue();
        }
    }
}
