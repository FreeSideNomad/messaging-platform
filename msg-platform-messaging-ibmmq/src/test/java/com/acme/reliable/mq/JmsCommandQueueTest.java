package com.acme.reliable.mq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.jms.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JmsCommandQueue - JMS message sending")
class JmsCommandQueueTest {

  private ConnectionFactory connectionFactory;
  private Connection connection;
  private Session session;
  private MessageProducer producer;
  private JmsCommandQueue commandQueue;

  @BeforeEach
  void setUp() throws JMSException {
    // Create mocks
    connectionFactory = mock(ConnectionFactory.class);
    connection = mock(Connection.class);
    session = mock(Session.class);
    producer = mock(MessageProducer.class);

    // Configure mock behavior
    when(connectionFactory.createConnection()).thenReturn(connection);
    when(connection.createSession(true, Session.SESSION_TRANSACTED)).thenReturn(session);
    when(session.createProducer(null)).thenReturn(producer);

    // Create instance
    commandQueue = new JmsCommandQueue(connectionFactory);
  }

  @Test
  @DisplayName("Constructor - should initialize connection and start it")
  void testConstructor() throws JMSException {
    // Verify connection was created and started
    verify(connectionFactory).createConnection();
    verify(connection).start();
  }

  @Test
  @DisplayName("send - should send message to correct queue")
  void testSendToQueue() throws JMSException {
    // Given
    String queueName = "TEST.QUEUE";
    String messageBody = "{\"test\": \"data\"}";
    Map<String, String> headers = new HashMap<>();

    Queue destination = mock(Queue.class);
    TextMessage textMessage = mock(TextMessage.class);

    when(session.createQueue(queueName)).thenReturn(destination);
    when(session.createTextMessage(messageBody)).thenReturn(textMessage);

    // When
    commandQueue.send(queueName, messageBody, headers);

    // Then
    verify(session).createQueue(queueName);
    verify(session).createTextMessage(messageBody);
    verify(producer).send(destination, textMessage);
    verify(session).commit();
  }

  @Test
  @DisplayName("send - should set correlationId header")
  void testSendWithCorrelationId() throws JMSException {
    // Given
    String queueName = "TEST.QUEUE";
    String messageBody = "{\"test\": \"data\"}";
    Map<String, String> headers = new HashMap<>();
    headers.put("correlationId", "corr-123");

    Queue destination = mock(Queue.class);
    TextMessage textMessage = mock(TextMessage.class);

    when(session.createQueue(anyString())).thenReturn(destination);
    when(session.createTextMessage(messageBody)).thenReturn(textMessage);

    // When
    commandQueue.send(queueName, messageBody, headers);

    // Then
    verify(textMessage).setJMSCorrelationID("corr-123");
    verify(session).commit();
  }

  @Test
  @DisplayName("send - should set replyTo queue")
  void testSendWithReplyTo() throws JMSException {
    // Given
    String queueName = "TEST.QUEUE";
    String messageBody = "{\"test\": \"data\"}";
    Map<String, String> headers = new HashMap<>();
    headers.put("replyTo", "REPLY.QUEUE");

    Queue destination = mock(Queue.class);
    Queue replyQueue = mock(Queue.class);
    TextMessage textMessage = mock(TextMessage.class);

    when(session.createQueue(queueName)).thenReturn(destination);
    when(session.createQueue("REPLY.QUEUE")).thenReturn(replyQueue);
    when(session.createTextMessage(messageBody)).thenReturn(textMessage);

    // When
    commandQueue.send(queueName, messageBody, headers);

    // Then
    verify(textMessage).setJMSReplyTo(replyQueue);
    verify(session).commit();
  }

  @Test
  @DisplayName("send - should set custom string properties")
  void testSendWithCustomHeaders() throws JMSException {
    // Given
    String queueName = "TEST.QUEUE";
    String messageBody = "{\"test\": \"data\"}";
    Map<String, String> headers = new HashMap<>();
    headers.put("customHeader1", "value1");
    headers.put("customHeader2", "value2");

    Queue destination = mock(Queue.class);
    TextMessage textMessage = mock(TextMessage.class);

    when(session.createQueue(queueName)).thenReturn(destination);
    when(session.createTextMessage(messageBody)).thenReturn(textMessage);

    // When
    commandQueue.send(queueName, messageBody, headers);

    // Then
    verify(textMessage).setStringProperty("customHeader1", "value1");
    verify(textMessage).setStringProperty("customHeader2", "value2");
    verify(session).commit();
  }

  @Test
  @DisplayName("send - should skip IBM MQ internal headers")
  void testSendSkipsInternalHeaders() throws JMSException {
    // Given
    String queueName = "TEST.QUEUE";
    String messageBody = "{\"test\": \"data\"}";
    Map<String, String> headers = new HashMap<>();
    headers.put("JMS_IBM_Character_Set", "UTF8");
    headers.put("JMS_IBM_Encoding", "273");
    headers.put("JMSX_GroupID", "group1");
    headers.put("mode", "sync");
    headers.put("validHeader", "validValue");

    Queue destination = mock(Queue.class);
    TextMessage textMessage = mock(TextMessage.class);

    when(session.createQueue(queueName)).thenReturn(destination);
    when(session.createTextMessage(messageBody)).thenReturn(textMessage);

    // When
    commandQueue.send(queueName, messageBody, headers);

    // Then
    // Should NOT set internal headers
    verify(textMessage, never()).setStringProperty(eq("JMS_IBM_Character_Set"), anyString());
    verify(textMessage, never()).setStringProperty(eq("JMS_IBM_Encoding"), anyString());
    verify(textMessage, never()).setStringProperty(eq("JMSX_GroupID"), anyString());
    verify(textMessage, never()).setStringProperty(eq("mode"), anyString());

    // Should set valid header
    verify(textMessage).setStringProperty("validHeader", "validValue");
    verify(session).commit();
  }

