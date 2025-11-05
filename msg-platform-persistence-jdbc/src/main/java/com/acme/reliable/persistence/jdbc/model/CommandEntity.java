package com.acme.reliable.persistence.jdbc.model;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Persistence model for Command table. Contains Micronaut Data annotations for JDBC mapping. */
@MappedEntity("command")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommandEntity {

  @Id @AutoPopulated private UUID id;

  @MappedProperty(type = DataType.STRING)
  private String name;

  @MappedProperty(value = "business_key", type = DataType.STRING)
  private String businessKey;

  @MappedProperty(type = DataType.JSON)
  private String payload;

  @MappedProperty(value = "idempotency_key", type = DataType.STRING)
  private String idempotencyKey;

  @MappedProperty(type = DataType.STRING)
  private String status;

  @DateCreated
  @MappedProperty(value = "requested_at", type = DataType.TIMESTAMP)
  private Instant requestedAt;

  @DateUpdated
  @MappedProperty(value = "updated_at", type = DataType.TIMESTAMP)
  private Instant updatedAt;

  @MappedProperty(type = DataType.INTEGER)
  private int retries;

  @Nullable @MappedProperty(value = "processing_lease_until", type = DataType.TIMESTAMP)
  private Instant processingLeaseUntil;

  @Nullable @MappedProperty(value = "last_error", type = DataType.STRING)
  private String lastError;

  @MappedProperty(type = DataType.JSON)
  private String reply;
}
