package com.acme.reliable.mq;

import com.acme.reliable.command.CommandHandlerRegistry;
import com.acme.reliable.command.CommandMessage;
import com.acme.reliable.process.CommandReply;
import com.acme.reliable.spi.CommandQueue;
import jakarta.jms.JMSException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for JMS command consumers that process commands through the CommandHandlerRegistry.
 * Provides generic command processing and reply handling logic that can be reused across bounded
 * contexts.
 */
public abstract class BaseCommandConsumer {
  private static final Logger log = LoggerFactory.getLogger(BaseCommandConsumer.class);

  private final CommandHandlerRegistry registry;
  private final CommandQueue commandQueue;
  private final String replyQueue;

  protected BaseCommandConsumer(
      CommandHandlerRegistry registry, CommandQueue commandQueue, String replyQueue) {
    this.registry = registry;
    this.commandQueue = commandQueue;
    this.replyQueue = replyQueue;
  }

  /**
   * Generic command processing logic. Extracts command metadata from JMS headers, delegates to
   * CommandHandlerRegistry, and sends reply back to the reply queue.
   */
  protected void processCommand(String commandType, String body, jakarta.jms.Message m)
      throws JMSException {
    log.info("Received {} command", commandType);

    // Extract command metadata from JMS message headers
    String commandIdStr = m.getStringProperty("commandId");
    String correlationIdStr = m.getStringProperty("correlationId");

    UUID commandId = commandIdStr != null ? UUID.fromString(commandIdStr) : UUID.randomUUID();
    UUID correlationId =
        correlationIdStr != null ? UUID.fromString(correlationIdStr) : UUID.randomUUID();

    try {
      // Create command
      CommandMessage command = new CommandMessage(commandId, correlationId, commandType, body);

      log.info("Processing command: type={}, id={}", commandType, commandId);

      // Handle command
      CommandReply reply = registry.handle(command);

      log.info(
          "Command processed: type={}, id={}, status={}", commandType, commandId, reply.status());

      // Send reply back to ProcessManager
      sendReply(correlationId, reply);

    } catch (Exception e) {
      log.error("Error processing {} command", commandType, e);

      // Send error reply
      CommandReply errorReply =
          CommandReply.failed(UUID.randomUUID(), correlationId, e.getMessage());
      sendReply(correlationId, errorReply);
    }
  }

  /** Send command reply back to the reply queue for ProcessManager */
  private void sendReply(UUID correlationId, CommandReply reply) {
    try {
      String replyJson = reply.toJson();

      Map<String, String> headers =
          Map.of(
              "correlationId", correlationId.toString(),
              "commandId", reply.commandId().toString(),
              "status", reply.status().name());

      commandQueue.send(replyQueue, replyJson, headers);

      log.debug("Sent reply to queue: correlationId={}, status={}", correlationId, reply.status());
    } catch (Exception e) {
      log.error("Failed to send reply: correlationId={}", correlationId, e);
    }
  }
}
