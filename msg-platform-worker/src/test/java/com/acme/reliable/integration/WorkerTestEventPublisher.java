package com.acme.reliable.integration;

import com.acme.reliable.spi.EventPublisher;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Test factory that provides an EventPublisher bean for worker integration tests.
 *
 * <p>In test mode, OutboxRelay needs an EventPublisher for publishing events to Kafka.
 * This factory provides a no-op implementation since events are processed via direct
 * service calls or outbox patterns in tests rather than actual Kafka publishing.
 */
@Factory
@Requires(env = "test")
public class WorkerTestEventPublisher {

  private static final Logger logger = Logger.getLogger(WorkerTestEventPublisher.class.getName());

  /**
   * Provides a no-op EventPublisher for tests.
   *
   * <p>In test mode, Kafka is disabled, so this is just a no-op placeholder to satisfy
   * dependency injection for OutboxRelay and other components that depend on EventPublisher.
   *
   * @return an EventPublisher that discards all events
   */
  @Singleton
  public EventPublisher eventPublisher() {
    logger.info("Providing no-op EventPublisher for worker test environment");
    return new NoOpEventPublisher();
  }

  /**
   * No-op EventPublisher implementation for tests.
   *
   * <p>In test mode, Kafka is disabled, so events are not actually published.
   * Events are processed via direct service calls or outbox relay patterns instead.
   */
  static class NoOpEventPublisher implements EventPublisher {
    @Override
    public void publish(String topic, String key, String value, Map<String, String> headers) {
      // No-op: In test mode, Kafka is disabled.
      // Events are processed via direct service calls or outbox relay patterns.
    }
  }
}
