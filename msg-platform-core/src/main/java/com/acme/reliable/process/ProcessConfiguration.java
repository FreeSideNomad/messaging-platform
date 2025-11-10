package com.acme.reliable.process;

/**
 * Configuration for process retry and error handling policies. Separate from ProcessGraph to allow
 * runtime configuration changes.
 */
public interface ProcessConfiguration {

    /**
     * Unique identifier for this process type (e.g., "SubmitPayment", "OpenAccount")
     */
    String getProcessType();

    /**
     * Define the process flow as a DAG using the fluent builder API.
     */
    ProcessGraph defineProcess();

    /**
     * Determine if a step failure is retryable based on the error
     *
     * @param step  the step that failed
     * @param error the error message
     * @return true if the step should be retried
     */
    default boolean isRetryable(String step, String error) {
        // Default: transient errors are retryable
        return error != null
                && (error.contains("timeout")
                || error.contains("connection")
                || error.contains("unavailable"));
    }

    /**
     * Maximum number of retries for a step
     *
     * @param step the step name
     * @return max retry count (default: 3)
     */
    default int getMaxRetries(String step) {
        return 3;
    }

    /**
     * Get retry delay in milliseconds for a given attempt
     *
     * @param step    the step name
     * @param attempt the attempt number (0-based)
     * @return delay in milliseconds before retry
     */
    default long getRetryDelay(String step, int attempt) {
        // Exponential backoff: 1s, 2s, 4s, 8s, capped at 30s
        long delayMs = (long) Math.pow(2, attempt) * 1000;
        return Math.min(delayMs, 30_000);
    }
}
