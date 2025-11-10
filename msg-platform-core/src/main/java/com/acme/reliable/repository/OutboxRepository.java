package com.acme.reliable.repository;

import com.acme.reliable.domain.Outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Outbox pattern - transactional outbox for reliable message publishing
 */
public interface OutboxRepository {

    // Insert operations

    /**
     * Insert a new outbox entry and return the generated ID
     */
    long insertReturningId(
            String category, String topic, String key, String type, String payload, String headers);

    // Publish/failure operations

    /**
     * Mark an outbox entry as published
     */
    void markPublished(long id);

    /**
     * Mark an outbox entry as failed and reschedule with backoff
     */
    void markFailed(long id, String error, Instant nextAttempt);

    /**
     * Reschedule an outbox entry with backoff after failure
     */
    void reschedule(long id, long backoffMs, String error);

    // Query/Claim operations

    /**
     * Claim a single outbox entry by ID if it's in NEW status
     */
    Optional<Outbox> claimIfNew(long id);

    /**
     * Claim a single outbox entry by ID (alias for claimIfNew)
     */
    default Optional<Outbox> claimOne(long id) {
        return claimIfNew(id);
    }

    /**
     * Claim a batch of outbox entries for processing (includes NEW, SENDING with timeout recovery, and FAILED)
     */
    List<Outbox> sweepBatch(int max);

    /**
     * Claim a batch of outbox entries with timeout recovery (maps to sweepBatch, claimer param for future use)
     */
    default List<Outbox> claim(int max, String claimer) {
        return sweepBatch(max);
    }

    /**
     * Recover stuck entries that have been in SENDING state too long
     */
    int recoverStuck(Duration olderThan);
}
