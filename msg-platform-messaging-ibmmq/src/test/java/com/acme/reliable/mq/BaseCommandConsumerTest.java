package com.acme.reliable.mq;

import com.acme.reliable.command.CommandHandlerRegistry;
import com.acme.reliable.command.CommandMessage;
import com.acme.reliable.process.CommandReply;
import com.acme.reliable.spi.CommandQueue;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("BaseCommandConsumer - Command processing and reply handling")
class BaseCommandConsumerTest {

    private static final String REPLY_QUEUE = "APP.CMD.REPLY.Q";
    private CommandHandlerRegistry registry;
    private CommandQueue commandQueue;
    private TestCommandConsumer consumer;
    private Message jmsMessage;

    @BeforeEach
    void setUp() {
        registry = mock(CommandHandlerRegistry.class);
        commandQueue = mock(CommandQueue.class);
        jmsMessage = mock(Message.class);

        consumer = new TestCommandConsumer(registry, commandQueue, REPLY_QUEUE);
    }

    @Test
    @DisplayName("processCommand - should process command successfully")
    void testProcessCommandSuccess() throws JMSException {
        // Given
        String commandType = "CreateAccount";
        String body = "{\"customerId\": \"123\", \"currency\": \"USD\"}";
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        when(jmsMessage.getStringProperty("commandId")).thenReturn(commandId.toString());
        when(jmsMessage.getStringProperty("correlationId")).thenReturn(correlationId.toString());

        CommandReply expectedReply =
                CommandReply.completed(commandId, correlationId, Map.of("accountId", "ACC-001"));
        when(registry.handle(any(CommandMessage.class))).thenReturn(expectedReply);

        // When
        consumer.processCommand(commandType, body, jmsMessage);

        // Then
        verify(registry)
                .handle(
                        argThat(
                                cmd ->
                                        cmd.commandId().equals(commandId)
                                                && cmd.correlationId().equals(correlationId)
                                                && cmd.commandType().equals(commandType)
                                                && cmd.payload().equals(body)));

        // Verify reply was sent
        ArgumentCaptor<String> queueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> headersCaptor = ArgumentCaptor.forClass(Map.class);

        verify(commandQueue).send(queueCaptor.capture(), bodyCaptor.capture(), headersCaptor.capture());

        assertEquals(REPLY_QUEUE, queueCaptor.getValue());
        assertTrue(bodyCaptor.getValue().contains("COMPLETED"));
        assertEquals(correlationId.toString(), headersCaptor.getValue().get("correlationId"));
        assertEquals(commandId.toString(), headersCaptor.getValue().get("commandId"));
    }

    @Test
    @DisplayName("processCommand - should generate IDs when not provided in JMS message")
    void testProcessCommandGeneratesIds() throws JMSException {
        // Given
        String commandType = "CreateUser";
        String body = "{\"name\": \"John\"}";

        when(jmsMessage.getStringProperty("commandId")).thenReturn(null);
        when(jmsMessage.getStringProperty("correlationId")).thenReturn(null);

        UUID replyCommandId = UUID.randomUUID();
        UUID replyCorrelationId = UUID.randomUUID();
        CommandReply reply = CommandReply.completed(replyCommandId, replyCorrelationId, Map.of());
        when(registry.handle(any(CommandMessage.class))).thenReturn(reply);

        // When
        consumer.processCommand(commandType, body, jmsMessage);

        // Then
        ArgumentCaptor<CommandMessage> cmdCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        verify(registry).handle(cmdCaptor.capture());

        CommandMessage capturedCmd = cmdCaptor.getValue();
        assertNotNull(capturedCmd.commandId());
        assertNotNull(capturedCmd.correlationId());
        assertEquals(commandType, capturedCmd.commandType());
        assertEquals(body, capturedCmd.payload());
    }

    @Test
    @DisplayName("processCommand - should send error reply on exception")
    void testProcessCommandHandlesException() throws JMSException {
        // Given
        String commandType = "CreatePayment";
        String body = "{\"amount\": 100}";
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        when(jmsMessage.getStringProperty("commandId")).thenReturn(commandId.toString());
        when(jmsMessage.getStringProperty("correlationId")).thenReturn(correlationId.toString());

        when(registry.handle(any(CommandMessage.class)))
                .thenThrow(new RuntimeException("Insufficient funds"));

        // When
        consumer.processCommand(commandType, body, jmsMessage);

        // Then
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> headersCaptor = ArgumentCaptor.forClass(Map.class);

        verify(commandQueue).send(eq(REPLY_QUEUE), bodyCaptor.capture(), headersCaptor.capture());

        String replyBody = bodyCaptor.getValue();
        assertTrue(replyBody.contains("FAILED"));
        assertTrue(replyBody.contains("Insufficient funds"));

        Map<String, String> headers = headersCaptor.getValue();
        assertEquals(correlationId.toString(), headers.get("correlationId"));
        assertEquals("FAILED", headers.get("status"));
    }

