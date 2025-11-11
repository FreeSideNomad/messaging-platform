package com.acme.reliable.processor;

/**
 * Central constants for test timing and synchronization delays.
 * These constants eliminate magic numbers and ensure consistent timing
 * across async/concurrent test scenarios.
 */
public final class TestTimingConstants {

    // Short delays - for quick sync between operations
    public static final long DELAY_50_MS = 50L;
    public static final long DELAY_100_MS = 100L;
    public static final long DELAY_200_MS = 200L;

    // Medium delays - for allowing processing to occur
    public static final long DELAY_500_MS = 500L;
    public static final long DELAY_800_MS = 800L;

    // Long delays - for timeout scenarios
    public static final long DELAY_1_SEC = 1000L;
    public static final long DELAY_1900_MS = 1900L;
    public static final long DELAY_2_SEC = 2000L;
    public static final long DELAY_2500_MS = 2500L;
    public static final long DELAY_5_SEC = 5000L;

    private TestTimingConstants() {
        // Utility class - prevent instantiation
    }
}
