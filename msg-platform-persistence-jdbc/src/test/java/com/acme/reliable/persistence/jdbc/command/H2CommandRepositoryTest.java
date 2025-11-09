package com.acme.reliable.persistence.jdbc.command;

import static org.assertj.core.api.Assertions.*;

import com.acme.reliable.domain.Command;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Comprehensive H2-based integration tests for JDBC CommandRepository implementation.
 * Tests all CRUD operations and validates complete field mapping.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class H2CommandRepositoryTest {

  private static HikariDataSource dataSource;
  private H2CommandRepository repository;

  @BeforeAll
  void setupSchema() throws Exception {
    // Create HikariCP DataSource for H2
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    config.setDriverClassName("org.h2.Driver");
    config.setUsername("sa");
    config.setPassword("");
    config.setMaximumPoolSize(5);

    dataSource = new HikariDataSource(config);

    // Initialize schema using Flyway migrations from project-level platform-specific SQL scripts
    Flyway flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations("filesystem:/Users/igormusic/code/messaging-platform/migrations/reliable/h2")
        .load();
    flyway.migrate();
  }

  @AfterAll
  void tearDown() {
    if (dataSource != null) {
      dataSource.close();
    }
  }

  @BeforeEach
  void setUp() throws Exception {
    repository = new H2CommandRepository(dataSource);

    // Clean up data before each test
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM command");
    }
  }

  @Nested
  @DisplayName("Insert and Field Validation Tests")
  class InsertAndFieldValidationTests {

    @Test
    @DisplayName("insertPending should insert command with all fields and retrieve correctly")
    void testInsertPendingWithFullFieldValidation() {
      // Given - Create a command with all fields populated
      UUID commandId = UUID.randomUUID();
      String commandName = "CreateUser";
      String businessKey = "user-123";
      String payload = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"email\":\"john.doe@example.com\"}";
      String idempotencyKey = "idempotency-" + UUID.randomUUID();
      String reply = "{\"replyTo\":\"TEST.REPLY.Q\",\"correlationId\":\"" + UUID.randomUUID() + "\"}";

      // When - Insert the command
      repository.insertPending(commandId, commandName, businessKey, payload, idempotencyKey, reply);

      // Then - Retrieve and validate all fields
      Optional<Command> retrieved = repository.findById(commandId);

      assertThat(retrieved).isPresent();
      Command command = retrieved.get();

      // Validate all fields are correctly mapped
      assertThat(command.getId()).isEqualTo(commandId);
      assertThat(command.getName()).isEqualTo(commandName);
      assertThat(command.getBusinessKey()).isEqualTo(businessKey);
      assertThat(command.getPayload()).isEqualTo(payload);
      assertThat(command.getIdempotencyKey()).isEqualTo(idempotencyKey);
      assertThat(command.getStatus()).isEqualTo("PENDING");
      assertThat(command.getRetries()).isEqualTo(0);
      assertThat(command.getReply()).isEqualTo(reply);

      // Validate timestamp fields
      assertThat(command.getRequestedAt()).isNotNull();
      assertThat(command.getRequestedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));

      // Validate optional fields that should be null initially
      assertThat(command.getUpdatedAt()).isNull();
      assertThat(command.getProcessingLeaseUntil()).isNull();
      assertThat(command.getLastError()).isNull();
    }

    @Test
    @DisplayName("insertPending should create command with PENDING status")
    void testInsertPendingCreatesCommandWithPendingStatus() {
      // Given
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "idempotency-" + UUID.randomUUID();

      // When
      repository.insertPending(
          commandId,
          "TestCommand",
          "test-key",
          "{}",
          idempotencyKey,
          "{}"
      );

      // Then
      Optional<Command> command = repository.findById(commandId);
      assertThat(command).isPresent();
      assertThat(command.get().getStatus()).isEqualTo("PENDING");
      assertThat(command.get().getRetries()).isEqualTo(0);
    }

    @Test
    @DisplayName("findById should return empty Optional for non-existent command")
    void testFindByIdReturnsEmptyForNonExistentCommand() {
      // Given
      UUID nonExistentId = UUID.randomUUID();

      // When
      Optional<Command> result = repository.findById(nonExistentId);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("insertPending should handle empty payload and reply")
    void testInsertPendingWithEmptyPayloadAndReply() {
      // Given
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "idempotency-" + UUID.randomUUID();

      // When
      repository.insertPending(
          commandId,
          "EmptyCommand",
          "empty-key",
          "{}",
          idempotencyKey,
          "{}"
      );

      // Then
      Optional<Command> command = repository.findById(commandId);
      assertThat(command).isPresent();
      assertThat(command.get().getPayload()).isEqualTo("{}");
      assertThat(command.get().getReply()).isEqualTo("{}");
    }
  }

  @Nested
  @DisplayName("Update Operations Tests")
  class UpdateOperationsTests {

    @Test
    @DisplayName("updateToRunning should update status and set processing lease")
    void testUpdateToRunning() {
      // Given - Insert a pending command
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "idempotency-" + UUID.randomUUID();
      repository.insertPending(
          commandId,
          "TestCommand",
          "test-key",
          "{}",
          idempotencyKey,
          "{}"
      );

      // When - Update to RUNNING with lease
      Timestamp lease = Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES));
      repository.updateToRunning(commandId, lease);

      // Then - Verify status and lease are updated
      Optional<Command> command = repository.findById(commandId);
      assertThat(command).isPresent();
      assertThat(command.get().getStatus()).isEqualTo("RUNNING");
      assertThat(command.get().getProcessingLeaseUntil()).isNotNull();
      assertThat(command.get().getProcessingLeaseUntil())
          .isCloseTo(lease.toInstant(), within(1, ChronoUnit.SECONDS));
      assertThat(command.get().getUpdatedAt()).isNotNull();
      assertThat(command.get().getUpdatedAt())
          .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("updateToSucceeded should update status and updated_at timestamp")
    void testUpdateToSucceeded() {
      // Given - Insert and start a command
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "idempotency-" + UUID.randomUUID();
      repository.insertPending(
          commandId,
          "SuccessCommand",
          "success-key",
          "{}",
          idempotencyKey,
          "{}"
      );
      Timestamp lease = Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES));
      repository.updateToRunning(commandId, lease);

      // When - Update to SUCCEEDED
      repository.updateToSucceeded(commandId);

      // Then - Verify status is updated
      Optional<Command> command = repository.findById(commandId);
      assertThat(command).isPresent();
      assertThat(command.get().getStatus()).isEqualTo("SUCCEEDED");
      assertThat(command.get().getUpdatedAt()).isNotNull();
      assertThat(command.get().getUpdatedAt())
          .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("updateToFailed should update status and set error message")
    void testUpdateToFailed() {
      // Given - Insert and start a command
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "idempotency-" + UUID.randomUUID();
      repository.insertPending(
          commandId,
          "FailCommand",
          "fail-key",
          "{}",
          idempotencyKey,
          "{}"
      );
      Timestamp lease = Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES));
      repository.updateToRunning(commandId, lease);

      // When - Update to FAILED with error
      String errorMessage = "Database connection timeout";
      repository.updateToFailed(commandId, errorMessage);

      // Then - Verify status and error are set
      Optional<Command> command = repository.findById(commandId);
      assertThat(command).isPresent();
      assertThat(command.get().getStatus()).isEqualTo("FAILED");
      assertThat(command.get().getLastError()).isEqualTo(errorMessage);
      assertThat(command.get().getUpdatedAt()).isNotNull();
      assertThat(command.get().getUpdatedAt())
          .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("incrementRetries should increment retry count and record error")
    void testIncrementRetries() {
      // Given - Insert a command
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "idempotency-" + UUID.randomUUID();
      repository.insertPending(
          commandId,
          "RetryCommand",
          "retry-key",
          "{}",
          idempotencyKey,
          "{}"
      );

      // When - Increment retries multiple times
      String error1 = "First attempt failed";
      repository.incrementRetries(commandId, error1);

      Optional<Command> afterFirstRetry = repository.findById(commandId);
      assertThat(afterFirstRetry).isPresent();
      assertThat(afterFirstRetry.get().getRetries()).isEqualTo(1);
      assertThat(afterFirstRetry.get().getLastError()).isEqualTo(error1);

      String error2 = "Second attempt failed";
      repository.incrementRetries(commandId, error2);

      Optional<Command> afterSecondRetry = repository.findById(commandId);
      assertThat(afterSecondRetry).isPresent();
      assertThat(afterSecondRetry.get().getRetries()).isEqualTo(2);
      assertThat(afterSecondRetry.get().getLastError()).isEqualTo(error2);
      assertThat(afterSecondRetry.get().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateToTimedOut should update status and record timeout reason")
    void testUpdateToTimedOut() {
      // Given - Insert and start a command
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "idempotency-" + UUID.randomUUID();
      repository.insertPending(
          commandId,
          "TimeoutCommand",
          "timeout-key",
          "{}",
          idempotencyKey,
          "{}"
      );
      Timestamp lease = Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES));
      repository.updateToRunning(commandId, lease);

      // When - Update to TIMED_OUT
      String timeoutReason = "Processing exceeded maximum duration of 5 minutes";
      repository.updateToTimedOut(commandId, timeoutReason);

      // Then - Verify status and reason are set
      Optional<Command> command = repository.findById(commandId);
      assertThat(command).isPresent();
      assertThat(command.get().getStatus()).isEqualTo("TIMED_OUT");
      assertThat(command.get().getLastError()).isEqualTo(timeoutReason);
      assertThat(command.get().getUpdatedAt()).isNotNull();
      assertThat(command.get().getUpdatedAt())
          .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }
  }

  @Nested
  @DisplayName("Semantic and Field Preservation Tests")
  class SemanticAndFieldPreservationTests {

    @Test
    @DisplayName("multiple updates should preserve previously set fields")
    void testMultipleUpdatesPreservePreviousFields() {
      // Given - Insert a command with all fields
      UUID commandId = UUID.randomUUID();
      String commandName = "ComplexCommand";
      String businessKey = "complex-123";
      String payload = "{\"data\":\"important\"}";
      String idempotencyKey = "idempotency-" + UUID.randomUUID();
      String reply = "{\"replyTo\":\"REPLY.Q\"}";

      repository.insertPending(commandId, commandName, businessKey, payload, idempotencyKey, reply);

      // When - Perform multiple updates
      Timestamp lease = Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES));
      repository.updateToRunning(commandId, lease);

      // Then - Verify original fields are preserved after updateToRunning
      Optional<Command> afterRunning = repository.findById(commandId);
      assertThat(afterRunning).isPresent();
      assertThat(afterRunning.get().getName()).isEqualTo(commandName);
      assertThat(afterRunning.get().getBusinessKey()).isEqualTo(businessKey);
      assertThat(afterRunning.get().getPayload()).isEqualTo(payload);
      assertThat(afterRunning.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
      assertThat(afterRunning.get().getReply()).isEqualTo(reply);
      assertThat(afterRunning.get().getRetries()).isEqualTo(0);

      // When - Increment retries
      repository.incrementRetries(commandId, "Temporary error");

      // Then - Verify fields are still preserved
      Optional<Command> afterRetry = repository.findById(commandId);
      assertThat(afterRetry).isPresent();
      assertThat(afterRetry.get().getName()).isEqualTo(commandName);
      assertThat(afterRetry.get().getBusinessKey()).isEqualTo(businessKey);
      assertThat(afterRetry.get().getPayload()).isEqualTo(payload);
      assertThat(afterRetry.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
      assertThat(afterRetry.get().getReply()).isEqualTo(reply);
      assertThat(afterRetry.get().getStatus()).isEqualTo("RUNNING");
      assertThat(afterRetry.get().getProcessingLeaseUntil()).isNotNull();

      // When - Update to succeeded
      repository.updateToSucceeded(commandId);

      // Then - Verify all fields are still preserved
      Optional<Command> afterSuccess = repository.findById(commandId);
      assertThat(afterSuccess).isPresent();
      assertThat(afterSuccess.get().getName()).isEqualTo(commandName);
      assertThat(afterSuccess.get().getBusinessKey()).isEqualTo(businessKey);
      assertThat(afterSuccess.get().getPayload()).isEqualTo(payload);
      assertThat(afterSuccess.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
      assertThat(afterSuccess.get().getReply()).isEqualTo(reply);
      assertThat(afterSuccess.get().getRetries()).isEqualTo(1);
      assertThat(afterSuccess.get().getProcessingLeaseUntil()).isNotNull();
      assertThat(afterSuccess.get().getLastError()).isEqualTo("Temporary error");
    }

    @Test
    @DisplayName("workflow: pending -> running -> failed preserves all fields")
    void testWorkflowPendingToRunningToFailed() {
      // Given
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "idempotency-" + UUID.randomUUID();
      String payload = "{\"userId\":\"user-456\",\"action\":\"delete\"}";
      String reply = "{\"callback\":\"http://callback.example.com\"}";

      // Insert pending
      repository.insertPending(
          commandId,
          "DeleteUser",
          "user-456",
          payload,
          idempotencyKey,
          reply
      );

      // Update to running
      Timestamp lease = Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES));
      repository.updateToRunning(commandId, lease);

      // Update to failed
      String errorMsg = "User not found in database";
      repository.updateToFailed(commandId, errorMsg);

      // Then - Verify complete workflow
      Optional<Command> finalCommand = repository.findById(commandId);
      assertThat(finalCommand).isPresent();

      Command cmd = finalCommand.get();
      assertThat(cmd.getId()).isEqualTo(commandId);
      assertThat(cmd.getName()).isEqualTo("DeleteUser");
      assertThat(cmd.getBusinessKey()).isEqualTo("user-456");
      assertThat(cmd.getPayload()).isEqualTo(payload);
      assertThat(cmd.getIdempotencyKey()).isEqualTo(idempotencyKey);
      assertThat(cmd.getReply()).isEqualTo(reply);
      assertThat(cmd.getStatus()).isEqualTo("FAILED");
      assertThat(cmd.getRetries()).isEqualTo(0);
      assertThat(cmd.getProcessingLeaseUntil()).isNotNull();
      assertThat(cmd.getLastError()).isEqualTo(errorMsg);
      assertThat(cmd.getRequestedAt()).isNotNull();
      assertThat(cmd.getUpdatedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Idempotency Key Tests")
  class IdempotencyKeyTests {

    @Test
    @DisplayName("existsByIdempotencyKey should return true for existing key")
    void testExistsByIdempotencyKeyReturnsTrueForExistingKey() {
      // Given
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "unique-key-" + UUID.randomUUID();

      repository.insertPending(
          commandId,
          "TestCommand",
          "test-key",
          "{}",
          idempotencyKey,
          "{}"
      );

      // When
      boolean exists = repository.existsByIdempotencyKey(idempotencyKey);

      // Then
      assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByIdempotencyKey should return false for non-existent key")
    void testExistsByIdempotencyKeyReturnsFalseForNonExistentKey() {
      // Given
      String nonExistentKey = "non-existent-" + UUID.randomUUID();

      // When
      boolean exists = repository.existsByIdempotencyKey(nonExistentKey);

      // Then
      assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("idempotency key lookup should work regardless of command status")
    void testIdempotencyKeyLookupWorksForAllStatuses() {
      // Given - Insert command and update through various statuses
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "status-test-" + UUID.randomUUID();

      repository.insertPending(
          commandId,
          "StatusCommand",
          "status-key",
          "{}",
          idempotencyKey,
          "{}"
      );

      // Then - Should exist in PENDING status
      assertThat(repository.existsByIdempotencyKey(idempotencyKey)).isTrue();

      // When - Update to RUNNING
      repository.updateToRunning(commandId, Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES)));

      // Then - Should still exist
      assertThat(repository.existsByIdempotencyKey(idempotencyKey)).isTrue();

      // When - Update to SUCCEEDED
      repository.updateToSucceeded(commandId);

      // Then - Should still exist
      assertThat(repository.existsByIdempotencyKey(idempotencyKey)).isTrue();
    }
  }

  @Nested
  @DisplayName("Edge Cases and Special Scenarios")
  class EdgeCasesTests {

    @Test
    @DisplayName("should handle long payload content")
    void testLongPayloadContent() {
      // Given - Create a large payload
      StringBuilder largePayload = new StringBuilder("{\"data\":\"");
      for (int i = 0; i < 1000; i++) {
        largePayload.append("This is a long string to test large payload handling. ");
      }
      largePayload.append("\"}");

      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "large-payload-" + UUID.randomUUID();

      // When
      repository.insertPending(
          commandId,
          "LargePayloadCommand",
          "large-key",
          largePayload.toString(),
          idempotencyKey,
          "{}"
      );

      // Then
      Optional<Command> command = repository.findById(commandId);
      assertThat(command).isPresent();
      assertThat(command.get().getPayload()).isEqualTo(largePayload.toString());
    }

    @Test
    @DisplayName("should handle long error messages")
    void testLongErrorMessages() {
      // Given
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "error-test-" + UUID.randomUUID();

      repository.insertPending(
          commandId,
          "ErrorCommand",
          "error-key",
          "{}",
          idempotencyKey,
          "{}"
      );

      // When - Set a very long error message
      StringBuilder longError = new StringBuilder("Error: ");
      for (int i = 0; i < 100; i++) {
        longError.append("Stack trace line ").append(i).append(": at com.example.Class.method(Class.java:").append(i).append(") ");
      }

      repository.updateToFailed(commandId, longError.toString());

      // Then
      Optional<Command> command = repository.findById(commandId);
      assertThat(command).isPresent();
      assertThat(command.get().getLastError()).isEqualTo(longError.toString());
    }

    @Test
    @DisplayName("should handle special characters in fields")
    void testSpecialCharactersInFields() {
      // Given
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "special-chars-" + UUID.randomUUID();
      String specialPayload = "{\"name\":\"Test's \\\"quoted\\\" value\",\"emoji\":\"😀🎉\",\"unicode\":\"Ñoño\"}";
      String specialReply = "{\"message\":\"Reply with 'quotes' and \\\"escapes\\\"\"}";

      // When
      repository.insertPending(
          commandId,
          "SpecialCommand",
          "special-key",
          specialPayload,
          idempotencyKey,
          specialReply
      );

      // Then
      Optional<Command> command = repository.findById(commandId);
      assertThat(command).isPresent();
      assertThat(command.get().getPayload()).isEqualTo(specialPayload);
      assertThat(command.get().getReply()).isEqualTo(specialReply);
    }

    @Test
    @DisplayName("should handle null processing_lease_until correctly")
    void testNullProcessingLeaseUntil() {
      // Given - Command that never entered RUNNING state
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "no-lease-" + UUID.randomUUID();

      repository.insertPending(
          commandId,
          "NoLeaseCommand",
          "no-lease-key",
          "{}",
          idempotencyKey,
          "{}"
      );

      // When - Directly update to FAILED without going through RUNNING
      repository.updateToFailed(commandId, "Failed before running");

      // Then
      Optional<Command> command = repository.findById(commandId);
      assertThat(command).isPresent();
      assertThat(command.get().getProcessingLeaseUntil()).isNull();
      assertThat(command.get().getStatus()).isEqualTo("FAILED");
      assertThat(command.get().getLastError()).isEqualTo("Failed before running");
    }
  }

  @Nested
  @DisplayName("Full Lifecycle Integration Tests")
  class FullLifecycleTests {

    @Test
    @DisplayName("complete success workflow with all state transitions")
    void testCompleteSuccessWorkflow() {
      // Given - Initial command
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "workflow-success-" + UUID.randomUUID();
      String payload = "{\"orderId\":\"ORDER-123\",\"amount\":99.99}";
      String reply = "{\"webhook\":\"https://api.example.com/callback\"}";

      // Step 1: Insert pending
      repository.insertPending(
          commandId,
          "ProcessOrder",
          "ORDER-123",
          payload,
          idempotencyKey,
          reply
      );

      Command pending = repository.findById(commandId).orElseThrow();
      assertThat(pending.getStatus()).isEqualTo("PENDING");
      assertThat(pending.getRetries()).isEqualTo(0);
      assertThat(pending.getProcessingLeaseUntil()).isNull();
      assertThat(pending.getLastError()).isNull();

      // Step 2: Start processing
      Timestamp lease = Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES));
      repository.updateToRunning(commandId, lease);

      Command running = repository.findById(commandId).orElseThrow();
      assertThat(running.getStatus()).isEqualTo("RUNNING");
      assertThat(running.getProcessingLeaseUntil()).isNotNull();

      // Step 3: Complete successfully
      repository.updateToSucceeded(commandId);

      Command succeeded = repository.findById(commandId).orElseThrow();
      assertThat(succeeded.getStatus()).isEqualTo("SUCCEEDED");
      assertThat(succeeded.getName()).isEqualTo("ProcessOrder");
      assertThat(succeeded.getBusinessKey()).isEqualTo("ORDER-123");
      assertThat(succeeded.getPayload()).isEqualTo(payload);
      assertThat(succeeded.getReply()).isEqualTo(reply);
    }

    @Test
    @DisplayName("complete retry workflow with eventual success")
    void testCompleteRetryWorkflow() {
      // Given
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "workflow-retry-" + UUID.randomUUID();

      repository.insertPending(
          commandId,
          "RetryableCommand",
          "retry-123",
          "{}",
          idempotencyKey,
          "{}"
      );

      // Step 1: First attempt - increment retries
      repository.incrementRetries(commandId, "Attempt 1 failed - network timeout");
      Command afterRetry1 = repository.findById(commandId).orElseThrow();
      assertThat(afterRetry1.getRetries()).isEqualTo(1);
      assertThat(afterRetry1.getLastError()).isEqualTo("Attempt 1 failed - network timeout");

      // Step 2: Second attempt - increment retries again
      repository.incrementRetries(commandId, "Attempt 2 failed - service unavailable");
      Command afterRetry2 = repository.findById(commandId).orElseThrow();
      assertThat(afterRetry2.getRetries()).isEqualTo(2);
      assertThat(afterRetry2.getLastError()).isEqualTo("Attempt 2 failed - service unavailable");

      // Step 3: Third attempt - success
      Timestamp lease = Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES));
      repository.updateToRunning(commandId, lease);
      repository.updateToSucceeded(commandId);

      Command succeeded = repository.findById(commandId).orElseThrow();
      assertThat(succeeded.getStatus()).isEqualTo("SUCCEEDED");
      assertThat(succeeded.getRetries()).isEqualTo(2);
      assertThat(succeeded.getLastError()).isEqualTo("Attempt 2 failed - service unavailable");
    }

    @Test
    @DisplayName("complete failure workflow with max retries")
    void testCompleteFailureWorkflow() {
      // Given
      UUID commandId = UUID.randomUUID();
      String idempotencyKey = "workflow-fail-" + UUID.randomUUID();

      repository.insertPending(
          commandId,
          "FailingCommand",
          "fail-123",
          "{}",
          idempotencyKey,
          "{}"
      );

      // Simulate multiple retry attempts
      for (int i = 1; i <= 3; i++) {
        repository.incrementRetries(commandId, "Retry attempt " + i + " failed");
      }

      // Start processing for final attempt
      Timestamp lease = Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES));
      repository.updateToRunning(commandId, lease);

      // Final failure
      repository.updateToFailed(commandId, "Maximum retries exceeded - permanent failure");

      Command failed = repository.findById(commandId).orElseThrow();
      assertThat(failed.getStatus()).isEqualTo("FAILED");
      assertThat(failed.getRetries()).isEqualTo(3);
      assertThat(failed.getLastError()).isEqualTo("Maximum retries exceeded - permanent failure");
    }
  }
}
