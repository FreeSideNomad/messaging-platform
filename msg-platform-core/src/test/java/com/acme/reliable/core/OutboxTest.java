package com.acme.reliable.core;

import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.spi.OutboxRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Outbox
 */
class OutboxTest {

    private Outbox outbox;
    private MessagingConfig config;

    @BeforeEach
    void setUp() {
        config = new MessagingConfig();
        outbox = new Outbox(config);
    }

    @Nested
    @DisplayName("Command Outbox Tests")
    class CommandOutboxTests {

        @Test
        @DisplayName("rowCommandRequested - should create command outbox row")
        void testRowCommandRequested() {
            UUID commandId = UUID.randomUUID();
            Map<String, String> replyHeaders = Map.of("replyTo", "TEST.REPLY.Q");

            OutboxRow row = outbox.rowCommandRequested(
                "CreateUser",
                commandId,
                "user-123",
                "{\"name\":\"John\"}",
                replyHeaders
            );

            assertThat(row).isNotNull();
            assertThat(row.id()).isNotNull();
            assertThat(row.category()).isEqualTo("command");
            assertThat(row.topic()).isEqualTo("APP.CMD.CREATEUSER.Q");
            assertThat(row.key()).isEqualTo("user-123");
            assertThat(row.type()).isEqualTo("CommandRequested");
            assertThat(row.payload()).isEqualTo("{\"name\":\"John\"}");
            assertThat(row.attempts()).isEqualTo(0);

            // Verify merged headers
            assertThat(row.headers()).containsEntry("commandId", commandId.toString());
            assertThat(row.headers()).containsEntry("commandName", "CreateUser");
            assertThat(row.headers()).containsEntry("businessKey", "user-123");
            assertThat(row.headers()).containsEntry("replyTo", "TEST.REPLY.Q");
        }

        @Test
        @DisplayName("rowCommandRequested - should handle empty reply headers")
        void testRowCommandRequestedEmptyHeaders() {
            UUID commandId = UUID.randomUUID();

            OutboxRow row = outbox.rowCommandRequested(
                "DeleteUser",
                commandId,
                "user-456",
                "{}",
                Map.of()
            );

            assertThat(row.headers()).containsEntry("commandId", commandId.toString());
            assertThat(row.headers()).containsEntry("commandName", "DeleteUser");
            assertThat(row.headers()).containsEntry("businessKey", "user-456");
            assertThat(row.headers()).hasSize(3);
        }

        @Test
        @DisplayName("rowCommandRequested - should use configured queue naming")
        void testRowCommandRequestedQueueNaming() {
            MessagingConfig customConfig = new MessagingConfig();
            customConfig.getQueueNaming().setCommandPrefix("CUSTOM.CMD.");
            customConfig.getQueueNaming().setQueueSuffix(".QUEUE");
            Outbox customOutbox = new Outbox(customConfig);

            OutboxRow row = customOutbox.rowCommandRequested(
                "TestCommand",
                UUID.randomUUID(),
                "key",
                "{}",
                Map.of()
            );

            assertThat(row.topic()).isEqualTo("CUSTOM.CMD.TESTCOMMAND.QUEUE");
        }
    }

    @Nested
    @DisplayName("Event Outbox Tests")
    class EventOutboxTests {

        @Test
        @DisplayName("rowKafkaEvent - should create event outbox row")
        void testRowKafkaEvent() {
            OutboxRow row = outbox.rowKafkaEvent(
                "events.UserCreated",
                "user-789",
                "UserCreated",
                "{\"userId\":\"789\"}"
            );

            assertThat(row).isNotNull();
            assertThat(row.id()).isNotNull();
            assertThat(row.category()).isEqualTo("event");
            assertThat(row.topic()).isEqualTo("events.UserCreated");
            assertThat(row.key()).isEqualTo("user-789");
            assertThat(row.type()).isEqualTo("UserCreated");
            assertThat(row.payload()).isEqualTo("{\"userId\":\"789\"}");
            assertThat(row.headers()).isEmpty();
            assertThat(row.attempts()).isEqualTo(0);
        }

