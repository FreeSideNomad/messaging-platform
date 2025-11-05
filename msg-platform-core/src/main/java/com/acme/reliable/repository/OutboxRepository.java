package com.acme.reliable.repository;

import java.util.UUID;

/** Repository for Outbox pattern - transactional outbox for reliable message publishing */
public interface OutboxRepository {

  /** Insert a new outbox entry */
  void insert(
      UUID id,
      String category,
      String topic,
      String key,
      String type,
      String payload,
      String headers,
      String status,
      int attempts);

  /** Mark an outbox entry as published */
  void markPublished(UUID id);

  /** Reschedule an outbox entry with backoff after failure */
  void reschedule(UUID id, long backoffMs, String error);
}