  @Test
  @DisplayName("send - should handle null headers")
  void testSendWithNullHeaders() throws JMSException {
    // Given
    String queueName = "TEST.QUEUE";
    String messageBody = "{\"test\": \"data\"}";

    Queue destination = mock(Queue.class);
    TextMessage textMessage = mock(TextMessage.class);

    when(session.createQueue(queueName)).thenReturn(destination);
    when(session.createTextMessage(messageBody)).thenReturn(textMessage);

    // When
    commandQueue.send(queueName, messageBody, null);

    // Then
    verify(producer).send(destination, textMessage);
    verify(session).commit();
  }

  @Test
  @DisplayName("send - should rollback on error")
  void testSendRollbackOnError() throws JMSException {
    // Given
    String queueName = "TEST.QUEUE";
    String messageBody = "{\"test\": \"data\"}";
    Map<String, String> headers = new HashMap<>();

    Queue destination = mock(Queue.class);
    TextMessage textMessage = mock(TextMessage.class);

    when(session.createQueue(queueName)).thenReturn(destination);
    when(session.createTextMessage(messageBody)).thenReturn(textMessage);
    doThrow(new JMSException("Send failed"))
        .when(producer)
        .send(any(Queue.class), any(Message.class));

    // When/Then
    assertThrows(RuntimeException.class, () -> commandQueue.send(queueName, messageBody, headers));
    verify(session).rollback();
  }

  @Test
  @DisplayName("send - should handle rollback failure gracefully")
  void testSendHandlesRollbackFailure() throws JMSException {
    // Given
    String queueName = "TEST.QUEUE";
    String messageBody = "{\"test\": \"data\"}";
    Map<String, String> headers = new HashMap<>();

    Queue destination = mock(Queue.class);
    TextMessage textMessage = mock(TextMessage.class);

    when(session.createQueue(queueName)).thenReturn(destination);
    when(session.createTextMessage(messageBody)).thenReturn(textMessage);
    doThrow(new JMSException("Send failed"))
        .when(producer)
        .send(any(Queue.class), any(Message.class));
    doThrow(new JMSException("Rollback failed")).when(session).rollback();

    // When/Then
    RuntimeException ex =
        assertThrows(
            RuntimeException.class, () -> commandQueue.send(queueName, messageBody, headers));
    assertTrue(ex.getMessage().contains("Failed to send message"));
  }

  @Test
  @DisplayName("send - should reuse session and producer across calls")
  void testSessionReuse() throws JMSException {
    // Given
    String queueName = "TEST.QUEUE";
    String messageBody = "{\"test\": \"data\"}";
    Map<String, String> headers = new HashMap<>();

    Queue destination = mock(Queue.class);
    TextMessage textMessage = mock(TextMessage.class);

    when(session.createQueue(queueName)).thenReturn(destination);
    when(session.createTextMessage(messageBody)).thenReturn(textMessage);

    // When - send multiple messages
    commandQueue.send(queueName, messageBody, headers);
    commandQueue.send(queueName, messageBody, headers);
    commandQueue.send(queueName, messageBody, headers);

    // Then - should only create one session (per thread)
    verify(connection, times(1)).createSession(true, Session.SESSION_TRANSACTED);
    verify(session, times(1)).createProducer(null);
    verify(session, times(3)).commit();
  }

  @Test
  @DisplayName("shutdown - should close connection")
  void testShutdown() throws Exception {
    // Given
    JmsCommandQueue queue = new JmsCommandQueue(connectionFactory);

    // When
    queue.shutdown();

    // Then
    verify(connection).close();
  }

  @Test
  @DisplayName("shutdown - should handle close errors gracefully")
  void testShutdownHandlesErrors() throws Exception {
    // Given
    doThrow(new JMSException("Close failed")).when(connection).close();

    // When/Then - should not throw
    assertDoesNotThrow(() -> commandQueue.shutdown());
  }

  @Test
  @DisplayName("send - should set both correlationId and replyTo together")
  void testSendWithBothCorrelationIdAndReplyTo() throws JMSException {
    // Given
    String queueName = "TEST.QUEUE";
    String messageBody = "{\"test\": \"data\"}";
    Map<String, String> headers = new HashMap<>();
    headers.put("correlationId", "corr-456");
    headers.put("replyTo", "REPLY.QUEUE");
    headers.put("customProp", "customValue");

    Queue destination = mock(Queue.class);
    Queue replyQueue = mock(Queue.class);
    TextMessage textMessage = mock(TextMessage.class);

    when(session.createQueue(queueName)).thenReturn(destination);
    when(session.createQueue("REPLY.QUEUE")).thenReturn(replyQueue);
    when(session.createTextMessage(messageBody)).thenReturn(textMessage);

    // When
    commandQueue.send(queueName, messageBody, headers);

    // Then
    verify(textMessage).setJMSCorrelationID("corr-456");
    verify(textMessage).setJMSReplyTo(replyQueue);
    verify(textMessage).setStringProperty("customProp", "customValue");
    // Should not set correlationId and replyTo as string properties
    verify(textMessage, never()).setStringProperty(eq("correlationId"), anyString());
    verify(textMessage, never()).setStringProperty(eq("replyTo"), anyString());
    verify(session).commit();
  }
}
