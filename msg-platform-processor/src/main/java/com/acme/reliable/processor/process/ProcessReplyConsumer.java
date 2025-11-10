package com.acme.reliable.processor.process;

import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.CommandReply;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes command replies from IBM MQ and routes them to ProcessManager
 */
@Singleton
public class ProcessReplyConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessReplyConsumer.class);

    private final ProcessManager processManager;

    public ProcessReplyConsumer(ProcessManager processManager) {
        this.processManager = processManager;
    }

    /**
     * Listen to reply queue and route to process manager STUB: To be fully implemented when JMS
     * configuration is added
     */
    // @JMSListener("connectionFactory")
    // @Queue("APP.CMD.REPLY.Q")
    public void onReply(String body) {
        try {
            // Parse reply message
            @SuppressWarnings("unchecked")
            Map<String, Object> message = Jsons.fromJson(body, Map.class);

            UUID commandId = UUID.fromString((String) message.get("commandId"));
            UUID correlationId = UUID.fromString((String) message.get("correlationId"));
            String type = (String) message.get("type");

            LOG.debug(
                    "Received reply: command={} correlation={} type={}", commandId, correlationId, type);

            // Build CommandReply
            CommandReply reply = parseReply(message, type, commandId, correlationId);

            // Route to process manager
            processManager.handleReply(correlationId, commandId, reply);

        } catch (Exception e) {
            LOG.error("Failed to process reply message", e);
            // Don't throw - would cause redelivery which might fail again
            // In production, could send to DLQ or alert
        }
    }

    private CommandReply parseReply(
            Map<String, Object> message, String type, UUID commandId, UUID correlationId) {
        return switch (type) {
            case "CommandCompleted" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload =
                        (Map<String, Object>) message.getOrDefault("payload", Map.of());
                @SuppressWarnings("unchecked")
                Map<String, Object> data = payload.isEmpty() ? Map.of() : payload;
                yield CommandReply.completed(commandId, correlationId, data);
            }
            case "CommandFailed" -> {
                String error = (String) message.getOrDefault("error", "Unknown error");
                yield CommandReply.failed(commandId, correlationId, error);
            }
            case "CommandTimedOut" -> {
                String error = (String) message.getOrDefault("error", "Command timed out");
                yield CommandReply.timedOut(commandId, correlationId, error);
            }
            default -> throw new IllegalArgumentException("Unknown reply type: " + type);
        };
    }
}
