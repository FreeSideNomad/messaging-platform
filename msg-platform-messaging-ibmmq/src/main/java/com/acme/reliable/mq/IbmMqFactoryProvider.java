package com.acme.reliable.mq;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSConnectionFactory;

import static com.ibm.msg.client.jakarta.jms.JmsConstants.*;
import static com.ibm.msg.client.jakarta.wmq.common.CommonConstants.*;

@Requires(notEnv = "test")
@Factory
public class IbmMqFactoryProvider {

    @JMSConnectionFactory("mqConnectionFactory")
    public jakarta.jms.ConnectionFactory mqConnectionFactory() throws Exception {
        var cf = new com.ibm.mq.jakarta.jms.MQConnectionFactory();
        cf.setTransportType(WMQ_CM_CLIENT);
        cf.setHostName(System.getenv().getOrDefault("MQ_HOST", "localhost"));
        cf.setPort(Integer.parseInt(System.getenv().getOrDefault("MQ_PORT", "1414")));
        cf.setQueueManager(System.getenv().getOrDefault("MQ_QMGR", "QM1"));
        cf.setChannel(System.getenv().getOrDefault("MQ_CHANNEL", "DEV.APP.SVRCONN"));
        cf.setAppName("reliable-cmd");
        cf.setBooleanProperty(USER_AUTHENTICATION_MQCSP, true);
        cf.setStringProperty(USERID, System.getenv().getOrDefault("MQ_USER", "app"));
        cf.setStringProperty(PASSWORD, System.getenv().getOrDefault("MQ_PASS", "passw0rd"));

        // Performance Optimizations for High Throughput (500 TPS / 1500 msg/sec)

        // 1. Client Reconnect: Enable automatic reconnection on connection failures
        // Improves resilience and availability under network issues
        cf.setIntProperty(WMQ_CLIENT_RECONNECT_OPTIONS, WMQ_CLIENT_RECONNECT);

        // 2. Share Conversations: Enable conversation sharing for better connection reuse
        // Allows multiple threads to share a single TCP/IP conversation
        // This reduces connection overhead significantly by allowing thread pooling
        cf.setIntProperty(WMQ_SHARE_CONV_ALLOWED, WMQ_SHARE_CONV_ALLOWED_YES);

        // Note: The PRIMARY performance optimization is session pooling in JmsCommandQueue.java
        // Session pooling eliminates the overhead of creating/destroying sessions and producers per
        // message
        // This was the main bottleneck - each message was creating a new session, producer, and
        // transaction
        // Expected improvement: 10-20x throughput increase from session reuse (from ~50 TPS to 500+
        // TPS)

        return cf;
    }
}
