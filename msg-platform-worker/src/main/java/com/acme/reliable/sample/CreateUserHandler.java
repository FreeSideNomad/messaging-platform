package com.acme.reliable.sample;

import com.acme.reliable.core.PermanentException;
import com.acme.reliable.core.TransientException;
import com.acme.reliable.spi.HandlerRegistry;
import jakarta.inject.Singleton;

@Singleton
public class CreateUserHandler implements HandlerRegistry {

    @Override
    public String invoke(String name, String payload) {
        if (!"CreateUser".equals(name)) {
            throw new IllegalArgumentException("Unknown " + name);
        }
        if (payload.contains("\"failPermanent\"")) {
            throw new PermanentException("Invariant broken");
        }
        if (payload.contains("\"failTransient\"")) {
            throw new TransientException("Downstream timeout");
        }
        return "{\"userId\":\"u-123\"}";
    }
}
