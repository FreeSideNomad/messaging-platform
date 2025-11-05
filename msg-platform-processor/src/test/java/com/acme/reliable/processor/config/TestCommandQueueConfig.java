package com.acme.reliable.processor.config;

import com.acme.reliable.spi.CommandQueue;
import com.acme.reliable.spi.EventPublisher;
import com.acme.reliable.spi.HandlerRegistry;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Test configuration that provides no-op implementations for messaging infrastructure.
 * This replaces the real MQ/Kafka implementations in test environments.
 */
@Factory
@Requires(env = "test")
public class TestCommandQueueConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TestCommandQueueConfig.class);

    @Singleton
    @Replaces(CommandQueue.class)
    public CommandQueue commandQueue() {
        return new NoOpCommandQueue();
    }

    @Singleton
    @Replaces(EventPublisher.class)
    public EventPublisher eventPublisher() {
        return new NoOpEventPublisher();
    }

    @Singleton
    @Replaces(HandlerRegistry.class)
    public HandlerRegistry handlerRegistry() {
        return new NoOpHandlerRegistry();
    }

    /**
     * No-op implementation of CommandQueue for testing.
     * Just logs the send operation without actually sending to MQ.
     */
    static class NoOpCommandQueue implements CommandQueue {
        private static final Logger LOG = LoggerFactory.getLogger(NoOpCommandQueue.class);

        @Override
        public void send(String queue, String body, Map<String, String> headers) {
            LOG.debug("Test CommandQueue.send() called - queue: {}, body length: {}, headers: {}",
                queue, body != null ? body.length() : 0, headers.size());
        }
    }

    /**
     * No-op implementation of EventPublisher for testing.
     * Just logs the publish operation without actually publishing to Kafka.
     */
    static class NoOpEventPublisher implements EventPublisher {
        private static final Logger LOG = LoggerFactory.getLogger(NoOpEventPublisher.class);

        @Override
        public void publish(String topic, String key, String value, Map<String, String> headers) {
            LOG.debug("Test EventPublisher.publish() called - topic: {}, key: {}, value length: {}, headers: {}",
                topic, key, value != null ? value.length() : 0, headers.size());
        }
    }

    /**
     * No-op implementation of HandlerRegistry for testing.
     * Returns a default JSON response.
     */
    static class NoOpHandlerRegistry implements HandlerRegistry {
        private static final Logger LOG = LoggerFactory.getLogger(NoOpHandlerRegistry.class);

        @Override
        public String invoke(String commandName, String payload) {
            LOG.debug("Test HandlerRegistry.invoke() called - commandName: {}, payload length: {}",
                commandName, payload != null ? payload.length() : 0);
            return "{}";  // Default empty JSON response
        }
    }
}