        @Test
        @DisplayName("rowKafkaEvent - should handle empty payload")
        void testRowKafkaEventEmptyPayload() {
            OutboxRow row = outbox.rowKafkaEvent(
                "events.Test",
                "key",
                "TestEvent",
                ""
            );

            assertThat(row.payload()).isEmpty();
            assertThat(row.headers()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Reply Outbox Tests")
    class ReplyOutboxTests {

        @Test
        @DisplayName("rowMqReply - should create reply outbox row with replyTo from envelope")
        void testRowMqReplyWithReplyTo() {
            UUID correlationId = UUID.randomUUID();
            Map<String, String> headers = Map.of(
                "replyTo", "CUSTOM.REPLY.Q",
                "customHeader", "value"
            );
            Envelope env = new Envelope(
                UUID.randomUUID(),
                "reply",
                "CommandCompleted",
                UUID.randomUUID(),
                correlationId,
                UUID.randomUUID(),
                Instant.now(),
                "key-123",
                headers,
                "{}"
            );

            OutboxRow row = outbox.rowMqReply(
                env,
                "CommandCompleted",
                "{\"result\":\"success\"}"
            );

            assertThat(row).isNotNull();
            assertThat(row.id()).isNotNull();
            assertThat(row.category()).isEqualTo("reply");
            assertThat(row.topic()).isEqualTo("CUSTOM.REPLY.Q");
            assertThat(row.key()).isEqualTo("key-123");
            assertThat(row.type()).isEqualTo("CommandCompleted");
            assertThat(row.payload()).isEqualTo("{\"result\":\"success\"}");
            assertThat(row.attempts()).isEqualTo(0);

            // Verify merged headers
            assertThat(row.headers()).containsEntry("correlationId", correlationId.toString());
            assertThat(row.headers()).containsEntry("replyTo", "CUSTOM.REPLY.Q");
            assertThat(row.headers()).containsEntry("customHeader", "value");
        }

        @Test
        @DisplayName("rowMqReply - should use default reply queue when replyTo not in envelope")
        void testRowMqReplyDefaultQueue() {
            UUID correlationId = UUID.randomUUID();
            Envelope env = new Envelope(
                UUID.randomUUID(),
                "reply",
                "CommandFailed",
                UUID.randomUUID(),
                correlationId,
                UUID.randomUUID(),
                Instant.now(),
                "key-456",
                Map.of(),
                "{}"
            );

            OutboxRow row = outbox.rowMqReply(
                env,
                "CommandFailed",
                "{\"error\":\"test\"}"
            );

            assertThat(row.topic()).isEqualTo("APP.CMD.REPLY.Q");
            assertThat(row.headers()).containsEntry("correlationId", correlationId.toString());
        }

        @Test
        @DisplayName("rowMqReply - should use custom default reply queue")
        void testRowMqReplyCustomDefaultQueue() {
            MessagingConfig customConfig = new MessagingConfig();
            customConfig.getQueueNaming().setReplyQueue("MY.REPLY.QUEUE");
            Outbox customOutbox = new Outbox(customConfig);

            UUID correlationId = UUID.randomUUID();
            Envelope env = new Envelope(
                UUID.randomUUID(),
                "reply",
                "Reply",
                UUID.randomUUID(),
                correlationId,
                UUID.randomUUID(),
                Instant.now(),
                "key",
                Map.of(),
                "{}"
            );

            OutboxRow row = customOutbox.rowMqReply(
                env,
                "Reply",
                "{}"
            );

            assertThat(row.topic()).isEqualTo("MY.REPLY.QUEUE");
        }

        @Test
        @DisplayName("rowMqReply - should handle empty envelope headers")
        void testRowMqReplyEmptyHeaders() {
            UUID correlationId = UUID.randomUUID();
            Envelope env = new Envelope(
                UUID.randomUUID(),
                "reply",
                "TestReply",
                UUID.randomUUID(),
                correlationId,
                UUID.randomUUID(),
                Instant.now(),
                "key",
                Map.of(),
                "{}"
            );

            OutboxRow row = outbox.rowMqReply(
                env,
                "TestReply",
                "{}"
            );

            assertThat(row.headers()).containsEntry("correlationId", correlationId.toString());
            assertThat(row.headers()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("should generate unique IDs for each row")
        void testUniqueIds() {
            OutboxRow row1 = outbox.rowKafkaEvent("topic", "key", "type", "{}");
            OutboxRow row2 = outbox.rowKafkaEvent("topic", "key", "type", "{}");

            assertThat(row1.id()).isNotEqualTo(row2.id());
        }

        @Test
        @DisplayName("should work with custom config")
        void testCustomConfig() {
            MessagingConfig customConfig = new MessagingConfig();
            customConfig.getQueueNaming().setCommandPrefix("PROD.CMD.");
            customConfig.getQueueNaming().setQueueSuffix(".QUEUE");
            customConfig.getQueueNaming().setReplyQueue("PROD.REPLY.QUEUE");

            Outbox customOutbox = new Outbox(customConfig);

            OutboxRow cmdRow = customOutbox.rowCommandRequested(
                "Test",
                UUID.randomUUID(),
                "key",
                "{}",
                Map.of()
            );
            assertThat(cmdRow.topic()).isEqualTo("PROD.CMD.TEST.QUEUE");

            Envelope env = new Envelope(
                UUID.randomUUID(),
                "reply",
                "Reply",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                "key",
                Map.of(),
                "{}"
            );
            OutboxRow replyRow = customOutbox.rowMqReply(env, "Reply", "{}");
            assertThat(replyRow.topic()).isEqualTo("PROD.REPLY.QUEUE");
        }
    }
}
