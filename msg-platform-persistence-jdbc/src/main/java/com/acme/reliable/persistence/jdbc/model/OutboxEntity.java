package com.acme.reliable.persistence.jdbc.model;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persistence model for Outbox table. Contains Micronaut Data annotations for JDBC mapping. */
@MappedEntity("outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEntity {

  @Id @AutoPopulated private UUID id;

  @MappedProperty(type = DataType.STRING)
  private String category;

  @MappedProperty(type = DataType.STRING)
  private String topic;

  @MappedProperty(value = "key", type = DataType.STRING)
  private String key;

  @MappedProperty(type = DataType.STRING)
  private String type;

  @MappedProperty(type = DataType.JSON)
  private String payload;

  @MappedProperty(type = DataType.JSON)
  private Map<String, String> headers;

  @MappedProperty(type = DataType.STRING)
  private String status;

  private int attempts;

  @Nullable @MappedProperty(value = "next_at", type = DataType.TIMESTAMP)
  private Instant nextAt;

  @Nullable @MappedProperty(value = "claimed_by", type = DataType.STRING)
  private String claimedBy;

  @DateCreated
  @MappedProperty(value = "created_at", type = DataType.TIMESTAMP)
  private Instant createdAt;

  @Nullable @MappedProperty(value = "published_at", type = DataType.TIMESTAMP)
  private Instant publishedAt;

  @Nullable @MappedProperty(value = "last_error", type = DataType.STRING)
  private String lastError;

  // Constructor for common use case
  public OutboxEntity(
      UUID id,
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