    @Test
    @DisplayName("processCommand - should handle JMSException when extracting properties")
    void testProcessCommandHandlesJmsException() throws JMSException {
        // Given
        String commandType = "CreateUser";
        String body = "{\"name\": \"Jane\"}";

        when(jmsMessage.getStringProperty("commandId"))
                .thenThrow(new JMSException("Property read failed"));

        // When/Then - should throw JMSException
        assertThrows(JMSException.class, () -> consumer.processCommand(commandType, body, jmsMessage));
    }

    @Test
    @DisplayName("processCommand - should handle invalid UUID format")
    void testProcessCommandHandlesInvalidUuid() throws JMSException {
        // Given
        String commandType = "CreateAccount";
        String body = "{\"test\": \"data\"}";

        when(jmsMessage.getStringProperty("commandId")).thenReturn("not-a-uuid");
        when(jmsMessage.getStringProperty("correlationId")).thenReturn(UUID.randomUUID().toString());

        CommandReply reply = CommandReply.completed(UUID.randomUUID(), UUID.randomUUID(), Map.of());
        when(registry.handle(any(CommandMessage.class))).thenReturn(reply);

        // When/Then - should handle exception and send error reply
        consumer.processCommand(commandType, body, jmsMessage);

        // Should have sent an error reply
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(commandQueue).send(eq(REPLY_QUEUE), bodyCaptor.capture(), any());

        assertTrue(bodyCaptor.getValue().contains("FAILED"));
    }

    @Test
    @DisplayName("processCommand - should log error if reply send fails")
    void testProcessCommandHandlesReplySendFailure() throws JMSException {
        // Given
        String commandType = "CreateUser";
        String body = "{\"name\": \"Bob\"}";
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        when(jmsMessage.getStringProperty("commandId")).thenReturn(commandId.toString());
        when(jmsMessage.getStringProperty("correlationId")).thenReturn(correlationId.toString());

        CommandReply reply = CommandReply.completed(commandId, correlationId, Map.of());
        when(registry.handle(any(CommandMessage.class))).thenReturn(reply);

        doThrow(new RuntimeException("Queue unavailable"))
                .when(commandQueue)
                .send(anyString(), anyString(), any());

        // When - should not throw exception
        assertDoesNotThrow(() -> consumer.processCommand(commandType, body, jmsMessage));

        // Then - should have attempted to send reply
        verify(commandQueue).send(eq(REPLY_QUEUE), anyString(), any());
    }

    @Test
    @DisplayName("processCommand - should send reply with correct status for completed command")
    void testProcessCommandSendsCompletedStatus() throws JMSException {
        // Given
        String commandType = "UpdateAccount";
        String body = "{\"accountId\": \"123\"}";
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        when(jmsMessage.getStringProperty("commandId")).thenReturn(commandId.toString());
        when(jmsMessage.getStringProperty("correlationId")).thenReturn(correlationId.toString());

        CommandReply reply =
                CommandReply.completed(commandId, correlationId, Map.of("result", "success"));
        when(registry.handle(any(CommandMessage.class))).thenReturn(reply);

        // When
        consumer.processCommand(commandType, body, jmsMessage);

        // Then
        ArgumentCaptor<Map> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(commandQueue).send(eq(REPLY_QUEUE), anyString(), headersCaptor.capture());

        Map<String, String> headers = headersCaptor.getValue();
        assertEquals("COMPLETED", headers.get("status"));
    }

    @Test
    @DisplayName("processCommand - should include all required headers in reply")
    void testProcessCommandIncludesAllReplyHeaders() throws JMSException {
        // Given
        String commandType = "DeleteAccount";
        String body = "{\"accountId\": \"456\"}";
        UUID commandId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        when(jmsMessage.getStringProperty("commandId")).thenReturn(commandId.toString());
        when(jmsMessage.getStringProperty("correlationId")).thenReturn(correlationId.toString());

        CommandReply reply = CommandReply.completed(commandId, correlationId, Map.of());
        when(registry.handle(any(CommandMessage.class))).thenReturn(reply);

        // When
        consumer.processCommand(commandType, body, jmsMessage);

        // Then
        ArgumentCaptor<Map> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(commandQueue).send(eq(REPLY_QUEUE), anyString(), headersCaptor.capture());

        Map<String, String> headers = headersCaptor.getValue();
        assertTrue(headers.containsKey("correlationId"));
        assertTrue(headers.containsKey("commandId"));
        assertTrue(headers.containsKey("status"));
        assertEquals(3, headers.size());
    }

    /**
     * Concrete test implementation of BaseCommandConsumer for testing purposes
     */
    private static class TestCommandConsumer extends BaseCommandConsumer {
        protected TestCommandConsumer(
                CommandHandlerRegistry registry, CommandQueue commandQueue, String replyQueue) {
            super(registry, commandQueue, replyQueue);
        }

        public void processCommand(String commandType, String body, Message m) throws JMSException {
            super.processCommand(commandType, body, m);
        }
    }
}
