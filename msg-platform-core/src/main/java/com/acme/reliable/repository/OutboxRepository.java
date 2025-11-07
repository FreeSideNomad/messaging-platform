package com.acme.reliable.repository;

/** Repository for Outbox pattern - transactional outbox for reliable message publishing */
public interface OutboxRepository {

  /** Insert a new outbox entry */
  void insert(
      long id,
      String category,
      String topic,
      String key,
      String type,
      String payload,
      String headers,
      String status,
      int attempts);

  /** Mark an outbox entry as published */
  void markPublished(long id);

  /** Reschedule an outbox entry with backoff after failure */
  void reschedule(long id, long backoffMs, String error);
}
