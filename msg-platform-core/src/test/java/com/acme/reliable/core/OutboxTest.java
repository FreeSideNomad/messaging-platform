package com.acme.reliable.core;

import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.domain.Outbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Outbox
 */
class OutboxTest {

    private MessagingConfig config;

    @BeforeEach
    void setUp() {
        config = new MessagingConfig();
    }

    @Nested
    @DisplayName("Command Outbox Tests")
    class CommandOutboxTests {

        @Test
        @DisplayName("rowCommandRequested - should create command outbox row")
        void testRowCommandRequested() {
            UUID commandId = UUID.randomUUID();
            Map<String, String> replyHeaders = Map.of("replyTo", "TEST.REPLY.Q");

            Outbox row =
                    Outbox.newCommandRequested(
                            "CreateUser", commandId, "user-123", "{\"name\":\"John\"}", replyHeaders, config);

            assertThat(row).isNotNull();
            assertThat(row.getId()).isNotNull();
            assertThat(row.getCategory()).isEqualTo("command");
            assertThat(row.getTopic()).isEqualTo("APP.CMD.CREATEUSER.Q");
            assertThat(row.getKey()).isEqualTo("user-123");
            assertThat(row.getType()).isEqualTo("CommandRequested");
            assertThat(row.getPayload()).isEqualTo("{\"name\":\"John\"}");
            assertThat(row.getAttempts()).isEqualTo(0);

            // Verify merged headers
            assertThat(row.getHeaders()).containsEntry("commandId", commandId.toString());
            assertThat(row.getHeaders()).containsEntry("commandName", "CreateUser");
            assertThat(row.getHeaders()).containsEntry("businessKey", "user-123");
            assertThat(row.getHeaders()).containsEntry("replyTo", "TEST.REPLY.Q");
        }

        @Test
        @DisplayName("rowCommandRequested - should handle empty reply headers")
        void testRowCommandRequestedEmptyHeaders() {
            UUID commandId = UUID.randomUUID();

            Outbox row =
                    Outbox.newCommandRequested("DeleteUser", commandId, "user-456", "{}", Map.of(), config);

            assertThat(row.getHeaders()).containsEntry("commandId", commandId.toString());
            assertThat(row.getHeaders()).containsEntry("commandName", "DeleteUser");
            assertThat(row.getHeaders()).containsEntry("businessKey", "user-456");
            assertThat(row.getHeaders()).hasSize(3);
        }

        @Test
        @DisplayName("rowCommandRequested - should use configured queue naming")
        void testRowCommandRequestedQueueNaming() {
            MessagingConfig customConfig = new MessagingConfig();
            customConfig.getQueueNaming().setCommandPrefix("CUSTOM.CMD.");
            customConfig.getQueueNaming().setQueueSuffix(".QUEUE");

            Outbox row =
                    Outbox.newCommandRequested("TestCommand", UUID.randomUUID(), "key", "{}", Map.of(), customConfig);

            assertThat(row.getTopic()).isEqualTo("CUSTOM.CMD.TESTCOMMAND.QUEUE");
        }
    }

    @Nested
    @DisplayName("Event Outbox Tests")
    class EventOutboxTests {

        @Test
        @DisplayName("rowKafkaEvent - should create event outbox row")
        void testRowKafkaEvent() {
            Outbox row =
                    Outbox.newKafkaEvent(
                            "events.UserCreated", "user-789", "UserCreated", "{\"userId\":\"789\"}");

            assertThat(row).isNotNull();
            assertThat(row.getId()).isNotNull();
            assertThat(row.getCategory()).isEqualTo("event");
            assertThat(row.getTopic()).isEqualTo("events.UserCreated");
            assertThat(row.getKey()).isEqualTo("user-789");
            assertThat(row.getType()).isEqualTo("UserCreated");
            assertThat(row.getPayload()).isEqualTo("{\"userId\":\"789\"}");
            assertThat(row.getHeaders()).isEmpty();
            assertThat(row.getAttempts()).isEqualTo(0);
        }

