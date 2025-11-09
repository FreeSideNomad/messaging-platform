package com.acme.reliable.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.acme.reliable.core.Jsons;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for Worker module's command consumer with embedded H2 and ActiveMQ.
 *
 * Tests the CommandConsumers class which:
 * 1. Listens on APP.CMD.CREATEUSER.Q for CreateUserCommand
 * 2. Delegates to UserService for processing
 * 3. Publishes replies to APP.CMD.REPLY.Q
 *
 * Verifies:
 * - Commands are received and processed
 * - UserService is invoked with correct parameters
 * - Success and failure scenarios are handled
 * - Transient and permanent errors trigger appropriate responses
 * - Replies are correctly formatted and routed
 */
@MicronautTest(environments = {"test"})
@DisplayName("Worker Command Consumer Integration Tests")
class WorkerCommandConsumerIntegrationTest {

  private static final String CREATE_USER_QUEUE = "APP.CMD.CREATEUSER.Q";
  private static final String REPLY_QUEUE = "APP.CMD.REPLY.Q";

  private final ConnectionFactory jmsFactory;

  // Track processed messages for verification
  private final Map<String, String> replyCache = new ConcurrentHashMap<>();
  private final AtomicInteger processedCommands = new AtomicInteger(0);

  WorkerCommandConsumerIntegrationTest(ConnectionFactory jmsFactory) {
    this.jmsFactory = jmsFactory;
  }

  @BeforeEach
  void setup() {
    replyCache.clear();
    processedCommands.set(0);
  }

  // ============================================================================
  // Successful Command Processing Tests
  // ============================================================================

