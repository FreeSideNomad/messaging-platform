package com.acme.payments.infrastructure.messaging;

import com.acme.reliable.spi.CommandQueue;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

/**
 * Test factory that provides a CommandQueue bean for integration tests.
 * In test mode, PaymentCommandConsumer is disabled, so commands are processed
 * via direct service calls or outbox relay instead of JMS message consumption.
 */
@Factory
@Requires(env = "test")
@Slf4j
public class TestJmsCommandQueue {

  @Singleton
  public CommandQueue commandQueue() {
    return new NoOpCommandQueue();
  }

  /**
   * No-op CommandQueue implementation for tests.
   * In test mode, PaymentCommandConsumer is disabled (via notEnv = "test"),
   * so this is just a no-op placeholder to satisfy dependency injection.
   */
  static class NoOpCommandQueue implements CommandQueue {
    @Override
    public void send(String queueName, String message, Map<String, String> headers) {
      // No-op: In test mode, PaymentCommandConsumer doesn't listen to JMS.
      // Commands are processed via direct service calls or outbox relay patterns.
    }
  }
}
