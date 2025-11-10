package com.acme.reliable.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MnKafkaPublisher using Testcontainers.
 * Tests real Kafka interactions including publish operations and error handling.
 */
@Testcontainers
@DisplayName("MnKafkaPublisher Integration Tests")
class MnKafkaPublisherIntegrationTest {

    @Container
    private static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private MnKafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        producer = new KafkaProducer<>(producerProps);
        publisher = new MnKafkaPublisher(producer);

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(consumerProps);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        if (producer != null) {
            producer.close();
        }
    }

    @Test
    @DisplayName("Should successfully publish message to Kafka")
    void testPublish_SuccessfulDelivery() {
        // Given
        String topic = "test-topic-" + UUID.randomUUID();
        String key = "test-key";
        String value = "test-value";
        consumer.subscribe(Collections.singletonList(topic));

        // When
        publisher.publish(topic, key, value, null);
        producer.flush(); // Ensure message is sent

        // Then
        // Poll until we get records (wait up to 10 seconds)
        var records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records).hasSizeGreaterThanOrEqualTo(1);

        ConsumerRecord<String, String> consumerRecord = records.iterator().next();
        assertThat(consumerRecord.key()).isEqualTo(key);
        assertThat(consumerRecord.value()).isEqualTo(value);
    }

    @Test
    @DisplayName("Should publish message with headers")
    void testPublish_WithHeaders() {
        // Given
        String topic = "test-topic-headers-" + UUID.randomUUID();
        String key = "test-key";
        String value = "test-value";
        Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", "corr-123");
        headers.put("trace-id", "trace-456");

        consumer.subscribe(Collections.singletonList(topic));

        // When
        publisher.publish(topic, key, value, headers);
        producer.flush();

        // Then
        var records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records).hasSizeGreaterThanOrEqualTo(1);

        ConsumerRecord<String, String> consumerRecord = records.iterator().next();
        assertThat(consumerRecord.headers()).isNotNull();

        Header correlationHeader = consumerRecord.headers().lastHeader("correlation-id");
        assertThat(correlationHeader).isNotNull();
        assertThat(new String(correlationHeader.value())).isEqualTo("corr-123");

        Header traceHeader = consumerRecord.headers().lastHeader("trace-id");
        assertThat(traceHeader).isNotNull();
        assertThat(new String(traceHeader.value())).isEqualTo("trace-456");
    }

    @Test
    @DisplayName("Should publish multiple messages to same topic")
    void testPublish_MultipleMessages() {
        // Given
        String topic = "test-topic-multi-" + UUID.randomUUID();
        consumer.subscribe(Collections.singletonList(topic));

        // When
        for (int i = 0; i < 10; i++) {
            publisher.publish(topic, "key-" + i, "value-" + i, null);
        }
        producer.flush();

        // Then
        // Poll multiple times to collect all 10 messages
        int totalRecords = 0;
        for (int i = 0; i < 5 && totalRecords < 10; i++) {
            var polledRecords = consumer.poll(Duration.ofSeconds(2));
            totalRecords += polledRecords.count();
        }
        assertThat(totalRecords).isEqualTo(10);
    }

    @Test
    @DisplayName("Should publish messages with null keys")
    void testPublish_NullKey() {
        // Given
        String topic = "test-topic-null-key-" + UUID.randomUUID();
        String value = "test-value-without-key";
        consumer.subscribe(Collections.singletonList(topic));

        // When
        publisher.publish(topic, null, value, null);
        producer.flush();

        // Then
        var records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records).hasSizeGreaterThanOrEqualTo(1);

        ConsumerRecord<String, String> consumerRecord = records.iterator().next();
        assertThat(consumerRecord.key()).isNull();
        assertThat(consumerRecord.value()).isEqualTo(value);
    }

    @Test
    @DisplayName("Should publish large message payloads")
    void testPublish_LargePayload() {
        // Given
        String topic = "test-topic-large-" + UUID.randomUUID();
        String key = "large-key";
        // Create a large value (100KB)
        String largeValue = "X".repeat(100 * 1024);
        consumer.subscribe(Collections.singletonList(topic));

        // When
        publisher.publish(topic, key, largeValue, null);
        producer.flush();

        // Then
        var records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records).hasSizeGreaterThanOrEqualTo(1);

        ConsumerRecord<String, String> consumerRecord = records.iterator().next();
        assertThat(consumerRecord.value()).hasSize(100 * 1024);
    }

    @Test
    @DisplayName("Should handle empty string values")
    void testPublish_EmptyValue() {
        // Given
        String topic = "test-topic-empty-" + UUID.randomUUID();
        String key = "test-key";
        String value = "";
        consumer.subscribe(Collections.singletonList(topic));

        // When
        publisher.publish(topic, key, value, null);
        producer.flush();

        // Then
        var records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records).hasSizeGreaterThanOrEqualTo(1);

        ConsumerRecord<String, String> consumerRecord = records.iterator().next();
        assertThat(consumerRecord.value()).isEmpty();
    }

    @Test
    @DisplayName("Should publish to different topics")
    void testPublish_DifferentTopics() {
        // Given
        String topic1 = "test-topic-1-" + UUID.randomUUID();
        String topic2 = "test-topic-2-" + UUID.randomUUID();

        KafkaConsumer<String, String> consumer1 = createConsumer();
        KafkaConsumer<String, String> consumer2 = createConsumer();
        consumer1.subscribe(Collections.singletonList(topic1));
        consumer2.subscribe(Collections.singletonList(topic2));

        try {
            // When
            publisher.publish(topic1, "key1", "value1", null);
            publisher.publish(topic2, "key2", "value2", null);
            producer.flush();

            // Then
            var records1 = consumer1.poll(Duration.ofSeconds(10));
            var records2 = consumer2.poll(Duration.ofSeconds(10));

            assertThat(records1).hasSizeGreaterThanOrEqualTo(1);
            assertThat(records2).hasSizeGreaterThanOrEqualTo(1);

            assertThat(records1.iterator().next().value()).isEqualTo("value1");
            assertThat(records2.iterator().next().value()).isEqualTo("value2");
        } finally {
            consumer1.close();
            consumer2.close();
        }
    }

    @Test
    @DisplayName("Should handle special characters in values")
    void testPublish_SpecialCharacters() {
        // Given
        String topic = "test-topic-special-" + UUID.randomUUID();
        String key = "special-key";
        String value = "Special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?~`\n\t\r";
        consumer.subscribe(Collections.singletonList(topic));

        // When
        publisher.publish(topic, key, value, null);
        producer.flush();

        // Then
        var records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records).hasSizeGreaterThanOrEqualTo(1);

        ConsumerRecord<String, String> consumerRecord = records.iterator().next();
        assertThat(consumerRecord.value()).isEqualTo(value);
    }

    @Test
    @DisplayName("Should handle Unicode characters")
    void testPublish_UnicodeCharacters() {
        // Given
        String topic = "test-topic-unicode-" + UUID.randomUUID();
        String key = "unicode-key";
        String value = "Unicode: 你好世界 مرحبا العالم Привет мир";
        consumer.subscribe(Collections.singletonList(topic));

        // When
        publisher.publish(topic, key, value, null);
        producer.flush();

        // Then
        var records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records).hasSizeGreaterThanOrEqualTo(1);

        ConsumerRecord<String, String> consumerRecord = records.iterator().next();
        assertThat(consumerRecord.value()).isEqualTo(value);
    }

    private KafkaConsumer<String, String> createConsumer() {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(consumerProps);
    }
}
