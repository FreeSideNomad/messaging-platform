package com.acme.reliable.performance;

import com.acme.reliable.e2e.E2ETestBase;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;

/** Base class for performance tests providing metrics collection and reporting capabilities. */
@Tag("performance")
public abstract class PerformanceTestBase extends E2ETestBase {

  protected PerformanceMetrics submitCommandsWithMetrics(int totalRequests, int concurrency)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(concurrency);
    CountDownLatch latch = new CountDownLatch(totalRequests);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);
    List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

    System.out.println(
        "Starting performance test: "
            + totalRequests
            + " requests with "
            + concurrency
            + " concurrent clients");

    long startTime = System.currentTimeMillis();

    for (int i = 0; i < totalRequests; i++) {
      final int index = i;
      executor.submit(
          () -> {
            try {
              long reqStart = System.nanoTime();
              String key = "perf-test-" + System.currentTimeMillis() + "-" + index;
              HttpResponse<String> response = submitCommand("CreateUser", key, "{}");
              long reqEnd = System.nanoTime();

              latencies.add((reqEnd - reqStart) / 1_000_000); // Convert to ms

              if (response.statusCode() == 202) {
                successCount.incrementAndGet();
              } else {
                failureCount.incrementAndGet();
              }
            } catch (Exception e) {
              failureCount.incrementAndGet();
            } finally {
              latch.countDown();
            }
          });
    }

    latch.await();
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.MINUTES);

    long endTime = System.currentTimeMillis();
    long durationMs = endTime - startTime;

    Collections.sort(latencies);

    return new PerformanceMetrics(
        totalRequests,
        successCount.get(),
        failureCount.get(),
        durationMs,
        calculatePercentile(latencies, 0.50),
        calculatePercentile(latencies, 0.95),
        calculatePercentile(latencies, 0.99),
        calculateAverage(latencies));
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

  protected record PerformanceMetrics(
      int totalRequests,
      int successCount,
      int failureCount,
      long durationMs,
      long p50LatencyMs,
      long p95LatencyMs,
      long p99LatencyMs,
      double avgLatencyMs) {
    public double throughputTPS() {
      return (double) totalRequests / (durationMs / 1000.0);
    }

    public double successRate() {
      return (double) successCount / totalRequests;
    }

    @Override
    public String toString() {
      return String.format(
          "Performance Metrics:%n"
              + "  Total Requests: %d%n"
              + "  Success: %d (%.2f%%)%n"
              + "  Failures: %d%n"
              + "  Duration: %d ms (%.2f seconds)%n"
              + "  Throughput: %.2f TPS%n"
              + "  Latency - P50: %d ms, P95: %d ms, P99: %d ms, Avg: %.2f ms",
          totalRequests,
          successCount,
          successRate() * 100,
          failureCount,
          durationMs,
          durationMs / 1000.0,
          throughputTPS(),
          p50LatencyMs,
          p95LatencyMs,
          p99LatencyMs,
          avgLatencyMs);
    }
  }
}
