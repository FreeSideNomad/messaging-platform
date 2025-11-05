package com.acme.reliable.processor.config;

import com.acme.reliable.command.CommandHandlerRegistry;
import com.acme.reliable.config.MessagingConfig;
import com.acme.reliable.config.TimeoutConfig;
import com.acme.reliable.core.Outbox;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Factory for creating core domain beans with framework-specific configuration.
 *
 * <p>This factory bridges the gap between framework-agnostic core POJOs and Micronaut's dependency
 * injection system. The core module remains free of framework dependencies, while this processor
 * module handles the DI wiring.
 */
@Factory
public class CoreBeansFactory {

  /** Creates TimeoutConfig bean populated from application.yml timeout.* properties */
  @Singleton
  @ConfigurationProperties("timeout")
  public TimeoutConfig timeoutConfig() {
    return new TimeoutConfig();
  }

  /** Creates MessagingConfig bean populated from application.yml messaging.* properties */
  @Singleton
  @ConfigurationProperties("messaging")
  public MessagingConfig messagingConfig() {
    return new MessagingConfig();
  }

  /** Creates CommandHandlerRegistry singleton */
  @Singleton
  public CommandHandlerRegistry commandHandlerRegistry() {
    return new CommandHandlerRegistry();
  }

  /** Creates Outbox singleton with MessagingConfig dependency */
  @Singleton
  public Outbox outbox(MessagingConfig messagingConfig) {
    return new Outbox(messagingConfig);
  }
}
