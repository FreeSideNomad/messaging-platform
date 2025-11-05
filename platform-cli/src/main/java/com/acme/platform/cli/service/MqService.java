package com.acme.platform.cli.service;

import com.acme.platform.cli.config.CliConfiguration;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class MqService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MqService.class);
    private static MqService instance;
    private final ConnectionFactory factory;
    private final CliConfiguration config;
    private Connection connection;

    private MqService() {
        this.config = CliConfiguration.getInstance();
        this.factory = new ConnectionFactory();
        factory.setHost(config.getRabbitmqHost());
        factory.setPort(config.getRabbitmqPort());
        factory.setUsername(config.getRabbitmqUser());
        factory.setPassword(config.getRabbitmqPassword());
        factory.setVirtualHost(config.getRabbitmqVhost());
        factory.setConnectionTimeout(10000);
        logger.info("MQ service initialized");
    }

    public static synchronized MqService getInstance() {
        if (instance == null) {
            instance = new MqService();
        }
        return instance;
    }

    private Connection getConnection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = factory.newConnection();
        }
        return connection;
    }

    public List<QueueInfo> listQueues() throws IOException, TimeoutException {
        List<QueueInfo> queues = new ArrayList<>();

        try (Connection conn = getConnection();
             Channel channel = conn.createChannel()) {

            // We'll use queue.declare passive mode to check known queues
            // For a more complete solution, you'd need to use RabbitMQ Management API
            String[] knownQueues = {
                    "payment-commands",
                    "account-commands",
                    "fx-commands",
                    "payment-events",
                    "account-events"
            };

            for (String queueName : knownQueues) {
                try {
                    com.rabbitmq.client.AMQP.Queue.DeclareOk ok = channel.queueDeclarePassive(queueName);
                    queues.add(new QueueInfo(
                            queueName,
                            ok.getMessageCount(),
                            ok.getConsumerCount(),
                            "ACTIVE"
                    ));
                } catch (IOException e) {
                    // Queue doesn't exist, skip it
                    logger.debug("Queue {} not found", queueName);
                }
            }
        }

        return queues;
    }

    public QueueInfo getQueueStatus(String queueName) throws IOException, TimeoutException {
        try (Connection conn = getConnection();
             Channel channel = conn.createChannel()) {

            com.rabbitmq.client.AMQP.Queue.DeclareOk ok = channel.queueDeclarePassive(queueName);
            return new QueueInfo(
                    queueName,
                    ok.getMessageCount(),
                    ok.getConsumerCount(),
                    "ACTIVE"
            );
        } catch (IOException e) {
            logger.error("Error getting queue status for {}", queueName, e);
            throw e;
        }
    }

    public void testConnection() throws IOException, TimeoutException {
        try (Connection conn = getConnection();
             Channel channel = conn.createChannel()) {
            logger.info("RabbitMQ connection test successful");
        }
    }

    @Override
    public void close() {
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
                logger.info("RabbitMQ connection closed");
            } catch (IOException e) {
                logger.error("Error closing RabbitMQ connection", e);
            }
        }
    }

    public static class QueueInfo {
        private final String name;
        private final long messageCount;
        private final int consumerCount;
        private final String status;

        public QueueInfo(String name, long messageCount, int consumerCount, String status) {
            this.name = name;
            this.messageCount = messageCount;
            this.consumerCount = consumerCount;
            this.status = status;
        }

        public String getName() {
            return name;
        }

        public long getMessageCount() {
            return messageCount;
        }

        public int getConsumerCount() {
            return consumerCount;
        }

        public String getStatus() {
            return status;
        }

        public boolean isHealthy() {
            return consumerCount > 0;
        }
    }
}
