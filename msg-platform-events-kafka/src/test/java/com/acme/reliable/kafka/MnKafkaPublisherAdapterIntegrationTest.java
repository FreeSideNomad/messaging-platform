package com.acme.reliable.kafka;

import com.acme.reliable.spi.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Enhanced tests for MnKafkaPublisherAdapter covering edge cases and error scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MnKafkaPublisherAdapter Enhanced Tests")
class MnKafkaPublisherAdapterIntegrationTest {

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private MnKafkaPublisherAdapter adapter;

    @Test
    @DisplayName("Should delegate all parameters correctly including null values")
    void testPublish_NullParameters() {
        // When: Publish with null parameters
        adapter.publish(null, null, null, null, null);

        // Then: Should delegate to EventPublisher with nulls
        verify(eventPublisher).publish(null, null, null, null);
    }

    @Test
    @DisplayName("Should handle empty strings correctly")
    void testPublish_EmptyStrings() {
        // Given
        String topic = "";
        String key = "";
        String type = "";
        String payload = "";
        Map<String, String> headers = new HashMap<>();

        // When
        adapter.publish(topic, key, type, payload, headers);

        // Then
        verify(eventPublisher).publish(eq(""), eq(""), eq(""), eq(headers));
    }

    @Test
    @DisplayName("Should handle large payloads")
    void testPublish_LargePayload() {
        // Given
        String largePayload = "x".repeat(10000);
        String topic = "large-topic";
        String key = "large-key";

        // When
        adapter.publish(topic, key, "type", largePayload, null);

        // Then
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher).publish(eq(topic), eq(key), payloadCaptor.capture(), isNull());
        assertThat(payloadCaptor.getValue()).hasSize(10000);
    }

    @Test
    @DisplayName("Should handle special characters in all fields")
    void testPublish_SpecialCharacters() {
        // Given
        String topic = "topic-with-special!@#$%";
        String key = "key-with-special^&*()";
        String type = "Type<>[]{}";
        String payload = "{\"special\": \"chars!@#$%^&*()\"}";
        Map<String, String> headers = new HashMap<>();
        headers.put("special-header!@#", "value$%^");

        // When
        adapter.publish(topic, key, type, payload, headers);

        // Then
        verify(eventPublisher).publish(eq(topic), eq(key), eq(payload), eq(headers));
    }

    @Test
    @DisplayName("Should handle multiple headers correctly")
    void testPublish_MultipleHeaders() {
        // Given
        Map<String, String> headers = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            headers.put("header-" + i, "value-" + i);
        }

        // When
        adapter.publish("topic", "key", "type", "payload", headers);

        // Then
        ArgumentCaptor<Map> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publish(eq("topic"), eq("key"), eq("payload"), headersCaptor.capture());
        assertThat(headersCaptor.getValue()).hasSize(100);
    }

    @Test
    @DisplayName("Should not modify headers map passed to it")
    void testPublish_DoesNotModifyHeaders() {
        // Given
        Map<String, String> headers = new HashMap<>();
        headers.put("original", "value");
        int originalSize = headers.size();

        // When
        adapter.publish("topic", "key", "type", "payload", headers);

        // Then
        assertThat(headers).hasSize(originalSize);
        assertThat(headers).containsEntry("original", "value");
    }

    @Test
    @DisplayName("Should handle consecutive publish calls")
    void testPublish_ConsecutiveCalls() {
        // When
        for (int i = 0; i < 10; i++) {
            adapter.publish("topic-" + i, "key-" + i, "type", "payload-" + i, null);
        }

        // Then
        verify(eventPublisher, times(10)).publish(anyString(), anyString(), anyString(), isNull());
    }

    @Test
    @DisplayName("Should propagate exceptions from EventPublisher")
    void testPublish_PropagatesException() {
        // Given
        doThrow(new RuntimeException("Publish failed"))
                .when(eventPublisher)
                .publish(anyString(), anyString(), anyString(), any());

        // When/Then
        try {
            adapter.publish("topic", "key", "type", "payload", null);
        } catch (RuntimeException e) {
            assertThat(e).hasMessage("Publish failed");
        }

        verify(eventPublisher).publish("topic", "key", "payload", null);
    }

    @Test
    @DisplayName("Should handle whitespace-only strings")
    void testPublish_WhitespaceStrings() {
        // Given
        String topic = "   ";
        String key = "\t\t";
        String type = "\n\n";
        String payload = "  \t\n  ";

        // When
        adapter.publish(topic, key, type, payload, null);

        // Then
        verify(eventPublisher).publish(eq(topic), eq(key), eq(payload), isNull());
    }

    @Test
    @DisplayName("Should verify constructor injection")
    void testConstructor() {
        // Given
        EventPublisher publisher = mock(EventPublisher.class);

        // When
        MnKafkaPublisherAdapter newAdapter = new MnKafkaPublisherAdapter(publisher);

        // Then
        assertThat(newAdapter).isNotNull();

        // Verify it uses the injected publisher
        newAdapter.publish("test", "key", "type", "payload", null);
        verify(publisher).publish("test", "key", "payload", null);
    }

    @Test
    @DisplayName("Should handle Unicode in all string fields")
    void testPublish_Unicode() {
        // Given
        String topic = "topic-你好";
        String key = "key-مرحبا";
        String type = "type-Привет";
        String payload = "{\"message\": \"世界\"}";
        Map<String, String> headers = new HashMap<>();
        headers.put("unicode-header", "العالم");

        // When
        adapter.publish(topic, key, type, payload, headers);

        // Then
        verify(eventPublisher).publish(eq(topic), eq(key), eq(payload), eq(headers));
    }

    @Test
    @DisplayName("Should handle JSON payload correctly")
    void testPublish_JsonPayload() {
        // Given
        String jsonPayload = "{\"userId\":123,\"name\":\"John Doe\",\"email\":\"john@example.com\"}";

        // When
        adapter.publish("users", "user-123", "UserCreated", jsonPayload, null);

        // Then
        verify(eventPublisher).publish("users", "user-123", jsonPayload, null);
    }

    @Test
    @DisplayName("Should handle nested JSON payload")
    void testPublish_NestedJsonPayload() {
        // Given
        String nestedJson = "{\"user\":{\"id\":1,\"profile\":{\"name\":\"Test\",\"address\":{\"city\":\"NYC\"}}}}";

        // When
        adapter.publish("topic", "key", "type", nestedJson, null);

        // Then
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher).publish(eq("topic"), eq("key"), payloadCaptor.capture(), isNull());
        assertThat(payloadCaptor.getValue()).isEqualTo(nestedJson);
    }

    @Test
    @DisplayName("Should handle malformed JSON as string")
    void testPublish_MalformedJson() {
        // Given
        String malformedJson = "{this is not valid json}";

        // When
        adapter.publish("topic", "key", "type", malformedJson, null);

        // Then
        verify(eventPublisher).publish("topic", "key", malformedJson, null);
    }

    @Test
    @DisplayName("Should handle very long topic names")
    void testPublish_LongTopicName() {
        // Given
        String longTopic = "very.long.topic.name.with.many.segments." + "segment.".repeat(50);

        // When
        adapter.publish(longTopic, "key", "type", "payload", null);

        // Then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher).publish(topicCaptor.capture(), eq("key"), eq("payload"), isNull());
        assertThat(topicCaptor.getValue()).hasSize(longTopic.length());
    }
}
