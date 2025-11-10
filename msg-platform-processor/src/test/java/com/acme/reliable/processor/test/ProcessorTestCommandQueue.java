package com.acme.reliable.processor.test;

import com.acme.reliable.spi.CommandQueue;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Capturable CommandQueue factory for processor integration tests.
 *
 * <p>Provides a CommandQueue implementation that captures all send operations for verification.
 * Tests can access the captured messages via static methods for assertion.
 *
 * <p>This is necessary because the test environment disables the IbmMqFactoryProvider,
 * which would normally provide the CommandQueue bean.
 */
@Factory
@Requires(env = "test")
public class ProcessorTestCommandQueue {

    /**
     * Creates a capturable CommandQueue for testing.
     *
     * @return a CommandQueue that captures all send operations
     */
    @Singleton
    public CommandQueue commandQueue() {
        return new CapturableCommandQueue();
    }

    /**
     * Capturable implementation that records all sends.
     */
    public static class CapturableCommandQueue implements CommandQueue {
        private static final ThreadLocal<List<SendOperation>> captured = ThreadLocal.withInitial(ArrayList::new);

        public static List<SendOperation> getCaptured() {
            return captured.get();
        }

        public static void reset() {
            captured.set(new ArrayList<>());
        }

        @Override
        public void send(String queueName, String message, Map<String, String> headers) {
            captured.get().add(new SendOperation(queueName, message, headers));
        }
    }

    /**
     * Record of a send operation.
     */
    public static class SendOperation {
        public final String queueName;
        public final String message;
        public final Map<String, String> headers;

        public SendOperation(String queueName, String message, Map<String, String> headers) {
            this.queueName = queueName;
            this.message = message;
            this.headers = headers;
        }
    }
}
