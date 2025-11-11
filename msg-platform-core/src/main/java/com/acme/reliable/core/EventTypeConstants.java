package com.acme.reliable.core;

/**
 * Central constants for event type strings used across the messaging platform.
 * These constants eliminate magic strings and ensure consistency across command,
 * reply, and domain event handling.
 */
public final class EventTypeConstants {

    // Command event types
    public static final String COMMAND_COMPLETED = "CommandCompleted";
    public static final String COMMAND_FAILED = "CommandFailed";
    public static final String COMMAND_TIMED_OUT = "CommandTimedOut";
    public static final String COMMAND_REQUESTED = "CommandRequested";

    // Reply event types
    public static final String REPLY_RECEIVED = "ReplyReceived";
    public static final String REPLY_TIMEOUT = "ReplyTimeout";

    private EventTypeConstants() {
        // Utility class - prevent instantiation
    }
}
