package com.acme.reliable.performance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Throughput performance tests measuring API capacity under load.
 */
@Tag("performance")
@Disabled("Throughput tests disabled - run manually when needed")
class ThroughputTest extends PerformanceTestBase {

    @Test
    @Order(1)
    @DisplayName("PERF: API should handle 500 TPS with 50 concurrent clients")
    void testThroughput_500TPS_50Concurrent() throws Exception {
        // Given
        int totalRequests = 5000;
        int concurrency = 50;

        System.out.println("\n========================================");
        System.out.println("PERFORMANCE TEST: 500 TPS Target");
        System.out.println("========================================");

        // When
        PerformanceMetrics metrics = submitCommandsWithMetrics(totalRequests, concurrency);

        // Then - Print metrics
        System.out.println("\n" + metrics);
        System.out.println("========================================\n");

        // Assertions
        assertThat(metrics.successRate())
            .as("Success rate should be >99%%")
            .isGreaterThan(0.99);

        assertThat(metrics.throughputTPS())
            .as("Throughput should be >400 TPS")
            .isGreaterThan(400);

        assertThat(metrics.p95LatencyMs())
            .as("P95 latency should be <200ms")
            .isLessThan(200);

        assertThat(metrics.p99LatencyMs())
            .as("P99 latency should be <500ms")
            .isLessThan(500);
    }

    @Test
    @Order(2)
    @DisplayName("PERF: API should handle 1000 TPS with 100 concurrent clients")
    void testThroughput_1000TPS_100Concurrent() throws Exception {
        // Given
        int totalRequests = 10000;
        int concurrency = 100;

        System.out.println("\n========================================");
        System.out.println("PERFORMANCE TEST: 1000 TPS Target");
        System.out.println("========================================");

        // When
        PerformanceMetrics metrics = submitCommandsWithMetrics(totalRequests, concurrency);

        // Then
        System.out.println("\n" + metrics);
        System.out.println("========================================\n");

        assertThat(metrics.successRate())
            .as("Success rate should be >98%%")
            .isGreaterThan(0.98);

        assertThat(metrics.throughputTPS())
            .as("Throughput should be >800 TPS")
            .isGreaterThan(800);

        assertThat(metrics.p95LatencyMs())
            .as("P95 latency should be <300ms")
            .isLessThan(300);
    }

    @Test
    @Order(3)
    @DisplayName("PERF: System should process all commands end-to-end")
    void testEndToEndProcessing_AllCommandsCompleted() throws Exception {
        // Given - Submit 1000 commands
        int commandCount = 1000;

        System.out.println("\n========================================");
        System.out.println("END-TO-END PROCESSING TEST");
        System.out.println("========================================");

        System.out.println("Submitting " + commandCount + " commands...");
        PerformanceMetrics submitMetrics = submitCommandsWithMetrics(commandCount, 50);

        System.out.println("\nSubmit Metrics:");
        System.out.println(submitMetrics);

        // When - Wait for processing (with timeout)
        System.out.println("\nWaiting for workers to process all commands (60s)...");
        Thread.sleep(60000); // 60s for workers to process

        // Then - All should be processed
        try (PreparedStatement ps = dbConnection.prepareStatement(
            "SELECT status, COUNT(*) as cnt FROM command " +
            "WHERE idempotency_key LIKE 'perf-test-%' " +
            "GROUP BY status")) {
            ResultSet rs = ps.executeQuery();

            Map<String, Integer> statusCounts = new HashMap<>();
            while (rs.next()) {
                statusCounts.put(rs.getString("status"), rs.getInt("cnt"));
            }

            System.out.println("\nCommand Status Distribution: " + statusCounts);

            int succeededCount = statusCounts.getOrDefault("SUCCEEDED", 0);
            int pendingCount = statusCounts.getOrDefault("PENDING", 0);
            int runningCount = statusCounts.getOrDefault("RUNNING", 0);
            int failedCount = statusCounts.getOrDefault("FAILED", 0);

            System.out.println("  Succeeded: " + succeededCount);
            System.out.println("  Pending: " + pendingCount);
            System.out.println("  Running: " + runningCount);
            System.out.println("  Failed: " + failedCount);
            System.out.println("========================================\n");

            assertThat(succeededCount)
                .as("At least 95%% of commands should be SUCCEEDED")
                .isGreaterThan((int) (commandCount * 0.95));
        }
    }
}
