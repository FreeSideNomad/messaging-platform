package com.acme.reliable.domain;

import java.time.Instant;
import java.util.Map;
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
}
