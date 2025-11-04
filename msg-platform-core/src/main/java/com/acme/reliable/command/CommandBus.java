package com.acme.reliable.command;

import java.util.Map;
import java.util.UUID;

public interface CommandBus {
    UUID accept(String name, String idempotencyKey, String businessKey, String payload, Map<String,String> reply);
}
