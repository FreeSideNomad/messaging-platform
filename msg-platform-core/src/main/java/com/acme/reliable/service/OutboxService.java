package com.acme.reliable.service;

import com.acme.reliable.domain.Outbox;

import java.util.List;
import java.util.Optional;

/**
 * Service for Outbox pattern operations
 */
public interface OutboxService {

    /**
     * Add an outbox entry and return its ID
     */
    long addReturningId(Outbox outbox);

    /**
     * Claim a single outbox entry by ID
     */
    Optional<Outbox> claimOne(long id, String claimer);

    /**
     * Claim a batch of outbox entries for processing
     */
    List<Outbox> claim(int max, String claimer);

    /**
     * Mark an outbox entry as published
     */
    void markPublished(long id);

    /**
     * Reschedule an outbox entry with backoff after failure
     */
    void reschedule(long id, long backoffMs, String error);
}
