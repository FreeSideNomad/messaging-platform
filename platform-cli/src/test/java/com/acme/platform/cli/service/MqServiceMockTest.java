package com.acme.platform.cli.service;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqServiceMockTest {

    @Mock
    private ConnectionFactory factory;

    @Mock
    private Connection connection;

    @Mock
    private Channel channel;

    private MqService mqService;

    @BeforeEach
    void setUp() throws Exception {
        mqService = MqService.getInstance();

        // Use reflection to inject mock factory and connection
        Field factoryField = MqService.class.getDeclaredField("factory");
        factoryField.setAccessible(true);
        factoryField.set(mqService, factory);

        Field connectionField = MqService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(mqService, connection);
    }

    @Test
    void testListQueues_returnsAllKnownQueues() throws Exception {
        // Arrange
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);

        AMQP.Queue.DeclareOk declareOk1 = mock(AMQP.Queue.DeclareOk.class);
        when(declareOk1.getMessageCount()).thenReturn(10);
        when(declareOk1.getConsumerCount()).thenReturn(2);

        AMQP.Queue.DeclareOk declareOk2 = mock(AMQP.Queue.DeclareOk.class);
        when(declareOk2.getMessageCount()).thenReturn(5);
        when(declareOk2.getConsumerCount()).thenReturn(1);

        AMQP.Queue.DeclareOk declareOk3 = mock(AMQP.Queue.DeclareOk.class);
        when(declareOk3.getMessageCount()).thenReturn(0);
        when(declareOk3.getConsumerCount()).thenReturn(1);

        when(channel.queueDeclarePassive("payment-commands")).thenReturn(declareOk1);
        when(channel.queueDeclarePassive("account-commands")).thenReturn(declareOk2);
        when(channel.queueDeclarePassive("fx-commands")).thenReturn(declareOk3);
        when(channel.queueDeclarePassive("payment-events")).thenThrow(new IOException("Queue not found"));
        when(channel.queueDeclarePassive("account-events")).thenThrow(new IOException("Queue not found"));

        // Act
        List<MqService.QueueInfo> queues = mqService.listQueues();

        // Assert
        assertThat(queues).hasSize(3);

        assertThat(queues.get(0).getName()).isEqualTo("payment-commands");
        assertThat(queues.get(0).getMessageCount()).isEqualTo(10);
        assertThat(queues.get(0).getConsumerCount()).isEqualTo(2);
        assertThat(queues.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(queues.get(0).isHealthy()).isTrue();

        assertThat(queues.get(1).getName()).isEqualTo("account-commands");
        assertThat(queues.get(1).getMessageCount()).isEqualTo(5);
        assertThat(queues.get(1).getConsumerCount()).isEqualTo(1);

        assertThat(queues.get(2).getName()).isEqualTo("fx-commands");
        assertThat(queues.get(2).getMessageCount()).isEqualTo(0);
        assertThat(queues.get(2).getConsumerCount()).isEqualTo(1);

        verify(channel).close();
    }

    @Test
    void testListQueues_withNoQueuesFound_returnsEmptyList() throws Exception {
        // Arrange
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);
        when(channel.queueDeclarePassive(anyString())).thenThrow(new IOException("Queue not found"));

        // Act
        List<MqService.QueueInfo> queues = mqService.listQueues();

        // Assert
        assertThat(queues).isEmpty();
        verify(channel).close();
    }

    @Test
    void testGetQueueStatus_successful() throws Exception {
        // Arrange
        String queueName = "payment-commands";
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);

        AMQP.Queue.DeclareOk declareOk = mock(AMQP.Queue.DeclareOk.class);
        when(declareOk.getMessageCount()).thenReturn(15);
        when(declareOk.getConsumerCount()).thenReturn(3);

        when(channel.queueDeclarePassive(queueName)).thenReturn(declareOk);

        // Act
        MqService.QueueInfo queueInfo = mqService.getQueueStatus(queueName);

        // Assert
        assertThat(queueInfo).isNotNull();
        assertThat(queueInfo.getName()).isEqualTo(queueName);
        assertThat(queueInfo.getMessageCount()).isEqualTo(15);
        assertThat(queueInfo.getConsumerCount()).isEqualTo(3);
        assertThat(queueInfo.getStatus()).isEqualTo("ACTIVE");
        assertThat(queueInfo.isHealthy()).isTrue();

        verify(channel).queueDeclarePassive(queueName);
        verify(channel).close();
    }

    @Test
    void testGetQueueStatus_queueNotFound_throwsException() throws Exception {
        // Arrange
        String queueName = "non-existent-queue";
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);
        when(channel.queueDeclarePassive(queueName)).thenThrow(new IOException("Queue not found"));

        // Act & Assert
        assertThatThrownBy(() -> mqService.getQueueStatus(queueName))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Queue not found");

        verify(channel).close();
    }

    @Test
    void testGetQueueStatus_withZeroConsumers_isUnhealthy() throws Exception {
        // Arrange
        String queueName = "test-queue";
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);

        AMQP.Queue.DeclareOk declareOk = mock(AMQP.Queue.DeclareOk.class);
        when(declareOk.getMessageCount()).thenReturn(100);
        when(declareOk.getConsumerCount()).thenReturn(0);

        when(channel.queueDeclarePassive(queueName)).thenReturn(declareOk);

        // Act
        MqService.QueueInfo queueInfo = mqService.getQueueStatus(queueName);

        // Assert
        assertThat(queueInfo.getConsumerCount()).isEqualTo(0);
        assertThat(queueInfo.isHealthy()).isFalse(); // No consumers = unhealthy
    }

    @Test
    void testTestConnection_successful() throws Exception {
        // Arrange
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);

        // Act & Assert
        assertThatCode(() -> mqService.testConnection()).doesNotThrowAnyException();
        verify(connection).createChannel();
        verify(channel).close();
    }

    @Test
    void testTestConnection_failsWithIOException() throws Exception {
        // Arrange
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenThrow(new IOException("Connection failed"));

        // Act & Assert
        assertThatThrownBy(() -> mqService.testConnection())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Connection failed");
    }


    @Test
    void testClose_closesConnection() throws Exception {
        // Arrange
        when(connection.isOpen()).thenReturn(true);

        // Act
        mqService.close();

        // Assert
        verify(connection).isOpen();
        verify(connection).close();
    }

    @Test
    void testClose_whenAlreadyClosed_doesNothing() throws Exception {
        // Arrange
        when(connection.isOpen()).thenReturn(false);

        // Act
        mqService.close();

        // Assert
        verify(connection).isOpen();
        verify(connection, never()).close();
    }

    @Test
    void testClose_withIOException_handlesGracefully() throws Exception {
        // Arrange
        when(connection.isOpen()).thenReturn(true);
        doThrow(new IOException("Close failed")).when(connection).close();

        // Act & Assert - should not throw exception
        assertThatCode(() -> mqService.close()).doesNotThrowAnyException();
        verify(connection).close();
    }


    @Test
    void testGetConnection_createsNewConnection_whenClosed() throws Exception {
        // Arrange
        when(connection.isOpen()).thenReturn(false, true); // First call false, second true
        when(factory.newConnection()).thenReturn(connection);
        when(connection.createChannel()).thenReturn(channel);

        AMQP.Queue.DeclareOk declareOk = mock(AMQP.Queue.DeclareOk.class);
        when(declareOk.getMessageCount()).thenReturn(0);
        when(declareOk.getConsumerCount()).thenReturn(0);
        when(channel.queueDeclarePassive(anyString())).thenReturn(declareOk);

        // Act
        mqService.listQueues();

        // Assert
        verify(factory).newConnection();
    }

    @Test
    void testListQueues_withMixedResults() throws Exception {
        // Arrange
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);

        AMQP.Queue.DeclareOk declareOk1 = mock(AMQP.Queue.DeclareOk.class);
        when(declareOk1.getMessageCount()).thenReturn(100);
        when(declareOk1.getConsumerCount()).thenReturn(0); // Unhealthy

        AMQP.Queue.DeclareOk declareOk2 = mock(AMQP.Queue.DeclareOk.class);
        when(declareOk2.getMessageCount()).thenReturn(50);
        when(declareOk2.getConsumerCount()).thenReturn(2); // Healthy

        when(channel.queueDeclarePassive("payment-commands")).thenReturn(declareOk1);
        when(channel.queueDeclarePassive("account-commands")).thenReturn(declareOk2);
        when(channel.queueDeclarePassive("fx-commands")).thenThrow(new IOException("Not found"));
        when(channel.queueDeclarePassive("payment-events")).thenThrow(new IOException("Not found"));
        when(channel.queueDeclarePassive("account-events")).thenThrow(new IOException("Not found"));

        // Act
        List<MqService.QueueInfo> queues = mqService.listQueues();

        // Assert
        assertThat(queues).hasSize(2);
        assertThat(queues.get(0).isHealthy()).isFalse(); // No consumers
        assertThat(queues.get(1).isHealthy()).isTrue(); // Has consumers
    }
}
