package com.acme.reliable.core;

/**
 * Central constants for process and entity status strings.
 * These constants eliminate magic strings and ensure consistency across
 * command, outbox, inbox, and process management.
 */
public final class ProcessStatusConstants {

    // Command/Outbox status values
    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    // Additional common status values
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";

    private ProcessStatusConstants() {
        // Utility class - prevent instantiation
    }
}
