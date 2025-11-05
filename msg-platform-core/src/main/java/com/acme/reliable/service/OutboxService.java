package com.acme.reliable.service;

import com.acme.reliable.spi.OutboxRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Service for Outbox pattern operations */
public interface OutboxService {

  /** Add an outbox entry and return its ID */
  UUID addReturningId(OutboxRow row);

  /** Claim a single outbox entry by ID */
  Optional<OutboxRow> claimOne(UUID id);

  /** Claim a batch of outbox entries for processing */
  List<OutboxRow> claim(int max, String claimer);

  /** Mark an outbox entry as published */
  void markPublished(UUID id);

  /** Reschedule an outbox entry with backoff after failure */
  void reschedule(UUID id, long backoffMs, String error);
}
