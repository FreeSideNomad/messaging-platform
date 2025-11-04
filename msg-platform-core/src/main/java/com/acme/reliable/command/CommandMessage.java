package com.acme.reliable.command;

import java.util.UUID;

/**
 * Represents a command message for in-memory processing.
 * This is distinct from the Command entity which is used for persistence.
 */
public record CommandMessage(
    UUID commandId,
    UUID correlationId,
    String commandType,
    String payload
) {
}
