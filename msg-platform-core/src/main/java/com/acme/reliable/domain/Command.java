package com.acme.reliable.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Command domain entity (pure domain object, no persistence annotations). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Command {

  private UUID id;
  private String name;
  private String businessKey;
  private String payload;
  private String idempotencyKey;
  private String status;
  private Instant requestedAt;
  private Instant updatedAt;
  private int retries;
  private Instant processingLeaseUntil;
  private String lastError;
  private String reply;
}
