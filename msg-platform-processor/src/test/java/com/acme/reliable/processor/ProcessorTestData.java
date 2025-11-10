package com.acme.reliable.processor;

import com.acme.reliable.domain.Outbox;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test data builders for creating processor domain objects in tests.
 *
 * <p>Provides fluent builders for:
 * - Outbox entries with various states
 * - Command messages
 * - Event messages
 * - Reply messages
 */
public class ProcessorTestData {

    private ProcessorTestData() {
        // Utility class
    }

    /**
     * Fluent builder for creating Outbox entries in tests.
     *
     * <p>Example usage:
     * <pre>
     * Outbox outbox = OutboxBuilder.aCommand()
     *     .withTopic("ORDERS.Q")
     *     .withPayload("{\"orderId\":\"O-001\"}")
     *     .build();
     * </pre>
     */
    public static class OutboxBuilder {

        private Long id;
        private String category = "command";
        private String topic = "TEST.Q";
        private String key = UUID.randomUUID().toString();
        private String type = "TestCommand";
        private String payload = "{}";
        private Map<String, String> headers = new HashMap<>();
        private String status = "NEW";
        private int attempts = 0;
        private Instant nextAt;
        private String claimedBy;
        private Instant createdAt = Instant.now();
        private Instant publishedAt;
        private String lastError;

        /**
         * Create a builder for a command message (routes to MQ)
         */
        public static OutboxBuilder aCommand() {
            OutboxBuilder builder = new OutboxBuilder();
            builder.category = "command";
            builder.topic = "COMMANDS.Q";
            return builder;
        }

        /**
         * Create a builder for a reply message (routes to MQ)
         */
        public static OutboxBuilder aReply() {
            OutboxBuilder builder = new OutboxBuilder();
            builder.category = "reply";
            builder.topic = "REPLIES.Q";
            return builder;
        }

        /**
         * Create a builder for an event message (routes to Kafka)
         */
        public static OutboxBuilder anEvent() {
            OutboxBuilder builder = new OutboxBuilder();
            builder.category = "event";
            builder.topic = "events";
            return builder;
        }

        public OutboxBuilder withId(long id) {
            this.id = id;
            return this;
        }

        public OutboxBuilder withCategory(String category) {
            this.category = category;
            return this;
        }

        public OutboxBuilder withTopic(String topic) {
            this.topic = topic;
            return this;
        }

        public OutboxBuilder withKey(String key) {
            this.key = key;
            return this;
        }

        public OutboxBuilder withType(String type) {
            this.type = type;
            return this;
        }

        public OutboxBuilder withPayload(String payload) {
            this.payload = payload;
            return this;
        }

        public OutboxBuilder withPayload(Object payloadObj) {
            this.payload = com.acme.reliable.core.Jsons.toJson(payloadObj);
            return this;
        }

        public OutboxBuilder withHeader(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        public OutboxBuilder withHeaders(Map<String, String> headers) {
            this.headers = new HashMap<>(headers);
            return this;
        }

        public OutboxBuilder withStatus(String status) {
            this.status = status;
            return this;
        }

        public OutboxBuilder withAttempts(int attempts) {
            this.attempts = attempts;
            return this;
        }

        public OutboxBuilder withNextAt(Instant nextAt) {
            this.nextAt = nextAt;
            return this;
        }

        public OutboxBuilder withClaimedBy(String claimer) {
            this.claimedBy = claimer;
            return this;
        }

        public OutboxBuilder withCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public OutboxBuilder withPublishedAt(Instant publishedAt) {
            this.publishedAt = publishedAt;
            return this;
        }

        public OutboxBuilder withLastError(String error) {
            this.lastError = error;
            return this;
        }

        public OutboxBuilder asPending() {
            this.status = "NEW";
            return this;
        }

        public OutboxBuilder asSending() {
            this.status = "SENDING";
            return this;
        }

        public OutboxBuilder asPublished() {
            this.status = "PUBLISHED";
            this.publishedAt = Instant.now();
            return this;
        }

        public OutboxBuilder asFailed() {
            this.status = "FAILED";
            this.lastError = "Test failure";
            return this;
        }

        public Outbox build() {
            Outbox outbox = new Outbox(
                    id, category, topic, key, type, payload, headers, status, attempts
            );
            if (nextAt != null) {
                outbox.setNextAt(nextAt);
            }
            if (claimedBy != null) {
                outbox.setClaimedBy(claimedBy);
            }
            if (createdAt != null) {
                outbox.setCreatedAt(createdAt);
            }
            if (publishedAt != null) {
                outbox.setPublishedAt(publishedAt);
            }
            if (lastError != null) {
                outbox.setLastError(lastError);
            }
            return outbox;
        }
    }

    /**
     * Builder for creating command test data
     */
    public static class CommandBuilder {

