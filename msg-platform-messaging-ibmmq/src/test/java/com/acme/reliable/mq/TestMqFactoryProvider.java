package com.acme.reliable.mq;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;

/**
 * Test-only factory providing embedded ActiveMQ for integration testing.
 *
 * <p>Activated only in test environment via {@code @Requires} annotation.
 * Uses VM transport ({@code vm://...}) for in-process messaging with no persistence.
 *
 * <p>This allows testing of message flow across modules without:
 * <ul>
 *   <li>External message broker</li>
 *   <li>Docker containers</li>
 *   <li>Testcontainers</li>
 *   <li>External processes</li>
 * </ul>
 *
 * <p>The VM transport creates an embedded broker in the same JVM process.
 * Multiple connections to the same {@code vm://} URI share the same broker instance.
 *
 * <p><strong>How it works:</strong>
 * <ol>
 *   <li>Micronaut detects test environment (via MICRONAUT_ENVIRONMENTS=test)</li>
 *   <li>{@code TestMqFactoryProvider} is loaded instead of {@code IbmMqFactoryProvider}</li>
 *   <li>ActiveMQ embedded broker is created in-process (non-persistent)</li>
 *   <li>@JmsListener consumers and JmsTemplate producers connect to same broker</li>
 *   <li>Messages actually queue and are delivered (real JMS semantics)</li>
 * </ol>
 *
 * @see IbmMqFactoryProvider for production factory
 * @see <a href="https://activemq.apache.org/vm-transport-reference">VM Transport Reference</a>
 */
@Requires(env = "test")
@Factory
public class TestMqFactoryProvider {

    /**
     * Creates and starts an embedded ActiveMQ broker for testing.
     *
     * @return BrokerService configured for in-memory operation
     * @throws Exception if broker creation or startup fails
     */
    @Bean(preDestroy = "stop")
    public BrokerService brokerService() throws Exception {
        // Create broker for testing with non-persistent in-memory storage
        BrokerService broker = new BrokerService();

        // Disable persistence - messages are only in memory
        broker.setPersistent(false);

        // Use VM transport only (no network transports needed for testing)
        broker.addConnector("vm://localhost");

        // Set broker name for identification
        broker.setBrokerName("EmbeddedTestBroker");

        // Don't use JMX in tests
        broker.setUseJmx(false);

        // Start the broker
        broker.start();
        broker.waitUntilStarted();

        return broker;
    }

    /**
     * Provides ActiveMQ ConnectionFactory using VM transport.
     *
     * <p>Connects to the embedded broker created by {@link #brokerService()}.
     * URI: {@code vm://localhost}
     * <ul>
     *   <li>{@code vm://} - In-process transport (no network overhead)</li>
     *   <li>{@code localhost} - Connects to local broker</li>
     * </ul>
     *
     * <p><strong>Benefits:</strong>
     * <ul>
     *   <li>No external process needed</li>
     *   <li>No Docker required</li>
     *   <li>Messages are real JMS (not mocked)</li>
     *   <li>Automatic cleanup on JVM shutdown</li>
     *   <li>Sub-millisecond latency (in-process)</li>
     * </ul>
     *
     * @return ConnectionFactory configured for embedded messaging
     */
    @JMSConnectionFactory("mqConnectionFactory")
    public ConnectionFactory mqConnectionFactory() {
        // VM transport connects to the broker created in brokerService()
        return new ActiveMQConnectionFactory("vm://localhost");
    }
}
