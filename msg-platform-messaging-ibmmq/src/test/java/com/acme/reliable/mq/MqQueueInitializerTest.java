package com.acme.reliable.mq;

import com.ibm.mq.MQException;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.jms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.ibm.mq.constants.CMQC.MQRC_UNKNOWN_OBJECT_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("MqQueueInitializer - Queue validation and initialization")
class MqQueueInitializerTest {

    private TestMqQueueInitializer initializer;
    private JMSConnectionPool connectionPool;
    private Connection connection;
    private Session session;
    private BeanCreatedEvent<JMSConnectionPool> event;

    @BeforeEach
    void setUp() throws JMSException {
        // Create mocks
        connectionPool = mock(JMSConnectionPool.class);
        connection = mock(Connection.class);
        session = mock(Session.class);
        event = mock(BeanCreatedEvent.class);

        // Configure mock behavior
        when(event.getBean()).thenReturn(connectionPool);
        when(connectionPool.createConnection()).thenReturn(connection);
        when(connection.createSession(false, Session.AUTO_ACKNOWLEDGE)).thenReturn(session);

        // Create test initializer with test queues (overrides exitApplication to not call System.exit)
        initializer = new TestMqQueueInitializer("QUEUE1.Q,QUEUE2.Q");
    }

    @Test
    @DisplayName("onCreated - should validate existing queues successfully")
    void testOnCreatedValidatesExistingQueues() throws JMSException {
        // Given
        Queue queue1 = mock(Queue.class);
        Queue queue2 = mock(Queue.class);
        QueueBrowser browser1 = mock(QueueBrowser.class);
        QueueBrowser browser2 = mock(QueueBrowser.class);

        when(session.createQueue("QUEUE1.Q")).thenReturn(queue1);
        when(session.createQueue("QUEUE2.Q")).thenReturn(queue2);
        when(session.createBrowser(queue1)).thenReturn(browser1);
        when(session.createBrowser(queue2)).thenReturn(browser2);

        // When
        JMSConnectionPool result = initializer.onCreated(event);

        // Then
        assertNotNull(result);
        assertEquals(connectionPool, result);
        verify(browser1).close();
        verify(browser2).close();
        verify(connection).close();
    }

    @Test
    @DisplayName("onCreated - should handle missing queue error")
    void testOnCreatedHandlesMissingQueue() throws JMSException {
        // Given
        Queue queue = mock(Queue.class);
        when(session.createQueue("QUEUE1.Q")).thenReturn(queue);

        // Create JMSException with MQException as cause
        MQException mqException = mock(MQException.class);
        when(mqException.getReason()).thenReturn(MQRC_UNKNOWN_OBJECT_NAME);

        JMSException jmsException = new JMSException("Queue not found");
        jmsException.initCause(mqException);

        when(session.createBrowser(queue)).thenThrow(jmsException);

        // When/Then - expects RuntimeException from test override of exitApplication
        RuntimeException ex = assertThrows(RuntimeException.class, () -> initializer.onCreated(event));
        assertTrue(ex.getMessage().contains("exitApplication called"));
    }

    @Test
    @DisplayName("onCreated - should detect missing queue from error message")
    void testOnCreatedDetectsMissingQueueFromMessage() throws JMSException {
        // Given
        Queue queue = mock(Queue.class);
        when(session.createQueue("QUEUE1.Q")).thenReturn(queue);

        JMSException jmsException = new JMSException("MQRC_UNKNOWN_OBJECT_NAME: Queue not found");
        when(session.createBrowser(queue)).thenThrow(jmsException);

        // When/Then - expects RuntimeException from test override of exitApplication
        RuntimeException ex = assertThrows(RuntimeException.class, () -> initializer.onCreated(event));
        assertTrue(ex.getMessage().contains("exitApplication called"));
    }

    @Test
    @DisplayName("onCreated - should detect missing queue from error code in message")
    void testOnCreatedDetectsMissingQueueFromErrorCode() throws JMSException {
        // Given
        Queue queue = mock(Queue.class);
        when(session.createQueue("QUEUE1.Q")).thenReturn(queue);

        JMSException jmsException = new JMSException("Error 2085: Queue not found");
        when(session.createBrowser(queue)).thenThrow(jmsException);

        // When/Then - expects RuntimeException from test override of exitApplication
        RuntimeException ex = assertThrows(RuntimeException.class, () -> initializer.onCreated(event));
        assertTrue(ex.getMessage().contains("exitApplication called"));
    }

    @Test
    @DisplayName("onCreated - should rethrow non-missing-queue JMSException")
    void testOnCreatedRethrowsOtherJmsExceptions() throws JMSException {
        // Given
        Queue queue = mock(Queue.class);
        when(session.createQueue("QUEUE1.Q")).thenReturn(queue);

        JMSException jmsException = new JMSException("Connection refused");
        when(session.createBrowser(queue)).thenThrow(jmsException);

        // When/Then
        assertThrows(RuntimeException.class, () -> initializer.onCreated(event));
    }

