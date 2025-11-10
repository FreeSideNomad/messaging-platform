package com.acme.reliable.processor.test;

import com.acme.reliable.spi.CommandQueue;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * No-op CommandQueue factory for processor integration tests.
 *
 * <p>Provides a placeholder CommandQueue implementation when the "test" environment is active,
 * allowing tests to run without requiring IBM MQ to be configured.
 *
 * <p>This is necessary because the test environment disables the IbmMqFactoryProvider,
 * which would normally provide the CommandQueue bean.
 */
@Factory
@Requires(env = "test")
public class ProcessorTestCommandQueue {

  /**
   * Creates a no-op CommandQueue for testing.
   *
   * @return a CommandQueue that silently ignores all send operations
   */
  @Singleton
  public CommandQueue commandQueue() {
    return new NoOpCommandQueue();
  }

  /** No-op implementation that ignores all sends. */
  static class NoOpCommandQueue implements CommandQueue {
    @Override
    public void send(String queueName, String message, Map<String, String> headers) {
      // No-op: CommandQueue disabled in test mode
    }
  }
}
