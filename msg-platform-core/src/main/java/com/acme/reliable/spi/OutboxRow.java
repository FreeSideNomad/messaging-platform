package com.acme.reliable.spi;

import java.util.Map;

public record OutboxRow(
    long id,
    String category,
    String topic,
    String key,
    String type,
    String payload,
    Map<String, String> headers,
    int attempts) {}
