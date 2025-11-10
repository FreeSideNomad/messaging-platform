package com.acme.reliable.service;

import java.util.UUID;

/**
 * Service for Dead Letter Queue operations
 */
public interface DlqService {

    /**
     * Park a failed command in the DLQ for manual intervention
     */
    void park(
            UUID commandId,
            String commandName,
            String businessKey,
            String payload,
            String failedStatus,
            String errorClass,
            String errorMessage,
            int attempts,
            String parkedBy);
}