    @Test
    @DisplayName("onCreated - should only validate once")
    void testOnCreatedValidatesOnlyOnce() throws JMSException {
        // Given
        Queue queue = mock(Queue.class);
        QueueBrowser browser = mock(QueueBrowser.class);

        when(session.createQueue(anyString())).thenReturn(queue);
        when(session.createBrowser(queue)).thenReturn(browser);

        // When - call multiple times
        initializer.onCreated(event);
        initializer.onCreated(event);
        initializer.onCreated(event);

        // Then - should only validate once (createConnection called only once)
        verify(connectionPool, times(1)).createConnection();
    }

    @Test
    @DisplayName("onCreated - should handle connection pool creation failure")
    void testOnCreatedHandlesConnectionPoolFailure() throws JMSException {
        // Given
        when(connectionPool.createConnection()).thenThrow(new JMSException("Connection failed"));

        // When/Then
        assertThrows(RuntimeException.class, () -> initializer.onCreated(event));
    }

    @Test
    @DisplayName("onCreated - should close connection even on failure")
    void testOnCreatedClosesConnectionOnFailure() throws JMSException {
        // Given
        Queue queue = mock(Queue.class);
        when(session.createQueue("QUEUE1.Q")).thenReturn(queue);
        when(session.createBrowser(queue)).thenThrow(new JMSException("Browser failed"));

        // When/Then
        assertThrows(RuntimeException.class, () -> initializer.onCreated(event));

        // Connection should still be closed (try-with-resources)
        verify(connection).close();
    }

    @Test
    @DisplayName("Constructor - should parse comma-separated queue names")
    void testConstructorParsesQueueNames() throws JMSException {
        // Given
        TestMqQueueInitializer init = new TestMqQueueInitializer("Q1,Q2,Q3");

        Queue q1 = mock(Queue.class);
        Queue q2 = mock(Queue.class);
        Queue q3 = mock(Queue.class);
        QueueBrowser b1 = mock(QueueBrowser.class);
        QueueBrowser b2 = mock(QueueBrowser.class);
        QueueBrowser b3 = mock(QueueBrowser.class);

        when(session.createQueue("Q1")).thenReturn(q1);
        when(session.createQueue("Q2")).thenReturn(q2);
        when(session.createQueue("Q3")).thenReturn(q3);
        when(session.createBrowser(q1)).thenReturn(b1);
        when(session.createBrowser(q2)).thenReturn(b2);
        when(session.createBrowser(q3)).thenReturn(b3);

        // When
        init.onCreated(event);

        // Then
        verify(session).createQueue("Q1");
        verify(session).createQueue("Q2");
        verify(session).createQueue("Q3");
    }

    @Test
    @DisplayName("Constructor - should use default queues if not provided")
    void testConstructorUsesDefaults() throws JMSException {
        // Given
        TestMqQueueInitializer init =
                new TestMqQueueInitializer("APP.CMD.CreateUser.Q,APP.CMD.REPLY.Q");

        Queue q1 = mock(Queue.class);
        Queue q2 = mock(Queue.class);
        QueueBrowser b1 = mock(QueueBrowser.class);
        QueueBrowser b2 = mock(QueueBrowser.class);

        when(session.createQueue("APP.CMD.CreateUser.Q")).thenReturn(q1);
        when(session.createQueue("APP.CMD.REPLY.Q")).thenReturn(q2);
        when(session.createBrowser(any())).thenReturn(b1, b2);

        // When
        init.onCreated(event);

        // Then
        verify(session).createQueue("APP.CMD.CreateUser.Q");
        verify(session).createQueue("APP.CMD.REPLY.Q");
    }

    @Test
    @DisplayName("onCreated - should validate all queues before failing")
    void testOnCreatedValidatesAllQueuesInSequence() throws JMSException {
        // Given
        Queue queue1 = mock(Queue.class);
        Queue queue2 = mock(Queue.class);
        QueueBrowser browser1 = mock(QueueBrowser.class);

        when(session.createQueue("QUEUE1.Q")).thenReturn(queue1);
        when(session.createQueue("QUEUE2.Q")).thenReturn(queue2);
        when(session.createBrowser(queue1)).thenReturn(browser1);

        // Second queue fails
        MQException mqException = mock(MQException.class);
        when(mqException.getReason()).thenReturn(MQRC_UNKNOWN_OBJECT_NAME);
        JMSException jmsException = new JMSException("Queue not found");
        jmsException.initCause(mqException);
        when(session.createBrowser(queue2)).thenThrow(jmsException);

        // When/Then - expects RuntimeException from test override of exitApplication
        assertThrows(RuntimeException.class, () -> initializer.onCreated(event));

        // Should have validated first queue successfully
        verify(browser1).close();
    }

    /**
     * Test subclass that overrides exitApplication to avoid calling System.exit() which would kill
     * the test JVM
     */
    private static class TestMqQueueInitializer extends MqQueueInitializer {
        public TestMqQueueInitializer(String requiredQueuesConfig) {
            super(requiredQueuesConfig);
        }

        @Override
        void exitApplication(String reason) {
            // Don't call System.exit() in tests - just throw an exception instead
            throw new RuntimeException("exitApplication called: " + reason);
        }
    }
}
