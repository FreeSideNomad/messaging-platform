package com.acme.reliable.kafka;

import com.acme.reliable.spi.EventPublisher;
import com.acme.reliable.spi.KafkaPublisher;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
@Requires(beans = EventPublisher.class)
public class MnKafkaPublisherAdapter implements KafkaPublisher {

    private final EventPublisher eventPublisher;

    public MnKafkaPublisherAdapter(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(
            String topic, String key, String type, String payload, Map<String, String> headers) {
        eventPublisher.publish(topic, key, payload, headers);
    }
}
