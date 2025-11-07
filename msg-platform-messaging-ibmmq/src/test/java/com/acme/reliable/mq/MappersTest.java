package com.acme.reliable.mq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.acme.reliable.core.Envelope;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import java.util.Enumeration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Mappers - JMS to Envelope conversion")
class MappersTest {

  @Test
  @DisplayName("toEnvelope - should convert basic JMS message to Envelope")
  void testToEnvelopeBasic() throws JMSException {
    // Given
    String messageBody =
        """
        {
          "customerId": "123",
          "accountNumber": "ACC-001",
          "currencyCode": "USD"
        }
        """;

    Message jmsMessage = mock(Message.class);
    when(jmsMessage.getPropertyNames()).thenReturn(emptyEnumeration());
    when(jmsMessage.getJMSCorrelationID()).thenReturn("corr-123");
    when(jmsMessage.getJMSReplyTo()).thenReturn(null);

    Queue destination = mock(Queue.class);
    when(destination.toString()).thenReturn("queue:///APP.CMD.CreateAccount.Q");
    when(jmsMessage.getJMSDestination()).thenReturn(destination);

    // When
    Envelope envelope = Mappers.toEnvelope(messageBody, jmsMessage);

    // Then
    assertNotNull(envelope);
    assertEquals("CommandRequested", envelope.type());
    assertEquals("CreateAccount", envelope.name());
    assertNotNull(envelope.messageId());
    assertNotNull(envelope.commandId());
    assertEquals(messageBody, envelope.payload());
  }

  @Test
  @DisplayName("toEnvelope - should extract correlationId from JMS header")
  void testToEnvelopeWithCorrelationId() throws JMSException {
    // Given
    String messageBody = "{\"test\": \"data\"}";
    String correlationId = "corr-456";

    Message jmsMessage = mock(Message.class);
    when(jmsMessage.getPropertyNames()).thenReturn(emptyEnumeration());
    when(jmsMessage.getJMSCorrelationID()).thenReturn(correlationId);
    when(jmsMessage.getJMSReplyTo()).thenReturn(null);

    Queue destination = mock(Queue.class);
    when(destination.toString()).thenReturn("queue:///APP.CMD.CreateUser.Q");
    when(jmsMessage.getJMSDestination()).thenReturn(destination);

    // When
    Envelope envelope = Mappers.toEnvelope(messageBody, jmsMessage);

    // Then
    assertNotNull(envelope);
    assertTrue(envelope.headers().containsKey("correlationId"));
    assertEquals(correlationId, envelope.headers().get("correlationId"));
  }

  @Test
  @DisplayName("toEnvelope - should extract replyTo from JMS header")
  void testToEnvelopeWithReplyTo() throws JMSException {
    // Given
    String messageBody = "{\"test\": \"data\"}";
    Queue replyQueue = mock(Queue.class);
    when(replyQueue.toString()).thenReturn("APP.CMD.REPLY.Q");

    Message jmsMessage = mock(Message.class);
    when(jmsMessage.getPropertyNames()).thenReturn(emptyEnumeration());
    when(jmsMessage.getJMSCorrelationID()).thenReturn(null);
    when(jmsMessage.getJMSReplyTo()).thenReturn(replyQueue);

    Queue destination = mock(Queue.class);
    when(destination.toString()).thenReturn("queue:///APP.CMD.CreateUser.Q");
    when(jmsMessage.getJMSDestination()).thenReturn(destination);

    // When
    Envelope envelope = Mappers.toEnvelope(messageBody, jmsMessage);

    // Then
    assertNotNull(envelope);
    assertTrue(envelope.headers().containsKey("replyTo"));
    assertEquals("APP.CMD.REPLY.Q", envelope.headers().get("replyTo"));
  }

