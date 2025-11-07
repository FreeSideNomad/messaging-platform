package com.acme.reliable.spi;

import java.util.Map;

public interface MqPublisher {
  void publish(String queue, String key, String type, String payload, Map<String, String> headers);
}
