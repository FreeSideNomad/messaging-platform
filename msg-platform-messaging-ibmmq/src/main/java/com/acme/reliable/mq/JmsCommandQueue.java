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
    private final ThreadLocal<SessionHolder> sessionPool;

    public JmsCommandQueue(@Named("mqConnectionFactory") jakarta.jms.ConnectionFactory cf) {
        try {
            this.connection = cf.createConnection();
            this.connection.start();
            LOG.info("JMS connection initialized and started");
        } catch (jakarta.jms.JMSException e) {
            throw new RuntimeException("Failed to initialize JMS connection", e);
        }

        // Initialize ThreadLocal after connection is established
        this.sessionPool =
                ThreadLocal.withInitial(
                        () -> {
                            try {
                                LOG.debug(
                                        "Creating new JMS session for thread {}", Thread.currentThread().getName());
                                return new SessionHolder(
                                        connection.createSession(false, jakarta.jms.Session.AUTO_ACKNOWLEDGE));
                            } catch (jakarta.jms.JMSException e) {
                                throw new RuntimeException("Failed to create JMS session", e);
                            }
                        });
    }

    @Override
    public void send(String queue, String body, java.util.Map<String, String> headers) {
        SessionHolder holder = sessionPool.get();

        try {
            var session = holder.session;
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

            // Reuse the pooled producer
            holder.producer.send(dest, msg);
            // No need to commit in AUTO_ACKNOWLEDGE mode

        } catch (Exception e) {
            try {
                holder.session.rollback();
            } catch (jakarta.jms.JMSException rollbackEx) {
                LOG.warn("Failed to rollback JMS session", rollbackEx);
            }

            // On error, invalidate the session and create a new one
            holder.close();
            sessionPool.remove();

            LOG.error("Error details for queue {}: {}", queue, e.getMessage(), e);
            throw new RuntimeException("Failed to send message to queue: " + queue, e);
        }
    }

    @PreDestroy
    void shutdown() {
        LOG.info("Shutting down JMS connection pool");
        try {
            // Clean up thread-local sessions
            sessionPool.remove();
            connection.close();
        } catch (Exception e) {
            LOG.warn("Error during JMS shutdown", e);
        }
    }

    /**
     * Holds a JMS session and producer per thread for reuse. This dramatically improves performance
     * by avoiding session/producer creation overhead.
     */
    private static class SessionHolder {
        final jakarta.jms.Session session;
        final jakarta.jms.MessageProducer producer;

        SessionHolder(jakarta.jms.Session session) throws jakarta.jms.JMSException {
            this.session = session;
            // Create a generic producer (destination set per send call)
            this.producer = session.createProducer(null);
        }

        void close() {
            try {
                if (producer != null) {
                    producer.close();
                }
            } catch (jakarta.jms.JMSException e) {
                LOG.debug("Error closing producer", e);
            }
            try {
                if (session != null) {
                    session.close();
                }
            } catch (jakarta.jms.JMSException e) {
                LOG.debug("Error closing session", e);
            }
        }
    }
}