        private UUID commandId = UUID.randomUUID();
        private String name = "TestCommand";
        private String businessKey = UUID.randomUUID().toString();
        private String payload = "{}";
        private String replyJson = "{}";
        private String idempotencyKey = UUID.randomUUID().toString();

        public static CommandBuilder aCommand() {
            return new CommandBuilder();
        }

        public CommandBuilder withCommandId(UUID id) {
            this.commandId = id;
            return this;
        }

        public CommandBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public CommandBuilder withBusinessKey(String key) {
            this.businessKey = key;
            return this;
        }

        public CommandBuilder withPayload(String payload) {
            this.payload = payload;
            return this;
        }

        public CommandBuilder withIdempotencyKey(String key) {
            this.idempotencyKey = key;
            return this;
        }

        public CommandBuilder withReplyJson(String json) {
            this.replyJson = json;
            return this;
        }

        public Map<String, Object> build() {
            return Map.of(
                    "commandId", commandId.toString(),
                    "name", name,
                    "businessKey", businessKey,
                    "payload", payload,
                    "replyJson", replyJson,
                    "idempotencyKey", idempotencyKey
            );
        }
    }

    /**
     * Builder for creating message/event test data
     */
    public static class MessageBuilder {

        private UUID messageId = UUID.randomUUID();
        private String type = "TestMessage";
        private String handler = "TestHandler";
        private Map<String, Object> data = new HashMap<>();

        public static MessageBuilder aMessage() {
            return new MessageBuilder();
        }

        public MessageBuilder withMessageId(UUID id) {
            this.messageId = id;
            return this;
        }

        public MessageBuilder withType(String type) {
            this.type = type;
            return this;
        }

        public MessageBuilder withHandler(String handler) {
            this.handler = handler;
            return this;
        }

        public MessageBuilder withData(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public MessageBuilder withData(Map<String, Object> data) {
            this.data = new HashMap<>(data);
            return this;
        }

        public Map<String, Object> build() {
            Map<String, Object> message = new HashMap<>();
            message.put("messageId", messageId.toString());
            message.put("type", type);
            message.put("handler", handler);
            message.putAll(data);
            return message;
        }

        public String buildJson() {
            return com.acme.reliable.core.Jsons.toJson(build());
        }
    }

    /**
     * Test data constants for common scenarios
     */
    public static class TestConstants {

        public static final String ORDER_COMMAND = "CreateOrder";
        public static final String PAYMENT_COMMAND = "ProcessPayment";
        public static final String SHIPMENT_COMMAND = "ShipOrder";

        public static final String ORDER_CREATED_EVENT = "OrderCreated";
        public static final String PAYMENT_COMPLETED_EVENT = "PaymentCompleted";
        public static final String ORDER_SHIPPED_EVENT = "OrderShipped";

        public static final String ORDERS_QUEUE = "ORDERS.Q";
        public static final String PAYMENTS_QUEUE = "PAYMENTS.Q";
        public static final String REPLIES_QUEUE = "REPLIES.Q";

        public static final String ORDERS_TOPIC = "order-events";
        public static final String PAYMENTS_TOPIC = "payment-events";
        public static final String SHIPMENT_TOPIC = "shipment-events";
    }

    /**
     * Factory methods for common test scenarios
     */
    public static class Scenarios {

        /**
         * Create a typical order command outbox entry
         */
        public static Outbox createOrderCommand(String orderId) {
            return OutboxBuilder.aCommand()
                    .withTopic(TestConstants.ORDERS_QUEUE)
                    .withType(TestConstants.ORDER_COMMAND)
                    .withKey(orderId)
                    .withPayload(Map.of("orderId", orderId, "amount", 99.99))
                    .build();
        }

        /**
         * Create a typical payment command outbox entry
         */
        public static Outbox createPaymentCommand(String paymentId) {
            return OutboxBuilder.aCommand()
                    .withTopic(TestConstants.PAYMENTS_QUEUE)
                    .withType(TestConstants.PAYMENT_COMMAND)
                    .withKey(paymentId)
                    .withPayload(Map.of("paymentId", paymentId, "amount", 99.99))
                    .build();
        }

        /**
         * Create a typical order created event
         */
        public static Outbox createOrderCreatedEvent(String orderId) {
            return OutboxBuilder.anEvent()
                    .withTopic(TestConstants.ORDERS_TOPIC)
                    .withType(TestConstants.ORDER_CREATED_EVENT)
                    .withKey(orderId)
                    .withPayload(Map.of(
                            "orderId", orderId,
                            "timestamp", Instant.now().toString(),
                            "amount", 99.99
                    ))
                    .build();
        }

        /**
         * Create a reply message
         */
        public static Outbox createReply(UUID commandId, String replyType) {
            return OutboxBuilder.aReply()
                    .withTopic(TestConstants.REPLIES_QUEUE)
                    .withType(replyType)
                    .withKey(commandId.toString())
                    .withPayload(Map.of(
                            "commandId", commandId.toString(),
                            "status", "processed"
                    ))
                    .build();
        }
    }
}
