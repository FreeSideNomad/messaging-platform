package com.acme.reliable.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Command persistence and state management
 */
public interface CommandService {

    /**
     * Save a new command in PENDING status
     */
    UUID savePending(String name, String idempotencyKey, String businessKey, String payload, String replyJson);

    /**
     * Find a command by ID
     */
    Optional<Record> find(UUID id);

    /**
     * Mark command as RUNNING with processing lease
     */
    void markRunning(UUID id, Instant leaseUntil);

    /**
     * Mark command as SUCCEEDED
     */
    void markSucceeded(UUID id);

    /**
     * Mark command as FAILED with error message
     */
    void markFailed(UUID id, String error);

    /**
     * Increment retry count and record error
     */
    void bumpRetry(UUID id, String error);

    /**
     * Mark command as TIMED_OUT
     */
    void markTimedOut(UUID id, String reason);

    /**
     * Check if a command with given idempotency key already exists
     */
    boolean existsByIdempotencyKey(String key);

    /**
     * Command record for service layer
     */
    record Record(UUID id, String name, String key, String payload, String status, String reply) {}
}