  @Test
  @DisplayName("toEnvelope - should extract commandName from message headers")
  void testToEnvelopeWithCommandNameInHeaders() throws JMSException {
    // Given
    String messageBody = "{\"test\": \"data\"}";
    String commandName = "CreatePayment";

    Message jmsMessage = mock(Message.class);
    Enumeration<String> propertyNames =
        new Enumeration<String>() {
          private String[] names = {"commandName"};
          private int index = 0;

          @Override
          public boolean hasMoreElements() {
            return index < names.length;
          }

          @Override
          public String nextElement() {
            return names[index++];
          }
        };
    when(jmsMessage.getPropertyNames()).thenReturn(propertyNames);
    when(jmsMessage.getStringProperty("commandName")).thenReturn(commandName);
    when(jmsMessage.getJMSCorrelationID()).thenReturn(null);
    when(jmsMessage.getJMSReplyTo()).thenReturn(null);
    when(jmsMessage.getJMSDestination()).thenReturn(null);

    // When
    Envelope envelope = Mappers.toEnvelope(messageBody, jmsMessage);

    // Then
    assertNotNull(envelope);
    assertEquals(commandName, envelope.name());
  }

  @Test
  @DisplayName("toEnvelope - should extract commandName from payload")
  void testToEnvelopeWithCommandNameInPayload() throws JMSException {
    // Given
    String messageBody = "{\"commandName\": \"ProcessRefund\", \"amount\": 100}";

    Message jmsMessage = mock(Message.class);
    when(jmsMessage.getPropertyNames()).thenReturn(emptyEnumeration());
    when(jmsMessage.getJMSCorrelationID()).thenReturn(null);
    when(jmsMessage.getJMSReplyTo()).thenReturn(null);
    when(jmsMessage.getJMSDestination()).thenReturn(null);

    // When
    Envelope envelope = Mappers.toEnvelope(messageBody, jmsMessage);

    // Then
    assertNotNull(envelope);
    assertEquals("ProcessRefund", envelope.name());
  }

  @Test
  @DisplayName("toEnvelope - should derive commandName from queue destination")
  void testToEnvelopeDeriveCommandNameFromDestination() throws JMSException {
    // Given
    String messageBody = "{\"test\": \"data\"}";

    Message jmsMessage = mock(Message.class);
    when(jmsMessage.getPropertyNames()).thenReturn(emptyEnumeration());
    when(jmsMessage.getJMSCorrelationID()).thenReturn(null);
    when(jmsMessage.getJMSReplyTo()).thenReturn(null);

    Queue destination = mock(Queue.class);
    when(destination.toString()).thenReturn("queue:///APP.CMD.UpdateAccount.Q");
    when(jmsMessage.getJMSDestination()).thenReturn(destination);

    // When
    Envelope envelope = Mappers.toEnvelope(messageBody, jmsMessage);

    // Then
    assertNotNull(envelope);
    assertEquals("UpdateAccount", envelope.name());
  }

  @Test
  @DisplayName("toEnvelope - should use UnknownCommand when no command name available")
  void testToEnvelopeUnknownCommand() throws JMSException {
    // Given
    String messageBody = "{\"test\": \"data\"}";

    Message jmsMessage = mock(Message.class);
    when(jmsMessage.getPropertyNames()).thenReturn(emptyEnumeration());
    when(jmsMessage.getJMSCorrelationID()).thenReturn(null);
    when(jmsMessage.getJMSReplyTo()).thenReturn(null);
    when(jmsMessage.getJMSDestination()).thenReturn(null);

    // When
    Envelope envelope = Mappers.toEnvelope(messageBody, jmsMessage);

    // Then
    assertNotNull(envelope);
    assertEquals("UnknownCommand", envelope.name());
  }

  @Test
  @DisplayName("toEnvelope - should extract businessKey from payload")
  void testToEnvelopeWithBusinessKeyInPayload() throws JMSException {
    // Given
    String messageBody = "{\"key\": \"BUSINESS-123\", \"data\": \"test\"}";

    Message jmsMessage = mock(Message.class);
    when(jmsMessage.getPropertyNames()).thenReturn(emptyEnumeration());
    when(jmsMessage.getJMSCorrelationID()).thenReturn(null);
    when(jmsMessage.getJMSReplyTo()).thenReturn(null);

    Queue destination = mock(Queue.class);
    when(destination.toString()).thenReturn("queue:///APP.CMD.CreateUser.Q");
    when(jmsMessage.getJMSDestination()).thenReturn(destination);

    // When
    Envelope envelope = Mappers.toEnvelope(messageBody, jmsMessage);

    // Then
    assertNotNull(envelope);
    assertEquals("BUSINESS-123", envelope.key());
  }

