package com.acme.reliable.kafka;

import com.acme.reliable.spi.EventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("MnKafkaPublisherAdapter Tests")
class MnKafkaPublisherAdapterTest {

    @Test
    @DisplayName("should delegate publish call to EventPublisher")
    void testPublishDelegation() {
        // Given: Mock EventPublisher
        EventPublisher mockPublisher = mock(EventPublisher.class);
        MnKafkaPublisherAdapter adapter = new MnKafkaPublisherAdapter(mockPublisher);

        // When: Call publish with all parameters
        String topic = "test-topic";
        String key = "test-key";
        String type = "UserCreated";
        String payload = "{\"userId\": 123}";
        Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", "corr-123");

        adapter.publish(topic, key, type, payload, headers);

        // Then: EventPublisher.publish should be called with correct parameters (type is ignored)
        verify(mockPublisher, times(1))
                .publish(topic, key, payload, headers);
    }

    @Test
    @DisplayName("should handle publish with null headers")
    void testPublishWithNullHeaders() {
        // Given: Mock EventPublisher
        EventPublisher mockPublisher = mock(EventPublisher.class);
        MnKafkaPublisherAdapter adapter = new MnKafkaPublisherAdapter(mockPublisher);

        // When: Call publish with null headers
        adapter.publish("test-topic", "test-key", "UserCreated", "{}", null);

        // Then: EventPublisher should be called with null headers
        verify(mockPublisher).publish("test-topic", "test-key", "{}", null);
    }

    @Test
    @DisplayName("should handle publish with empty headers")
    void testPublishWithEmptyHeaders() {
        // Given: Mock EventPublisher
        EventPublisher mockPublisher = mock(EventPublisher.class);
        MnKafkaPublisherAdapter adapter = new MnKafkaPublisherAdapter(mockPublisher);

        // When: Call publish with empty headers
        Map<String, String> emptyHeaders = new HashMap<>();
        adapter.publish("test-topic", "test-key", "OrderCreated", "{\"orderId\": 456}", emptyHeaders);

        // Then: EventPublisher should be called
        verify(mockPublisher).publish("test-topic", "test-key", "{\"orderId\": 456}", emptyHeaders);
    }

    @Test
    @DisplayName("should ignore type parameter and pass other parameters correctly")
    void testTypeParameterIgnored() {
        // Given: Mock EventPublisher
        EventPublisher mockPublisher = mock(EventPublisher.class);
        MnKafkaPublisherAdapter adapter = new MnKafkaPublisherAdapter(mockPublisher);

        String topic = "events";
        String key = "user-1";
        String type = "UserRegistered"; // This should be ignored
        String payload = "{\"email\": \"user@example.com\"}";
        Map<String, String> headers = new HashMap<>();
        headers.put("source", "api");

        // When: Publish is called
        adapter.publish(topic, key, type, payload, headers);

        // Then: Verify that EventPublisher was called with topic, key, payload, and headers
        // (NOT with the type parameter)
        verify(mockPublisher, times(1))
                .publish(eq(topic), eq(key), eq(payload), eq(headers));
    }
}
