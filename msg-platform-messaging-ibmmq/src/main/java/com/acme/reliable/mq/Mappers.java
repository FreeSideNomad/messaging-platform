package com.acme.reliable.mq;

import com.acme.reliable.core.Envelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper class to map JMS messages to Envelope */
public final class Mappers {
  private static final Logger log = LoggerFactory.getLogger(Mappers.class);
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String HEADER_COMMAND_NAME = "commandName";

  public static Envelope toEnvelope(String text, Message m) throws JMSException {
    JsonNode node = null;
    try {
      node = mapper.readTree(text);
    } catch (Exception parseError) {
      log.warn(
          "Failed to parse JMS payload as JSON. Will proceed using headers only. reason={}",
          parseError.getMessage());
    }

    // Extract headers from JMS message
    Map<String, String> headers = new HashMap<>();
    var enumeration = m.getPropertyNames();
    while (enumeration.hasMoreElements()) {
      String propName = (String) enumeration.nextElement();
      headers.put(propName, m.getStringProperty(propName));
    }

    // Add standard JMS headers
    if (m.getJMSCorrelationID() != null) {
      headers.put("correlationId", m.getJMSCorrelationID());
    }
    if (m.getJMSReplyTo() != null) {
      headers.put("replyTo", m.getJMSReplyTo().toString());
    }

    // Get commandId from headers or generate
    UUID commandId = extractUuid(headers.get("commandId")).orElse(UUID.randomUUID());

    UUID correlationId = extractUuid(headers.get("correlationId")).orElse(commandId);

    UUID messageId = commandId;

    // Extract business key from payload
    String businessKey = headers.get("businessKey");
    if ((businessKey == null || businessKey.isBlank()) && node != null) {
      businessKey =
          node.has("key") ? node.get("key").asText() : node.path("businessKey").asText(null);
    }
    if (businessKey == null || businessKey.isBlank()) {
      businessKey = commandId.toString();
    }

    // Extract command name from payload or headers
    String commandName = headers.get(HEADER_COMMAND_NAME);
    if ((commandName == null || commandName.isBlank()) && node != null) {
      if (node.has(HEADER_COMMAND_NAME)) {
        commandName = node.get(HEADER_COMMAND_NAME).asText();
      }
    }
    if (commandName == null || commandName.isBlank()) {
      if (m.getJMSDestination() != null) {
        commandName = deriveNameFromDestination(m.getJMSDestination().toString());
      } else {
        commandName = "UnknownCommand";
      }
    }

    return new Envelope(
        messageId,
        "CommandRequested",
        commandName,
        commandId,
        correlationId,
        commandId, // causationId same as commandId for now
        Instant.now(),
        businessKey,
        headers,
        text);
  }

  private static Optional<UUID> extractUuid(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(value));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  private static String deriveNameFromDestination(String destination) {
    if (destination == null || destination.isBlank()) {
      return "UnknownCommand";
    }
    String cleaned = destination;
    if (cleaned.startsWith("queue:///")) {
      cleaned = cleaned.substring("queue:///".length());
    }
    if (cleaned.endsWith(".Q")) {
      cleaned = cleaned.substring(0, cleaned.length() - 2);
    }
    int idx = cleaned.lastIndexOf('.');
    return idx >= 0 && idx + 1 < cleaned.length() ? cleaned.substring(idx + 1) : cleaned;
  }
}
