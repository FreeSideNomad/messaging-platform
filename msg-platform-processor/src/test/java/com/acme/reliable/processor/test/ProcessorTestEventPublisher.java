package com.acme.reliable.processor.test;

import com.acme.reliable.spi.EventPublisher;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * No-op EventPublisher factory for processor integration tests.
 *
 * <p>Provides a placeholder EventPublisher implementation when the "test" environment is active,
 * allowing tests to run without requiring Kafka to be configured.
 *
 * <p>This is necessary because external event publishing is not needed during integration tests
 * where the focus is on testing the processor logic itself.
 */
@Factory
@Requires(env = "test")
public class ProcessorTestEventPublisher {

  /**
   * Creates a no-op EventPublisher for testing.
   *
   * @return an EventPublisher that silently ignores all publish operations
   */
  @Singleton
  public EventPublisher eventPublisher() {
    return new NoOpEventPublisher();
  }

  /** No-op implementation that ignores all publishes. */
  static class NoOpEventPublisher implements EventPublisher {
    @Override
    public void publish(String topic, String key, String value, Map<String, String> headers) {
      // No-op: EventPublisher disabled in test mode
    }
  }
}
