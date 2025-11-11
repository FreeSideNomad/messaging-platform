package com.acme.reliable.mq;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Requires(beans = IbmMqFactoryProvider.class)
public class JmsCommandQueue implements com.acme.reliable.spi.CommandQueue {
    private static final Logger LOG = LoggerFactory.getLogger(JmsCommandQueue.class);
    private static final String HEADER_CORRELATION_ID = "correlationId";
    private static final String HEADER_REPLY_TO = "replyTo";

    private final jakarta.jms.Connection connection;

    public JmsCommandQueue(@Named("mqConnectionFactory") jakarta.jms.ConnectionFactory cf) {
        try {
            this.connection = cf.createConnection();
            this.connection.start();
            LOG.info("JMS connection initialized and started");
        } catch (jakarta.jms.JMSException e) {
            throw new RuntimeException("Failed to initialize JMS connection", e);
        }
    }

    @Override
    public void send(String queue, String body, java.util.Map<String, String> headers) {
        // Create a fresh session for each message to avoid transaction state issues
        jakarta.jms.Session session = null;
        jakarta.jms.MessageProducer producer = null;

        try {
            // Use CLIENT_ACKNOWLEDGE mode to avoid transaction coordinator issues
            session = connection.createSession(false, jakarta.jms.Session.CLIENT_ACKNOWLEDGE);
            producer = session.createProducer(null);

            var dest = session.createQueue(queue);
            var msg = session.createTextMessage(body);

            if (headers != null) {
                if (headers.containsKey(HEADER_CORRELATION_ID)) {
                    msg.setJMSCorrelationID(headers.get(HEADER_CORRELATION_ID));
                }
                if (headers.containsKey(HEADER_REPLY_TO)) {
                    msg.setJMSReplyTo(session.createQueue(headers.get(HEADER_REPLY_TO)));
                }
                for (var e : headers.entrySet()) {
                    String key = e.getKey();
                    // Skip special JMS headers and IBM MQ internal properties
                    if (HEADER_CORRELATION_ID.equals(key)
                            || HEADER_REPLY_TO.equals(key)
                            || "mode".equals(key)
                            || key.startsWith("JMS_IBM_")
                            || key.startsWith("JMSX")) {
                        continue;
                    }
                    msg.setStringProperty(key, e.getValue());
                }
            }

            // Send the message - AUTO_ACKNOWLEDGE mode ensures immediate delivery
            producer.send(dest, msg);
            LOG.debug("Successfully sent message to queue: {}", queue);

        } catch (Exception e) {
            // Log full exception chain including linked exceptions
            LOG.error("Error details for queue {}: {} | Cause: {}",
                queue, e.getMessage(),
                e.getCause() != null ? e.getCause().getMessage() : "null",
                e);
            throw new RuntimeException("Failed to send message to queue: " + queue, e);
        } finally {
            // Close the session and producer
            if (producer != null) {
                try {
                    producer.close();
                } catch (jakarta.jms.JMSException e) {
                    LOG.debug("Error closing producer", e);
                }
            }
            if (session != null) {
                try {
                    session.close();
                } catch (jakarta.jms.JMSException e) {
                    LOG.debug("Error closing session", e);
                }
            }
        }
    }

    @PreDestroy
    void shutdown() {
        LOG.info("Shutting down JMS connection");
        try {
            connection.close();
        } catch (Exception e) {
            LOG.warn("Error during JMS shutdown", e);
        }
    }
}
