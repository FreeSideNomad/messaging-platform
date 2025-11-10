package com.acme.reliable.performance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Endurance tests validating system stability under sustained load.
 */
@Tag("endurance")
@Disabled("Endurance tests disabled - run manually when needed")
class EnduranceTest extends PerformanceTestBase {

    @Test
    @DisplayName("ENDURANCE: System should handle sustained 200 TPS for 10 minutes")
    void testEndurance_200TPS_10Minutes() throws Exception {
        // Given
        int targetTPS = 200;
        int durationMinutes = 10;
        int totalRequests = targetTPS * durationMinutes * 60;

        System.out.println("\n========================================");
        System.out.println("ENDURANCE TEST: 200 TPS for 10 Minutes");
        System.out.println("========================================");
        System.out.println("Total requests: " + totalRequests);
        System.out.println("Duration: " + durationMinutes + " minutes");
        System.out.println("Target TPS: " + targetTPS);
        System.out.println("========================================\n");

        // When - Submit at steady rate
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        long startTime = System.currentTimeMillis();
        long intervalMs = 1000 / targetTPS; // Time between requests

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            long expectedTime = startTime + (index * intervalMs);

            executor.submit(
                    () -> {
                        try {
                            // Wait until expected time (rate limiting)
                            long now = System.currentTimeMillis();
                            if (now < expectedTime) {
                                Thread.sleep(expectedTime - now);
                            }

                            long reqStart = System.nanoTime();
                            String key = "endurance-" + System.currentTimeMillis() + "-" + index;
                            HttpResponse<String> response = submitCommand("CreateUser", key, "{}");
                            long reqEnd = System.nanoTime();

                            latencies.add((reqEnd - reqStart) / 1_000_000);

                            if (response.statusCode() == 202) {
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }

                            // Log progress every 1000 requests
                            if (index % 1000 == 0 && index > 0) {
                                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                                double actualTPS = (double) index / elapsed;
                                System.out.printf(
                                        "Progress: %d/%d (%.1f%%) | Elapsed: %ds | Actual TPS: %.1f | Success: %d | Failures: %d%n",
                                        index,
                                        totalRequests,
                                        (double) index / totalRequests * 100,
                                        elapsed,
                                        actualTPS,
                                        successCount.get(),
                                        failureCount.get());
                            }
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                            if (index % 1000 == 0) {
                                System.err.println("Error at request " + index + ": " + e.getMessage());
                            }
                        }
                    });
        }

        executor.shutdown();
        boolean completed = executor.awaitTermination(durationMinutes + 5, TimeUnit.MINUTES);

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;

        // Then - Calculate metrics
        Collections.sort(latencies);
        PerformanceMetrics metrics =
                new PerformanceMetrics(
                        totalRequests,
                        successCount.get(),
                        failureCount.get(),
                        durationMs,
                        calculatePercentile(latencies, 0.50),
                        calculatePercentile(latencies, 0.95),
                        calculatePercentile(latencies, 0.99),
                        calculateAverage(latencies));

        System.out.println("\n========================================");
        System.out.println("Endurance Test Results:");
        System.out.println("========================================");
        System.out.println(metrics);
        System.out.println("Test completed: " + completed);
        System.out.println("========================================\n");

        // Assertions
        assertThat(completed).as("Test should complete within timeout").isTrue();

        assertThat(metrics.successRate())
                .as("Success rate should be >98%% over 10 minutes")
                .isGreaterThan(0.98);

        assertThat(metrics.throughputTPS())
                .as("Throughput should be within 90%% of target")
                .isGreaterThan(targetTPS * 0.9);

        assertThat(metrics.p99LatencyMs()).as("P99 latency should be <1000ms").isLessThan(1000);

        // Verify database health
        System.out.println("Verifying database integrity...");
        try (PreparedStatement ps =
                     dbConnection.prepareStatement(
                             "SELECT COUNT(*) FROM command WHERE idempotency_key LIKE 'endurance-%'")) {
            ResultSet rs = ps.executeQuery();
            rs.next();
            int dbCount = rs.getInt(1);
            System.out.println("Commands in database: " + dbCount);

            assertThat(dbCount)
                    .as("At least 95%% of commands should be persisted")
                    .isGreaterThan((int) (totalRequests * 0.95));
        }

        System.out.println("Endurance test completed successfully!\n");
    }

    private long calculatePercentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, index));
    }

    private double calculateAverage(List<Long> values) {
        if (values.isEmpty()) return 0;
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }
}
