package com.acme.payments.integration;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.jms.ConnectionFactory;

import java.util.logging.Logger;

/**
 * Provides embedded ActiveMQ ConnectionFactory for test environment.
 *
 * <p>This factory is only active when the "test" environment is enabled in Micronaut
 * ApplicationContext. It creates an embedded ActiveMQ broker using VM transport
 * (in-process, no external broker needed).
 *
 * <p>Usage: Enable this in tests by running ApplicationContext with "test" environment:
 * <pre>
 * ApplicationContext.run(configuration, "test");
 * </pre>
 */
@Factory
@Requires(env = "test")
public class TestMqFactoryProvider {

    private static final Logger logger = Logger.getLogger(TestMqFactoryProvider.class.getName());

    /**
     * Creates an embedded ActiveMQ ConnectionFactory for testing.
     *
     * <p>Uses VM transport (vm://localhost) which is an in-process transport that doesn't require
     * a separate broker process or Docker container. Perfect for integration tests.
     *
     * @return a ConnectionFactory configured for embedded ActiveMQ
     */
    public ConnectionFactory connectionFactory() {
        logger.info("Creating embedded ActiveMQ ConnectionFactory for test environment");

        try {
            // Try to load ActiveMQ ConnectionFactory using reflection to avoid hard dependency
            Class<?> activeMqFactoryClass =
                    Class.forName("org.apache.activemq.ActiveMQConnectionFactory");

            // VM transport: runs broker in-process, no separate process needed
            // broker:vm://localhost allows multiple JMS clients in same JVM to connect to same broker
            // useAsyncSend=false: ensures synchronous send (good for test determinism)
            // persistenceEnabled=false: keeps messages in memory, not on disk
            String brokerUrl =
                    "vm://localhost?broker.persistent=false&broker.useAsyncSend=false&broker.useShutdownHook=false";

            // Create factory instance using reflection
            Object factory =
                    activeMqFactoryClass
                            .getConstructor(String.class)
                            .newInstance(brokerUrl);

            // Set disable timestamps using reflection
            activeMqFactoryClass
                    .getMethod("setDisableTimeStampsByDefault", boolean.class)
                    .invoke(factory, true);

            logger.info("Embedded ActiveMQ ConnectionFactory created: " + brokerUrl);

            return (ConnectionFactory) factory;
        } catch (ClassNotFoundException e) {
            // ActiveMQ not available - return a null-safe factory
            logger.warning("ActiveMQ not available in classpath. JMS tests will be skipped.");
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ActiveMQ ConnectionFactory", e);
        }
    }
}
