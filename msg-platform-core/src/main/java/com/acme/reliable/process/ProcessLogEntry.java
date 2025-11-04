package com.acme.reliable.process;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable log entry for process event sourcing
 */
public record ProcessLogEntry(
    UUID processId,
    long sequence,
    Instant timestamp,
    ProcessEvent event
) {
    public static ProcessLogEntry create(UUID processId, long sequence, ProcessEvent event) {
        return new ProcessLogEntry(processId, sequence, Instant.now(), event);
    }
}
