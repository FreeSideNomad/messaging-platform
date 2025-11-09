package com.acme.reliable.domain;

import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.core.Envelope;
import com.acme.reliable.core.Jsons;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Outbox domain entity (pure domain object, no persistence annotations). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Outbox {

  private Long id;
  private String category;
  private String topic;
  private String key;
  private String type;
  private String payload;
  private Map<String, String> headers;
  private String status;
  private int attempts;
  private Instant nextAt;
  private String claimedBy;
  private Instant createdAt;
  private Instant publishedAt;
  private String lastError;

  // Constructor for common use case
  public Outbox(
      Long id,
      String category,
      String topic,
      String key,
      String type,
      String payload,
      Map<String, String> headers,
      String status,
      int attempts) {
    this.id = id;
    this.category = category;
    this.topic = topic;
    this.key = key;
    this.type = type;
    this.payload = payload;
    this.headers = headers;
    this.status = status;
    this.attempts = attempts;
  }

  // Factory methods (previously in core.Outbox helper class)
  public static Outbox newCommandRequested(
      String name,
      UUID id,
      String key,
      String payload,
      Map<String, String> reply,
      MessagingConfig config) {
    return new Outbox(
        0L,
        "command",
        config.getQueueNaming().buildCommandQueue(name),
        key,
        "CommandRequested",
        payload,
        Jsons.merge(
            reply,
            Map.of(
                "commandId", id.toString(),
                "commandName", name,
                "businessKey", key)),
        null,
        0);
  }

  public static Outbox newKafkaEvent(String topic, String key, String type, String payload) {
    return new Outbox(0L, "event", topic, key, type, payload, Map.of(), null, 0);
  }

  public static Outbox newMqReply(
      Envelope env, String type, String payload, MessagingConfig config) {
    String replyTo =
        env.headers().getOrDefault("replyTo", config.getQueueNaming().getReplyQueue());
    return new Outbox(
        0L,
        "reply",
        replyTo,
        env.key(),
        type,
        payload,
        Jsons.merge(env.headers(), Map.of("correlationId", env.correlationId().toString())),
        null,
        0);
  }
}