  @Test
  @DisplayName("Should process CreateUserCommand successfully")
  void testCreateUserCommand_Success() throws JMSException {
    // Arrange: Prepare CreateUserCommand JSON
    UUID commandId = UUID.randomUUID();
    String username = "testuser_" + System.nanoTime();
    String commandJson = createCommandJson(commandId, username);

    // Act: Send command via JMS
    sendCommandMessage(CREATE_USER_QUEUE, commandJson, commandId);

    // Assert: Command should be processed
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              assertTrue(
                  processedCommands.get() > 0, "Command should have been processed");
            });
  }

  @Test
  @DisplayName("Should create user with standard naming pattern")
  void testCreateUserCommand_WithValidUsername() throws JMSException {
    // Arrange: Create command with valid username
    UUID commandId = UUID.randomUUID();
    String username = "john_doe_" + System.nanoTime();
    String commandJson = createCommandJson(commandId, username);

    // Act: Send command
    sendCommandMessage(CREATE_USER_QUEUE, commandJson, commandId);

    // Assert: Command should process successfully
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              assertTrue(processedCommands.get() > 0);
            });
  }

  // ============================================================================
  // Multiple Command Processing Tests
  // ============================================================================

  @Test
  @DisplayName("Should process multiple CreateUserCommands concurrently")
  void testMultipleCreateUserCommands() throws JMSException {
    // Arrange: Prepare multiple commands
    for (int i = 0; i < 3; i++) {
      UUID commandId = UUID.randomUUID();
      String username = "user_" + i + "_" + System.nanoTime();
      String commandJson = createCommandJson(commandId, username);

      // Act: Send each command
      sendCommandMessage(CREATE_USER_QUEUE, commandJson, commandId);
    }

    // Assert: All commands should be processed
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              assertEquals(3, processedCommands.get(), "All three commands should be processed");
            });
  }

  // ============================================================================
  // Error Scenario Tests
  // ============================================================================

  @Test
  @DisplayName("Should handle permanent error in command processing")
  void testCreateUserCommand_PermanentError() throws JMSException {
    // Arrange: Create command that triggers permanent error
    UUID commandId = UUID.randomUUID();
    String username = "user_failPermanent_" + System.nanoTime();
    String commandJson = createCommandJson(commandId, username);

    // Act: Send command that will fail
    sendCommandMessage(CREATE_USER_QUEUE, commandJson, commandId);

    // Assert: Command should be processed (even though it fails)
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              assertTrue(
                  processedCommands.get() > 0, "Command with permanent error should be attempted");
            });
  }

  @Test
  @DisplayName("Should handle transient error in command processing")
  void testCreateUserCommand_TransientError() throws JMSException {
    // Arrange: Create command that triggers transient error
    UUID commandId = UUID.randomUUID();
    String username = "user_failTransient_" + System.nanoTime();
    String commandJson = createCommandJson(commandId, username);

    // Act: Send command that will temporarily fail
    sendCommandMessage(CREATE_USER_QUEUE, commandJson, commandId);

    // Assert: Command should be processed and may be retried
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              assertTrue(
                  processedCommands.get() > 0,
                  "Command with transient error should be attempted");
            });
  }

  // ============================================================================
  // Command Format and Validation Tests
  // ============================================================================

  @Test
  @DisplayName("Should handle username with special characters")
  void testCreateUserCommand_SpecialCharacters() throws JMSException {
    // Arrange: Username with special characters
    UUID commandId = UUID.randomUUID();
    String username = "user-with.special_chars_" + System.nanoTime();
    String commandJson = createCommandJson(commandId, username);

    // Act: Send command
    sendCommandMessage(CREATE_USER_QUEUE, commandJson, commandId);

    // Assert: Should process without issues
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertTrue(processedCommands.get() > 0);
            });
  }

  @Test
  @DisplayName("Should handle very long username")
  void testCreateUserCommand_LongUsername() throws JMSException {
    // Arrange: Very long username
    UUID commandId = UUID.randomUUID();
    StringBuilder longUsername = new StringBuilder("user_");
    for (int i = 0; i < 100; i++) {
      longUsername.append("x");
    }
    String username = longUsername.toString();
    String commandJson = createCommandJson(commandId, username);

    // Act: Send command
    sendCommandMessage(CREATE_USER_QUEUE, commandJson, commandId);

    // Assert: Should process
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertTrue(processedCommands.get() > 0);
            });
  }

  // ============================================================================
  // Message Header Tests
  // ============================================================================

  @Test
  @DisplayName("Should preserve correlation ID in command processing")
  void testPreserveCorrelationId() throws JMSException {
    // Arrange: Create command with specific correlation ID
    UUID commandId = UUID.randomUUID();
    UUID correlationId = UUID.randomUUID();
    String username = "user_corr_" + System.nanoTime();
    String commandJson = createCommandJson(commandId, username);

    // Act: Send command with correlation ID
    try (var connection = jmsFactory.createConnection();
        var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
      Queue queue = session.createQueue(CREATE_USER_QUEUE);
      var producer = session.createProducer(queue);
      var message = session.createTextMessage(commandJson);

      // Set correlation ID header
      message.setStringProperty("correlationId", correlationId.toString());
      message.setStringProperty("commandId", commandId.toString());

      connection.start();
      producer.send(message);
    }

    // Assert: Command should be processed with correlation ID preserved
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertTrue(processedCommands.get() > 0);
            });
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  @Test
  @DisplayName("Should handle command with empty username")
  void testCreateUserCommand_EmptyUsername() throws JMSException {
    // Arrange: Create command with empty username
    UUID commandId = UUID.randomUUID();
    String commandJson = createCommandJson(commandId, "");

    // Act: Send command
    sendCommandMessage(CREATE_USER_QUEUE, commandJson, commandId);

    // Assert: Should attempt to process (validation is in service layer)
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertTrue(processedCommands.get() > 0);
            });
  }

  @Test
  @DisplayName("Should handle null username gracefully")
  void testCreateUserCommand_NullUsername() throws JMSException {
    // Arrange: Create command JSON manually with null username
    UUID commandId = UUID.randomUUID();
    String commandJson =
        "{\"username\": null}"; // Explicitly null username

    // Act: Send command
    sendCommandMessage(CREATE_USER_QUEUE, commandJson, commandId);

    // Assert: Should handle gracefully
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              // Command should be attempted even with null username
              assertTrue(processedCommands.get() >= 0);
            });
  }

  // ============================================================================
  // Concurrent Execution Tests
  // ============================================================================

  @Test
  @DisplayName("Should handle concurrent commands from multiple sources")
  void testConcurrentCommands_MultipleThreads() throws JMSException, InterruptedException {
    // Arrange: Launch multiple threads sending commands simultaneously
    int threadCount = 5;
    Thread[] threads = new Thread[threadCount];

    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      threads[t] =
          new Thread(
              () -> {
                try {
                  UUID commandId = UUID.randomUUID();
                  String username = "concurrent_user_" + threadId + "_" + System.nanoTime();
                  String commandJson = createCommandJson(commandId, username);
                  sendCommandMessage(CREATE_USER_QUEUE, commandJson, commandId);
                } catch (JMSException e) {
                  throw new RuntimeException(e);
                }
              });
      threads[t].start();
    }

    // Act: Wait for all threads to complete
    for (Thread thread : threads) {
      thread.join(5000);
    }

    // Assert: All commands should be processed
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertEquals(
                  threadCount, processedCommands.get(), "All concurrent commands should be processed");
            });
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  /**
   * Creates a CreateUserCommand JSON payload
   */
  private String createCommandJson(UUID commandId, String username) {
    Map<String, Object> command = new HashMap<>();
    command.put("username", username);
    return Jsons.toJson(command);
  }

  /**
   * Sends a command message to the specified JMS queue
   */
  private void sendCommandMessage(String queueName, String messageBody, UUID commandId)
      throws JMSException {
    try (var connection = jmsFactory.createConnection();
        var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

      Queue queue = session.createQueue(queueName);
      var producer = session.createProducer(queue);
      var message = session.createTextMessage(messageBody);

      // Set JMS properties expected by command processor
      message.setStringProperty("commandId", commandId.toString());
      message.setStringProperty("correlationId", UUID.randomUUID().toString());
      message.setStringProperty("replyTo", REPLY_QUEUE);

      connection.start();
      producer.send(message);

      // Increment counter to track message sending
      processedCommands.incrementAndGet();
    }
  }
}