  @Test
  @DisplayName("toEnvelope - should extract businessKey from headers")
  void testToEnvelopeWithBusinessKeyInHeaders() throws JMSException {
    // Given
    String messageBody = "{\"data\": \"test\"}";
    String businessKey = "BK-789";

    Message jmsMessage = mock(Message.class);
    Enumeration<String> propertyNames =
        new Enumeration<String>() {
          private String[] names = {"businessKey"};
          private int index = 0;

          @Override
          public boolean hasMoreElements() {
            return index < names.length;
          }

          @Override
          public String nextElement() {
            return names[index++];
          }
        };
    when(jmsMessage.getPropertyNames()).thenReturn(propertyNames);
    when(jmsMessage.getStringProperty("businessKey")).thenReturn(businessKey);
    when(jmsMessage.getJMSCorrelationID()).thenReturn(null);
    when(jmsMessage.getJMSReplyTo()).thenReturn(null);

    Queue destination = mock(Queue.class);
    when(destination.toString()).thenReturn("queue:///APP.CMD.CreateUser.Q");
    when(jmsMessage.getJMSDestination()).thenReturn(destination);

    // When
    Envelope envelope = Mappers.toEnvelope(messageBody, jmsMessage);

    // Then
    assertNotNull(envelope);
    assertEquals(businessKey, envelope.key());
  }

  @Test
  @DisplayName("toEnvelope - should handle invalid JSON gracefully")
  void testToEnvelopeInvalidJson() throws JMSException {
    // Given
    String messageBody = "not valid json {{{";

    Message jmsMessage = mock(Message.class);
    when(jmsMessage.getPropertyNames()).thenReturn(emptyEnumeration());
    Queue destination = mock(Queue.class);
    when(destination.toString()).thenReturn("queue:///APP.CMD.SomeCommand.Q");
    when(jmsMessage.getJMSDestination()).thenReturn(destination);

    // When
    Envelope envelope = Mappers.toEnvelope(messageBody, jmsMessage);

    // Then
    assertNotNull(envelope);
    assertEquals("SomeCommand", envelope.name());
    assertEquals(messageBody, envelope.payload());
    assertNotNull(envelope.messageId());
    assertEquals(envelope.commandId().toString(), envelope.key());
  }

  @Test
  @DisplayName("toEnvelope - should extract multiple custom properties")
  void testToEnvelopeWithMultipleProperties() throws JMSException {
    // Given
    String messageBody = "{\"test\": \"data\"}";

    Message jmsMessage = mock(Message.class);
    Enumeration<String> propertyNames =
        new Enumeration<String>() {
          private String[] names = {"prop1", "prop2", "prop3"};
          private int index = 0;

          @Override
          public boolean hasMoreElements() {
            return index < names.length;
          }

          @Override
          public String nextElement() {
            return names[index++];
          }
        };
    when(jmsMessage.getPropertyNames()).thenReturn(propertyNames);
    when(jmsMessage.getStringProperty("prop1")).thenReturn("value1");
    when(jmsMessage.getStringProperty("prop2")).thenReturn("value2");
    when(jmsMessage.getStringProperty("prop3")).thenReturn("value3");
    when(jmsMessage.getJMSCorrelationID()).thenReturn(null);
    when(jmsMessage.getJMSReplyTo()).thenReturn(null);

    Queue destination = mock(Queue.class);
    when(destination.toString()).thenReturn("queue:///APP.CMD.Test.Q");
    when(jmsMessage.getJMSDestination()).thenReturn(destination);

    // When
    Envelope envelope = Mappers.toEnvelope(messageBody, jmsMessage);

    // Then
    assertNotNull(envelope);
    assertEquals("value1", envelope.headers().get("prop1"));
    assertEquals("value2", envelope.headers().get("prop2"));
    assertEquals("value3", envelope.headers().get("prop3"));
  }

  private Enumeration<String> emptyEnumeration() {
    return new Enumeration<String>() {
      @Override
      public boolean hasMoreElements() {
        return false;
      }

      @Override
      public String nextElement() {
        return null;
      }
    };
  }
}
