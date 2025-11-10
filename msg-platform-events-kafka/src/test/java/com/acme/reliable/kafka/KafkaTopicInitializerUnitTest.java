package com.acme.reliable.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for KafkaTopicInitializer helper methods */
@DisplayName("KafkaTopicInitializer Unit Tests")
class KafkaTopicInitializerUnitTest {

  @Test
  @DisplayName("Should initialize with valid parameters")
  void testConstructor_ValidParameters() {
    // Given/When
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer("localhost:9092", "test.topic1,test.topic2", 3, 1);

    // Then
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle null topics")
  void testConstructor_NullTopics() {
    // Given/When
    KafkaTopicInitializer initializer = new KafkaTopicInitializer("localhost:9092", null, 3, 1);

    // Then - should not throw
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle empty topics string")
  void testConstructor_EmptyTopics() {
    // Given/When
    KafkaTopicInitializer initializer = new KafkaTopicInitializer("localhost:9092", "", 3, 1);

    // Then - should not throw
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle blank topics string")
  void testConstructor_BlankTopics() {
    // Given/When
    KafkaTopicInitializer initializer = new KafkaTopicInitializer("localhost:9092", "   ", 3, 1);

    // Then - should not throw
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle topics with whitespace")
  void testConstructor_TopicsWithWhitespace() {
    // Given/When
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer("localhost:9092", " topic1 , topic2 , topic3 ", 3, 1);

    // Then - should not throw
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle null bootstrap servers")
  void testConstructor_NullBootstrapServers() {
    // Given/When
    KafkaTopicInitializer initializer = new KafkaTopicInitializer(null, "test.topic", 3, 1);

    // Then - should not throw (will use default)
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle empty bootstrap servers")
  void testConstructor_EmptyBootstrapServers() {
    // Given/When
    KafkaTopicInitializer initializer = new KafkaTopicInitializer("", "test.topic", 3, 1);

    // Then - should not throw (will use default)
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle custom partitions")
  void testConstructor_CustomPartitions() {
    // Given/When
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer("localhost:9092", "test.topic", 10, 1);

    // Then - should not throw
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle custom replication factor")
  void testConstructor_CustomReplicationFactor() {
    // Given/When
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer("localhost:9092", "test.topic", 3, 3);

    // Then - should not throw
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle comma-separated bootstrap servers")
  void testConstructor_MultipleBootstrapServers() {
    // Given/When
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer(
            "localhost:9092,localhost:9093,localhost:9094", "test.topic", 3, 1);

    // Then - should not throw
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle bootstrap servers with empty entries")
  void testConstructor_BootstrapServersWithEmptyEntries() {
    // Given/When - this tests the filter branch in normalizeBootstrapServers
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer("localhost:9092,,localhost:9093", "test.topic", 3, 1);

    // Then - should not throw
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle bootstrap servers without port")
  void testConstructor_BootstrapServersWithoutPort() {
    // Given/When - this tests the ternary operator branch in normalizeBootstrapServers
    KafkaTopicInitializer initializer =
        new KafkaTopicInitializer("9092,9093", "test.topic", 3, 1);

    // Then - should not throw
    assertThat(initializer).isNotNull();
  }

  @Test
  @DisplayName("Should handle blank bootstrap servers")
  void testConstructor_BlankBootstrapServers() {
    // Given/When - tests the isBlank branch in constructor
    KafkaTopicInitializer initializer = new KafkaTopicInitializer("   ", "test.topic", 3, 1);

    // Then - should not throw (will use default)
    assertThat(initializer).isNotNull();
  }
}
