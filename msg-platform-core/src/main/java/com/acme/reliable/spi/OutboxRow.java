package com.acme.reliable.spi;

import java.util.Map;
import java.util.UUID;

public record OutboxRow(
    UUID id,
    String category,
    String topic,
    String key,
    String type,
    String payload,
    Map<String,String> headers,
    int attempts
) {}
