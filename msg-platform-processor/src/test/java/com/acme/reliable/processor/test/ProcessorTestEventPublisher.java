package com.acme.reliable.processor.test;

import com.acme.reliable.spi.EventPublisher;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Capturable EventPublisher factory for processor integration tests.
 *
 * <p>Provides an EventPublisher implementation that captures all publish operations for verification.
 * Tests can access the captured messages via static methods for assertion.
 *
 * <p>This is necessary because the test environment disables the KafkaPublisherAdapter,
 * which would normally provide the EventPublisher bean.
 */
@Factory
@Requires(env = "test")
public class ProcessorTestEventPublisher {

    /**
     * Creates a capturable EventPublisher for testing.
     *
     * @return an EventPublisher that captures all publish operations
     */
    @Singleton
    public EventPublisher eventPublisher() {
        return new CapturableEventPublisher();
    }

    /**
     * Capturable implementation that records all publishes.
     */
    public static class CapturableEventPublisher implements EventPublisher {
        private static final ThreadLocal<List<PublishOperation>> captured = ThreadLocal.withInitial(ArrayList::new);

        public static List<PublishOperation> getCaptured() {
            return captured.get();
        }

        public static void reset() {
            captured.set(new ArrayList<>());
        }

        @Override
        public void publish(String topic, String key, String value, Map<String, String> headers) {
            captured.get().add(new PublishOperation(topic, key, value, headers));
        }
    }

    /**
     * Record of a publish operation.
     */
    public static class PublishOperation {
        public final String topic;
        public final String key;
        public final String value;
        public final Map<String, String> headers;

        public PublishOperation(String topic, String key, String value, Map<String, String> headers) {
            this.topic = topic;
            this.key = key;
            this.value = value;
            this.headers = headers;
        }
    }
}
