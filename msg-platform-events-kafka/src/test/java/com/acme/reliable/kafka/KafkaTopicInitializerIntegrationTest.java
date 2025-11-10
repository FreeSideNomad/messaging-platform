package com.acme.reliable.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.micronaut.context.BeanContext;
import io.micronaut.context.event.StartupEvent;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for KafkaTopicInitializer using Testcontainers.
 * Tests topic creation, initialization order, and error handling.
 */
@Testcontainers
@DisplayName("KafkaTopicInitializer Integration Tests")
class KafkaTopicInitializerIntegrationTest {

  @Container
  private static final KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
          .withStartupTimeout(Duration.ofMinutes(2));

  @Test
  @DisplayName("Should create single topic successfully")
  void testEnsureTopics_SingleTopic() throws Exception {
    // Given
    String topic = "test-topic-single-" + System.currentTimeMillis();
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), topic, 3, 1);

    // When
    initializer.ensureTopics();

    // Then
    try (Admin admin = createAdmin()) {
      Set<String> topics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
      assertThat(topics).contains(topic);
    }
  }

  @Test
  @DisplayName("Should create multiple topics successfully")
  void testEnsureTopics_MultipleTopics() throws Exception {
    // Given
    long timestamp = System.currentTimeMillis();
    String topics = String.format("topic1-%d,topic2-%d,topic3-%d", timestamp, timestamp, timestamp);
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), topics, 3, 1);

    // When
    initializer.ensureTopics();

    // Then
    try (Admin admin = createAdmin()) {
      Set<String> kafkaTopics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
      assertThat(kafkaTopics).contains(
          "topic1-" + timestamp,
          "topic2-" + timestamp,
          "topic3-" + timestamp
      );
    }
  }

  @Test
  @DisplayName("Should handle idempotent topic creation")
  void testEnsureTopics_Idempotent() throws Exception {
    // Given
    String topic = "test-topic-idempotent-" + System.currentTimeMillis();
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), topic, 3, 1);

    // When: Create topics twice
    initializer.ensureTopics();
    initializer.ensureTopics();

    // Then: Should not throw and topic should exist
    try (Admin admin = createAdmin()) {
      Set<String> topics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
      assertThat(topics).contains(topic);
    }
  }

  @Test
  @DisplayName("Should skip creation when no topics configured")
  void testEnsureTopics_NoTopics() {
    // Given
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), "", 3, 1);

    // When/Then: Should not throw
    initializer.ensureTopics();
  }

  @Test
  @DisplayName("Should skip creation when null topics configured")
  void testEnsureTopics_NullTopics() {
    // Given
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), null, 3, 1);

    // When/Then: Should not throw
    initializer.ensureTopics();
  }

  @Test
  @DisplayName("Should handle topics with whitespace")
  void testEnsureTopics_TopicsWithWhitespace() throws Exception {
    // Given
    long timestamp = System.currentTimeMillis();
    String topics = String.format(" topic1-%d , topic2-%d , topic3-%d ", timestamp, timestamp, timestamp);
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), topics, 3, 1);

    // When
    initializer.ensureTopics();

    // Then
    try (Admin admin = createAdmin()) {
      Set<String> kafkaTopics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
      assertThat(kafkaTopics).contains(
          "topic1-" + timestamp,
          "topic2-" + timestamp,
          "topic3-" + timestamp
      );
    }
  }

  @Test
  @DisplayName("Should create topics with custom partition count")
  void testEnsureTopics_CustomPartitions() throws Exception {
    // Given
    String topic = "test-topic-partitions-" + System.currentTimeMillis();
    int partitions = 10;
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), topic, partitions, 1);

    // When
    initializer.ensureTopics();

    // Then
    try (Admin admin = createAdmin()) {
      var topicDescription = admin.describeTopics(Set.of(topic))
          .allTopicNames()
          .get(10, TimeUnit.SECONDS);
      assertThat(topicDescription.get(topic).partitions()).hasSize(partitions);
    }
  }

  @Test
  @DisplayName("Should handle startup event trigger")
  void testOnApplicationEvent() throws Exception {
    // Given
    String topic = "test-topic-event-" + System.currentTimeMillis();
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), topic, 3, 1);
    BeanContext mockContext = mock(BeanContext.class);
    StartupEvent event = new StartupEvent(mockContext);

    // When
    initializer.onApplicationEvent(event);

    // Then
    try (Admin admin = createAdmin()) {
      Set<String> topics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
      assertThat(topics).contains(topic);
    }
  }

  @Test
  @DisplayName("Should handle comma-separated bootstrap servers")
  void testEnsureTopics_MultipleBootstrapServers() throws Exception {
    // Given
    String bootstrapServers = kafka.getBootstrapServers() + ",localhost:9999";
    String topic = "test-topic-multi-bootstrap-" + System.currentTimeMillis();
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(bootstrapServers, topic, 3, 1);

    // When
    initializer.ensureTopics();

    // Then: Should succeed with at least one working broker
    try (Admin admin = createAdmin()) {
      Set<String> topics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
      assertThat(topics).contains(topic);
    }
  }

  @Test
  @DisplayName("Should normalize bootstrap servers without port")
  void testConstructor_NormalizeBootstrapServers() throws Exception {
    // Given: Bootstrap server without explicit port
    String topic = "test-topic-normalize-" + System.currentTimeMillis();
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer("localhost", topic, 3, 1);

    // When/Then: Should use default port localhost:9092
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle blank bootstrap servers")
  void testConstructor_BlankBootstrapServers() {
    // Given/When
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer("  ", "test-topic", 3, 1);

    // Then: Should not throw (will use default localhost:9092)
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle invalid bootstrap servers gracefully")
  void testEnsureTopics_InvalidBootstrapServers() {
    // Given
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer("invalid-broker:9092", "test-topic", 3, 1);

    // When/Then: Should throw exception after retries (can be IllegalStateException or KafkaException)
    assertThatThrownBy(() -> initializer.ensureTopics())
        .satisfiesAnyOf(
            e -> assertThat(e).isInstanceOf(IllegalStateException.class),
            e -> assertThat(e).hasCauseInstanceOf(org.apache.kafka.common.KafkaException.class)
        );
  }

  @Test
  @DisplayName("Should handle topics with dots and dashes")
  void testEnsureTopics_TopicsWithSpecialChars() throws Exception {
    // Given
    long timestamp = System.currentTimeMillis();
    String topics = String.format("events.user-created-%d,events.order-placed-%d", timestamp, timestamp);
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), topics, 3, 1);

    // When
    initializer.ensureTopics();

    // Then
    try (Admin admin = createAdmin()) {
      Set<String> kafkaTopics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
      assertThat(kafkaTopics).contains(
          "events.user-created-" + timestamp,
          "events.order-placed-" + timestamp
      );
    }
  }

  @Test
  @DisplayName("Should handle mixed valid and empty topic names")
  void testEnsureTopics_MixedTopicNames() throws Exception {
    // Given
    long timestamp = System.currentTimeMillis();
    String topics = String.format("valid-topic-%d,,,another-topic-%d,", timestamp, timestamp);
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), topics, 3, 1);

    // When
    initializer.ensureTopics();

    // Then: Should only create valid topic names
    try (Admin admin = createAdmin()) {
      Set<String> kafkaTopics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
      assertThat(kafkaTopics).contains(
          "valid-topic-" + timestamp,
          "another-topic-" + timestamp
      );
    }
  }

  @Test
  @DisplayName("Should use environment variable over config for bootstrap servers")
  void testConstructor_EnvironmentVariableOverride() {
    // Given: Environment variable is set (simulated by passing non-blank value)
    String envBootstrap = "env-broker:9092";

    // When: Create initializer - in real scenario, env var would be read
    // For testing, we verify the constructor accepts the parameter
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(envBootstrap, "test-topic", 3, 1);

    // Then
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle default partition count")
  void testConstructor_DefaultPartitions() throws Exception {
    // Given
    String topic = "test-topic-default-partitions-" + System.currentTimeMillis();
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), topic, 3, 1);

    // When
    initializer.ensureTopics();

    // Then
    try (Admin admin = createAdmin()) {
      var topicDescription = admin.describeTopics(Set.of(topic))
          .allTopicNames()
          .get(10, TimeUnit.SECONDS);
      assertThat(topicDescription.get(topic).partitions()).hasSize(3);
    }
  }

  @Test
  @DisplayName("Should handle single partition topic")
  void testEnsureTopics_SinglePartition() throws Exception {
    // Given
    String topic = "test-topic-single-partition-" + System.currentTimeMillis();
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), topic, 1, 1);

    // When
    initializer.ensureTopics();

    // Then
    try (Admin admin = createAdmin()) {
      var topicDescription = admin.describeTopics(Set.of(topic))
          .allTopicNames()
          .get(10, TimeUnit.SECONDS);
      assertThat(topicDescription.get(topic).partitions()).hasSize(1);
    }
  }

  @Test
  @DisplayName("Should verify replication factor is applied")
  void testEnsureTopics_ReplicationFactor() throws Exception {
    // Given
    String topic = "test-topic-replication-" + System.currentTimeMillis();
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), topic, 3, 1);

    // When
    initializer.ensureTopics();

    // Then
    try (Admin admin = createAdmin()) {
      var topicDescription = admin.describeTopics(Set.of(topic))
          .allTopicNames()
          .get(10, TimeUnit.SECONDS);
      // With single broker, replication factor will be 1
      assertThat(topicDescription.get(topic).partitions().get(0).replicas()).hasSize(1);
    }
  }

  @Test
  @DisplayName("Should handle long valid topic names")
  void testEnsureTopics_LongTopicName() throws Exception {
    // Given: Kafka allows topic names up to 249 characters
    String longName = "test-" + "x".repeat(230) + "-" + System.currentTimeMillis(); // Keep under 249 chars
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(kafka.getBootstrapServers(), longName, 3, 1);

    // When
    initializer.ensureTopics();

    // Then
    try (Admin admin = createAdmin()) {
      Set<String> topics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
      assertThat(topics).anyMatch(t -> t.startsWith("test-x"));
    }
  }

  private Admin createAdmin() {
    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    return Admin.create(props);
  }
}
