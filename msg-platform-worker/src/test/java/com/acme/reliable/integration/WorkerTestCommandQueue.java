package com.acme.reliable.integration;

import com.acme.reliable.spi.CommandQueue;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Test factory that provides a CommandQueue bean for worker integration tests.
 *
 * <p>In test mode, OutboxRelay needs a CommandQueue for publishing commands to the outbox.
 * This factory provides a no-op implementation since commands are processed via direct
 * service calls or outbox patterns in tests rather than JMS message consumption.
 */
@Factory
@Requires(env = "test")
public class WorkerTestCommandQueue {

    private static final Logger logger = Logger.getLogger(WorkerTestCommandQueue.class.getName());

    /**
     * Provides a no-op CommandQueue for tests.
     *
     * <p>In test mode, the CommandConsumer doesn't listen to JMS, so this is just a no-op
     * placeholder to satisfy dependency injection for OutboxRelay and other components that
     * depend on CommandQueue.
     *
     * @return a CommandQueue that discards all messages
     */
    @Singleton
    public CommandQueue commandQueue() {
        logger.info("Providing no-op CommandQueue for worker test environment");
        return new NoOpCommandQueue();
    }

    /**
     * No-op CommandQueue implementation for tests.
     *
     * <p>In test mode, CommandConsumer doesn't listen to JMS, so commands are processed via
     * direct service calls or outbox relay patterns instead of JMS message consumption.
     */
    static class NoOpCommandQueue implements CommandQueue {
        @Override
        public void send(String queueName, String message, Map<String, String> headers) {
            // No-op: In test mode, CommandConsumer doesn't listen to JMS.
            // Commands are processed via direct service calls or outbox relay patterns.
        }
    }
}
