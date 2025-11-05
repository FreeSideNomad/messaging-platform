package com.acme.reliable.command;

import com.acme.reliable.core.Jsons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CommandMessage record
 */
class CommandMessageTest {

    @Test
    @DisplayName("Should create CommandMessage with all fields")
    void testCommandMessageCreation() {
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        CommandMessage message = new CommandMessage(
            commandId,
            correlationId,
            "CreateUser",
            "{\"name\":\"John\"}"
        );

        assertThat(message.commandId()).isEqualTo(commandId);
        assertThat(message.correlationId()).isEqualTo(correlationId);
        assertThat(message.commandType()).isEqualTo("CreateUser");
        assertThat(message.payload()).isEqualTo("{\"name\":\"John\"}");
    }

    @Test
    @DisplayName("Should handle null correlation ID")
    void testCommandMessageWithNullCorrelation() {
        UUID commandId = UUID.randomUUID();

        CommandMessage message = new CommandMessage(
            commandId,
            null,
            "CreateUser",
            "{}"
        );

        assertThat(message.commandId()).isEqualTo(commandId);
        assertThat(message.correlationId()).isNull();
    }

    @Test
    @DisplayName("Should support equality comparison")
    void testCommandMessageEquality() {
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        CommandMessage message1 = new CommandMessage(
            commandId,
            correlationId,
            "CreateUser",
            "{\"name\":\"John\"}"
        );

        CommandMessage message2 = new CommandMessage(
            commandId,
            correlationId,
            "CreateUser",
            "{\"name\":\"John\"}"
        );

        assertThat(message1).isEqualTo(message2);
        assertThat(message1.hashCode()).isEqualTo(message2.hashCode());
    }

    @Test
    @DisplayName("Should not equal when command IDs differ")
    void testCommandMessageInequality() {
        UUID correlationId = UUID.randomUUID();

        CommandMessage message1 = new CommandMessage(
            UUID.randomUUID(),
            correlationId,
            "CreateUser",
            "{}"
        );

        CommandMessage message2 = new CommandMessage(
            UUID.randomUUID(),
            correlationId,
            "CreateUser",
            "{}"
        );

        assertThat(message1).isNotEqualTo(message2);
    }

    @Test
    @DisplayName("Should serialize to JSON")
    void testCommandMessageSerialization() {
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        CommandMessage message = new CommandMessage(
            commandId,
            correlationId,
            "CreateUser",
            "{\"name\":\"John\"}"
        );

        String json = Jsons.toJson(message);

        assertThat(json)
            .contains("\"commandType\":\"CreateUser\"")
            .contains("\"payload\":");
    }

    @Test
    @DisplayName("Should deserialize from JSON")
    void testCommandMessageDeserialization() {
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        String json = String.format("""
            {
                "commandId": "%s",
                "correlationId": "%s",
                "commandType": "CreateUser",
                "payload": "{\\"name\\":\\"John\\"}"
            }
            """, commandId, correlationId);

        CommandMessage message = Jsons.fromJson(json, CommandMessage.class);

        assertThat(message.commandId()).isEqualTo(commandId);
        assertThat(message.correlationId()).isEqualTo(correlationId);
        assertThat(message.commandType()).isEqualTo("CreateUser");
        assertThat(message.payload()).contains("John");
    }

    @Test
    @DisplayName("Should handle empty payload")
    void testCommandMessageEmptyPayload() {
        CommandMessage message = new CommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "NoDataCommand",
            ""
        );

        assertThat(message.payload()).isEmpty();
    }

    @Test
    @DisplayName("Should handle complex JSON payload")
    void testCommandMessageComplexPayload() {
        String complexPayload = "{\"user\":{\"name\":\"John\",\"age\":30},\"metadata\":{\"source\":\"api\"}}";

        CommandMessage message = new CommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "CreateUser",
            complexPayload
        );

        assertThat(message.payload()).contains("John");
        assertThat(message.payload()).contains("metadata");
        assertThat(message.payload()).contains("source");
    }
}
