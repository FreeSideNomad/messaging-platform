package com.acme.reliable.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Envelope record
 */
class EnvelopeTest {

    @Test
    @DisplayName("Should create Envelope with all fields")
    void testEnvelopeCreation() {
        UUID messageId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        Map<String, String> headers = Map.of("key", "value");

        Envelope envelope = new Envelope(
            messageId,
            "command",
            "CreateUser",
            commandId,
            correlationId,
            causationId,
            occurredAt,
            "user-123",
            headers,
            "{\"data\":\"test\"}"
        );

        assertThat(envelope.messageId()).isEqualTo(messageId);
        assertThat(envelope.type()).isEqualTo("command");
        assertThat(envelope.name()).isEqualTo("CreateUser");
        assertThat(envelope.commandId()).isEqualTo(commandId);
        assertThat(envelope.correlationId()).isEqualTo(correlationId);
        assertThat(envelope.causationId()).isEqualTo(causationId);
        assertThat(envelope.occurredAt()).isEqualTo(occurredAt);
        assertThat(envelope.key()).isEqualTo("user-123");
        assertThat(envelope.headers()).containsEntry("key", "value");
        assertThat(envelope.payload()).isEqualTo("{\"data\":\"test\"}");
    }

    @Test
    @DisplayName("Should handle null headers")
    void testEnvelopeWithNullHeaders() {
        Envelope envelope = new Envelope(
            UUID.randomUUID(),
            "command",
            "Test",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now(),
            "key",
            null,
            "{}"
        );

        assertThat(envelope.headers()).isNull();
    }

    @Test
    @DisplayName("Should handle null key")
    void testEnvelopeWithNullKey() {
        Envelope envelope = new Envelope(
            UUID.randomUUID(),
            "command",
            "Test",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now(),
            null,
            Map.of(),
            "{}"
        );

        assertThat(envelope.key()).isNull();
    }

    @Test
    @DisplayName("Should support equality comparison")
    void testEnvelopeEquality() {
        UUID messageId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2025-01-01T00:00:00Z");
        Map<String, String> headers = Map.of("key", "value");

        Envelope envelope1 = new Envelope(
            messageId, "command", "Test", commandId, correlationId,
            causationId, occurredAt, "key", headers, "{}"
        );

        Envelope envelope2 = new Envelope(
            messageId, "command", "Test", commandId, correlationId,
            causationId, occurredAt, "key", headers, "{}"
        );

        assertThat(envelope1).isEqualTo(envelope2);
        assertThat(envelope1.hashCode()).isEqualTo(envelope2.hashCode());
    }

    @Test
    @DisplayName("Should serialize to JSON")
    void testEnvelopeSerialization() {
        Envelope envelope = new Envelope(
            UUID.randomUUID(),
            "command",
            "Test",
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now(),
            "key",
            Map.of("header", "value"),
            "{\"test\":true}"
        );

        String json = Jsons.toJson(envelope);

        assertThat(json)
            .contains("\"type\":\"command\"")
            .contains("\"name\":\"Test\"")
            .contains("\"key\":\"key\"");
    }

    @Test
    @DisplayName("Should deserialize from JSON")
    void testEnvelopeDeserialization() {
        UUID messageId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2025-01-01T00:00:00Z");

        String json = String.format("""
            {
                "messageId": "%s",
                "type": "command",
                "name": "Test",
                "commandId": "%s",
                "correlationId": "%s",
                "causationId": "%s",
                "occurredAt": "2025-01-01T00:00:00Z",
                "key": "test-key",
                "headers": {"h1": "v1"},
                "payload": "{\\"data\\":\\"test\\"}"
            }
            """, messageId, commandId, correlationId, causationId);

        Envelope envelope = Jsons.fromJson(json, Envelope.class);

        assertThat(envelope.messageId()).isEqualTo(messageId);
        assertThat(envelope.type()).isEqualTo("command");
        assertThat(envelope.name()).isEqualTo("Test");
        assertThat(envelope.commandId()).isEqualTo(commandId);
        assertThat(envelope.correlationId()).isEqualTo(correlationId);
        assertThat(envelope.causationId()).isEqualTo(causationId);
        assertThat(envelope.occurredAt()).isEqualTo(occurredAt);
        assertThat(envelope.key()).isEqualTo("test-key");
        assertThat(envelope.headers()).containsEntry("h1", "v1");
    }
}
