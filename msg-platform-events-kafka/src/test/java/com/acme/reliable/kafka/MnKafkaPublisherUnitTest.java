package com.acme.reliable.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Unit tests for MnKafkaPublisher */
@DisplayName("MnKafkaPublisher Unit Tests")
class MnKafkaPublisherUnitTest {

  private KafkaProducer<String, String> mockProducer;
  private MnKafkaPublisher publisher;

  @BeforeEach
  void setUp() {
    mockProducer = mock(KafkaProducer.class);
    publisher = new MnKafkaPublisher(mockProducer);
  }

  @Test
  @DisplayName("Should publish message without headers")
  void testPublish_WithoutHeaders() {
    // Given
    String topic = "test-topic";
    String key = "test-key";
    String value = "test-value";

    ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);

    Future mockFuture = mock(Future.class);
    when(mockProducer.send(any(ProducerRecord.class), any(Callback.class))).thenReturn(mockFuture);

    // When
    publisher.publish(topic, key, value, null);

    // Then
    verify(mockProducer).send(recordCaptor.capture(), callbackCaptor.capture());

    ProducerRecord<String, String> capturedRecord = recordCaptor.getValue();
    assertThat(capturedRecord.topic()).isEqualTo(topic);
    assertThat(capturedRecord.key()).isEqualTo(key);
    assertThat(capturedRecord.value()).isEqualTo(value);
    assertThat(capturedRecord.headers()).isEmpty();
  }

  @Test
  @DisplayName("Should publish message with headers")
  void testPublish_WithHeaders() {
    // Given
    String topic = "test-topic";
    String key = "test-key";
    String value = "test-value";
    Map<String, String> headers = new HashMap<>();
    headers.put("header1", "value1");
    headers.put("header2", "value2");

    ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
        ArgumentCaptor.forClass(ProducerRecord.class);

    Future mockFuture = mock(Future.class);
    when(mockProducer.send(any(ProducerRecord.class), any(Callback.class))).thenReturn(mockFuture);

    // When
    publisher.publish(topic, key, value, headers);

    // Then
    verify(mockProducer).send(recordCaptor.capture(), any(Callback.class));

    ProducerRecord<String, String> capturedRecord = recordCaptor.getValue();
    assertThat(capturedRecord.topic()).isEqualTo(topic);
    assertThat(capturedRecord.headers()).hasSize(2);
    assertThat(new String(capturedRecord.headers().lastHeader("header1").value()))
        .isEqualTo("value1");
    assertThat(new String(capturedRecord.headers().lastHeader("header2").value()))
        .isEqualTo("value2");
  }

  @Test
  @DisplayName("Should handle successful publish callback")
  void testPublish_SuccessCallback() {
    // Given
    ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
    Future mockFuture = mock(Future.class);
    when(mockProducer.send(any(ProducerRecord.class), any(Callback.class))).thenReturn(mockFuture);

    RecordMetadata metadata =
        new RecordMetadata(new TopicPartition("test-topic", 0), 0L, 0L, 0L, 0L, 0, 100);

    // When
    publisher.publish("test-topic", "key", "value", null);
    verify(mockProducer).send(any(ProducerRecord.class), callbackCaptor.capture());

    // Simulate successful callback
    Callback callback = callbackCaptor.getValue();
    callback.onCompletion(metadata, null);

    // Then - no exception should be thrown
  }

  @Test
  @DisplayName("Should handle failed publish callback")
  void testPublish_FailureCallback() {
    // Given
    ArgumentCaptor<Callback> callbackCaptor = ArgumentCaptor.forClass(Callback.class);
    Future mockFuture = mock(Future.class);
    when(mockProducer.send(any(ProducerRecord.class), any(Callback.class))).thenReturn(mockFuture);

    Exception publishException = new Exception("Kafka publish failed");

    // When
    publisher.publish("test-topic", "key", "value", null);
    verify(mockProducer).send(any(ProducerRecord.class), callbackCaptor.capture());

    // Simulate failed callback
    Callback callback = callbackCaptor.getValue();

    // Then
    assertThatThrownBy(() -> callback.onCompletion(null, publishException))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to publish to Kafka topic");
  }

  @Test
  @DisplayName("Should publish with empty headers map")
  void testPublish_EmptyHeadersMap() {
    // Given
    Map<String, String> emptyHeaders = new HashMap<>();
    ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
        ArgumentCaptor.forClass(ProducerRecord.class);

    Future mockFuture = mock(Future.class);
    when(mockProducer.send(any(ProducerRecord.class), any(Callback.class))).thenReturn(mockFuture);

    // When
    publisher.publish("topic", "key", "value", emptyHeaders);

    // Then
    verify(mockProducer).send(recordCaptor.capture(), any(Callback.class));
    ProducerRecord<String, String> capturedRecord = recordCaptor.getValue();
    assertThat(capturedRecord.headers()).isEmpty();
  }

  @Test
  @DisplayName("Should handle multiple headers")
  void testPublish_MultipleHeaders() {
    // Given
    Map<String, String> headers = new HashMap<>();
    headers.put("correlation-id", "123");
    headers.put("trace-id", "456");
    headers.put("span-id", "789");

    ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
        ArgumentCaptor.forClass(ProducerRecord.class);

    Future mockFuture = mock(Future.class);
    when(mockProducer.send(any(ProducerRecord.class), any(Callback.class))).thenReturn(mockFuture);

    // When
    publisher.publish("topic", "key", "value", headers);

    // Then
    verify(mockProducer).send(recordCaptor.capture(), any(Callback.class));
    ProducerRecord<String, String> capturedRecord = recordCaptor.getValue();
    assertThat(capturedRecord.headers()).hasSize(3);
  }

  @Test
  @DisplayName("Should create publisher with producer")
  void testConstructor() {
    // Given/When
    KafkaProducer<String, String> producer = mock(KafkaProducer.class);
    MnKafkaPublisher pub = new MnKafkaPublisher(producer);

    // Then
    assertThat(pub).isNotNull();
  }

  @Test
  @DisplayName("Should handle null key")
  void testPublish_NullKey() {
    // Given
    ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
        ArgumentCaptor.forClass(ProducerRecord.class);

    Future mockFuture = mock(Future.class);
    when(mockProducer.send(any(ProducerRecord.class), any(Callback.class))).thenReturn(mockFuture);

    // When
    publisher.publish("topic", null, "value", null);

    // Then
    verify(mockProducer).send(recordCaptor.capture(), any(Callback.class));
    ProducerRecord<String, String> capturedRecord = recordCaptor.getValue();
    assertThat(capturedRecord.key()).isNull();
  }
}
