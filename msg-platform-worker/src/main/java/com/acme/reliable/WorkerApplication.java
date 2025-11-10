package com.acme.reliable;

import io.micronaut.runtime.Micronaut;

/**
 * Worker Application - Processes commands from IBM MQ. Consumes commands from queue, executes
 * handlers, publishes replies/events. Can run multiple instances for horizontal scaling.
 */
public class WorkerApplication {
    public static void main(String[] args) {
        Micronaut.run(WorkerApplication.class, args);
    }
}
