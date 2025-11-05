package com.acme.platform.cli.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MqServiceTest {

    @Test
    void testGetInstance_returnsSingleton() {
        MqService instance1 = MqService.getInstance();
        MqService instance2 = MqService.getInstance();

        assertThat(instance1).isSameAs(instance2);
    }

    @Test
    void testQueueInfo_construction() {
        MqService.QueueInfo queueInfo = new MqService.QueueInfo(
                "test-queue",
                100L,
                2,
                "ACTIVE"
        );

        assertThat(queueInfo.getName()).isEqualTo("test-queue");
        assertThat(queueInfo.getMessageCount()).isEqualTo(100L);
        assertThat(queueInfo.getConsumerCount()).isEqualTo(2);
        assertThat(queueInfo.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void testQueueInfo_isHealthy_withConsumers() {
        MqService.QueueInfo queueInfo = new MqService.QueueInfo(
                "test-queue",
                100L,
                2,
                "ACTIVE"
        );

        assertThat(queueInfo.isHealthy()).isTrue();
    }

    @Test
    void testQueueInfo_isHealthy_withoutConsumers() {
        MqService.QueueInfo queueInfo = new MqService.QueueInfo(
                "test-queue",
                100L,
                0,
                "ACTIVE"
        );

        assertThat(queueInfo.isHealthy()).isFalse();
    }

    @Test
    void testQueueInfo_withZeroMessages() {
        MqService.QueueInfo queueInfo = new MqService.QueueInfo(
                "empty-queue",
                0L,
                1,
                "ACTIVE"
        );

        assertThat(queueInfo.getName()).isEqualTo("empty-queue");
        assertThat(queueInfo.getMessageCount()).isEqualTo(0L);
        assertThat(queueInfo.isHealthy()).isTrue();
    }

    @Test
    void testQueueInfo_withLargeMessageCount() {
        MqService.QueueInfo queueInfo = new MqService.QueueInfo(
                "busy-queue",
                1000000L,
                5,
                "ACTIVE"
        );

        assertThat(queueInfo.getMessageCount()).isEqualTo(1000000L);
        assertThat(queueInfo.getConsumerCount()).isEqualTo(5);
    }
}
