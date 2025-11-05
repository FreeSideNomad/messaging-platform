package com.acme.platform.cli.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaServiceTest {

    @Test
    void testGetInstance_returnsSingleton() {
        KafkaService instance1 = KafkaService.getInstance();
        KafkaService instance2 = KafkaService.getInstance();

        assertThat(instance1).isSameAs(instance2);
    }

    @Test
    void testTopicInfo_construction() {
        KafkaService.TopicInfo topicInfo = new KafkaService.TopicInfo(
                "test-topic",
                3,
                2,
                1000L
        );

        assertThat(topicInfo.getName()).isEqualTo("test-topic");
        assertThat(topicInfo.getPartitions()).isEqualTo(3);
        assertThat(topicInfo.getReplicationFactor()).isEqualTo(2);
        assertThat(topicInfo.getEventCount()).isEqualTo(1000L);
    }

    @Test
    void testTopicInfo_withZeroEvents() {
        KafkaService.TopicInfo topicInfo = new KafkaService.TopicInfo(
                "empty-topic",
                1,
                1,
                0L
        );

        assertThat(topicInfo.getName()).isEqualTo("empty-topic");
        assertThat(topicInfo.getEventCount()).isEqualTo(0L);
    }

    @Test
    void testTopicInfo_withNegativeEventCount() {
        KafkaService.TopicInfo topicInfo = new KafkaService.TopicInfo(
                "error-topic",
                1,
                1,
                -1L
        );

        assertThat(topicInfo.getEventCount()).isEqualTo(-1L);
    }

    @Test
    void testTopicInfo_withMultiplePartitions() {
        KafkaService.TopicInfo topicInfo = new KafkaService.TopicInfo(
                "multi-partition-topic",
                10,
                3,
                50000L
        );

        assertThat(topicInfo.getPartitions()).isEqualTo(10);
        assertThat(topicInfo.getReplicationFactor()).isEqualTo(3);
        assertThat(topicInfo.getEventCount()).isEqualTo(50000L);
    }

    @Test
    void testConsumerGroupLag_construction() {
        KafkaService.ConsumerGroupLag lag = new KafkaService.ConsumerGroupLag(
                "test-topic",
                0,
                100L,
                150L,
                50L
        );

        assertThat(lag.getTopic()).isEqualTo("test-topic");
        assertThat(lag.getPartition()).isEqualTo(0);
        assertThat(lag.getCurrentOffset()).isEqualTo(100L);
        assertThat(lag.getEndOffset()).isEqualTo(150L);
        assertThat(lag.getLag()).isEqualTo(50L);
    }

    @Test
    void testConsumerGroupLag_withZeroLag() {
        KafkaService.ConsumerGroupLag lag = new KafkaService.ConsumerGroupLag(
                "test-topic",
                0,
                100L,
                100L,
                0L
        );

        assertThat(lag.getLag()).isEqualTo(0L);
    }

    @Test
    void testConsumerGroupLag_withLargeLag() {
        KafkaService.ConsumerGroupLag lag = new KafkaService.ConsumerGroupLag(
                "slow-topic",
                0,
                1000L,
                10000L,
                9000L
        );

        assertThat(lag.getLag()).isEqualTo(9000L);
        assertThat(lag.getCurrentOffset()).isLessThan(lag.getEndOffset());
    }

    @Test
    void testConsumerGroupLag_multiplePartitions() {
        KafkaService.ConsumerGroupLag lag1 = new KafkaService.ConsumerGroupLag(
                "test-topic",
                0,
                100L,
                150L,
                50L
        );

        KafkaService.ConsumerGroupLag lag2 = new KafkaService.ConsumerGroupLag(
                "test-topic",
                1,
                200L,
                250L,
                50L
        );

        assertThat(lag1.getPartition()).isEqualTo(0);
        assertThat(lag2.getPartition()).isEqualTo(1);
        assertThat(lag1.getTopic()).isEqualTo(lag2.getTopic());
    }
}
