package com.acme.reliable;

import io.micronaut.runtime.Micronaut;

/**
 * API Application - REST endpoint for accepting commands.
 * Submits commands to database and IBM MQ queue.
 * Does NOT process commands - that's done by Workers.
 */
public class ApiApplication {
    public static void main(String[] args) {
        Micronaut.run(ApiApplication.class, args);
    }
}
