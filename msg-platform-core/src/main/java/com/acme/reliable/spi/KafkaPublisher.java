package com.acme.reliable.spi;

import java.util.Map;

public interface KafkaPublisher {
    void publish(String topic, String key, String type, String payload, Map<String, String> headers);
}