        @Test
        @DisplayName("rowKafkaEvent - should handle empty payload")
        void testRowKafkaEventEmptyPayload() {
            Outbox row = Outbox.newKafkaEvent("events.Test", "key", "TestEvent", "");

            assertThat(row.getPayload()).isEmpty();
            assertThat(row.getHeaders()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Reply Outbox Tests")
    class ReplyOutboxTests {

        @Test
        @DisplayName("rowMqReply - should create reply outbox row with replyTo from envelope")
        void testRowMqReplyWithReplyTo() {
            UUID correlationId = UUID.randomUUID();
            Map<String, String> headers =
                    Map.of(
                            "replyTo", "CUSTOM.REPLY.Q",
                            "customHeader", "value");
            Envelope env =
                    new Envelope(
                            UUID.randomUUID(),
                            "reply",
                            "CommandCompleted",
                            UUID.randomUUID(),
                            correlationId,
                            UUID.randomUUID(),
                            Instant.now(),
                            "key-123",
                            headers,
                            "{}");

            Outbox row = Outbox.newMqReply(env, "CommandCompleted", "{\"result\":\"success\"}", config);

            assertThat(row).isNotNull();
            assertThat(row.getId()).isNotNull();
            assertThat(row.getCategory()).isEqualTo("reply");
            assertThat(row.getTopic()).isEqualTo("CUSTOM.REPLY.Q");
            assertThat(row.getKey()).isEqualTo("key-123");
            assertThat(row.getType()).isEqualTo("CommandCompleted");
            assertThat(row.getPayload()).isEqualTo("{\"result\":\"success\"}");
            assertThat(row.getAttempts()).isEqualTo(0);

            // Verify merged headers
            assertThat(row.getHeaders()).containsEntry("correlationId", correlationId.toString());
            assertThat(row.getHeaders()).containsEntry("replyTo", "CUSTOM.REPLY.Q");
            assertThat(row.getHeaders()).containsEntry("customHeader", "value");
        }

        @Test
        @DisplayName("rowMqReply - should use default reply queue when replyTo not in envelope")
        void testRowMqReplyDefaultQueue() {
            UUID correlationId = UUID.randomUUID();
            Envelope env =
                    new Envelope(
                            UUID.randomUUID(),
                            "reply",
                            "CommandFailed",
                            UUID.randomUUID(),
                            correlationId,
                            UUID.randomUUID(),
                            Instant.now(),
                            "key-456",
                            Map.of(),
                            "{}");

            Outbox row = Outbox.newMqReply(env, "CommandFailed", "{\"error\":\"test\"}", config);

            assertThat(row.getTopic()).isEqualTo("APP.CMD.REPLY.Q");
            assertThat(row.getHeaders()).containsEntry("correlationId", correlationId.toString());
        }

        @Test
        @DisplayName("rowMqReply - should use custom default reply queue")
        void testRowMqReplyCustomDefaultQueue() {
            MessagingConfig customConfig = new MessagingConfig();
            customConfig.getQueueNaming().setReplyQueue("MY.REPLY.QUEUE");

            UUID correlationId = UUID.randomUUID();
            Envelope env =
                    new Envelope(
                            UUID.randomUUID(),
                            "reply",
                            "Reply",
                            UUID.randomUUID(),
                            correlationId,
                            UUID.randomUUID(),
                            Instant.now(),
                            "key",
                            Map.of(),
                            "{}");

            Outbox row = Outbox.newMqReply(env, "Reply", "{}", customConfig);

            assertThat(row.getTopic()).isEqualTo("MY.REPLY.QUEUE");
        }

        @Test
        @DisplayName("rowMqReply - should handle empty envelope headers")
        void testRowMqReplyEmptyHeaders() {
            UUID correlationId = UUID.randomUUID();
            Envelope env =
                    new Envelope(
                            UUID.randomUUID(),
                            "reply",
                            "TestReply",
                            UUID.randomUUID(),
                            correlationId,
                            UUID.randomUUID(),
                            Instant.now(),
                            "key",
                            Map.of(),
                            "{}");

            Outbox row = Outbox.newMqReply(env, "TestReply", "{}", config);

            assertThat(row.getHeaders()).containsEntry("correlationId", correlationId.toString());
            assertThat(row.getHeaders()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("should use placeholder ID (0L) for database auto-generation")
        void testPlaceholderIds() {
            Outbox row1 = Outbox.newKafkaEvent("topic", "key", "type", "{}");
            Outbox row2 = Outbox.newKafkaEvent("topic", "key", "type", "{}");

            assertThat(row1.getId()).isEqualTo(0L);
            assertThat(row2.getId()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should work with custom config")
        void testCustomConfig() {
            MessagingConfig customConfig = new MessagingConfig();
            customConfig.getQueueNaming().setCommandPrefix("PROD.CMD.");
            customConfig.getQueueNaming().setQueueSuffix(".QUEUE");
            customConfig.getQueueNaming().setReplyQueue("PROD.REPLY.QUEUE");

            Outbox cmdRow =
                    Outbox.newCommandRequested("Test", UUID.randomUUID(), "key", "{}", Map.of(), customConfig);
            assertThat(cmdRow.getTopic()).isEqualTo("PROD.CMD.TEST.QUEUE");

            Envelope env =
                    new Envelope(
                            UUID.randomUUID(),
                            "reply",
                            "Reply",
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            Instant.now(),
                            "key",
                            Map.of(),
                            "{}");
            Outbox replyRow = Outbox.newMqReply(env, "Reply", "{}", customConfig);
            assertThat(replyRow.getTopic()).isEqualTo("PROD.REPLY.QUEUE");
        }
    }
}
