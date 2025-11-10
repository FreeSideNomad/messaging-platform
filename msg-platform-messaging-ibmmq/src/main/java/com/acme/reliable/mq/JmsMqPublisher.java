package com.acme.reliable.mq;

import com.acme.reliable.spi.CommandQueue;
import com.acme.reliable.spi.MqPublisher;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
@Requires(beans = CommandQueue.class)
public class JmsMqPublisher implements MqPublisher {

    private final CommandQueue commandQueue;

    public JmsMqPublisher(CommandQueue commandQueue) {
        this.commandQueue = commandQueue;
    }

    @Override
    public void publish(
            String queue, String key, String type, String payload, Map<String, String> headers) {
        commandQueue.send(queue, payload, headers);
    }
}
