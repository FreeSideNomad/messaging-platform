package com.acme.reliable.persistence.jdbc.process;

import static org.assertj.core.api.Assertions.*;

import com.acme.reliable.persistence.jdbc.H2ProcessRepository;
import com.acme.reliable.persistence.jdbc.H2RepositoryTestBase;
import com.acme.reliable.process.ProcessEvent;
import com.acme.reliable.process.ProcessInstance;
import com.acme.reliable.process.ProcessLogEntry;
import com.acme.reliable.process.ProcessStatus;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for H2-based Process Repository.
 * Tests event sourcing, process lifecycle, and query operations using the repository.
 */
class H2ProcessRepositoryTest extends H2RepositoryTestBase {

  private H2ProcessRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = new H2ProcessRepository(dataSource);

    try (Connection conn = dataSource.getConnection()) {
      conn.createStatement().execute("DELETE FROM process_log");
      conn.createStatement().execute("DELETE FROM process_instance");
    }
  }

  @Nested
  @DisplayName("Process Lifecycle Tests")
  class ProcessLifecycleTests {

    @Test
    @DisplayName("should insert process instance with initial state")
    void testInsertProcess() {
      // Given
      UUID processId = UUID.randomUUID();
      String processType = "OrderFulfillment";
      String businessKey = "order-123";
      Map<String, Object> initialData = new HashMap<>();
      initialData.put("amount", 99.99);

      ProcessInstance instance = ProcessInstance.create(
          processId,
          processType,
          businessKey,
          "validate",
          initialData);

      ProcessEvent initialEvent = new ProcessEvent.ProcessStarted(
          processType,
          businessKey,
          initialData);

      // When - insert process using repository
      repository.insert(instance, initialEvent);

      // Then - verify process exists
      Optional<ProcessInstance> found = repository.findById(processId);
      assertThat(found).isPresent();
      assertThat(found.get().processId()).isEqualTo(processId);
      assertThat(found.get().processType()).isEqualTo(processType);
      assertThat(found.get().businessKey()).isEqualTo(businessKey);
      assertThat(found.get().status()).isEqualTo(ProcessStatus.NEW);
      assertThat(found.get().currentStep()).isEqualTo("validate");
    }
  }

  @Nested
  @DisplayName("Event Sourcing Tests")
  class EventSourcingTests {

    @Test
    @DisplayName("should log process events in sequence")
    void testEventLogging() {
      // Given - create process and log events
      UUID processId = UUID.randomUUID();
      String processType = "Order";
      String businessKey = "order-1";
      Map<String, Object> initialData = new HashMap<>();

      ProcessInstance instance = ProcessInstance.create(
          processId,
          processType,
          businessKey,
          "step1",
          initialData);

      ProcessEvent initialEvent = new ProcessEvent.ProcessStarted(
          processType,
          businessKey,
          initialData);

      // When - insert process
      repository.insert(instance, initialEvent);

      // Add more events
      ProcessEvent stepCompleted = new ProcessEvent.StepCompleted(
          "step1",
          "cmd-001",
          initialData);

      ProcessInstance runningInstance = instance.withStatus(ProcessStatus.RUNNING);
      repository.update(runningInstance, stepCompleted);

      ProcessEvent processCompleted = new ProcessEvent.ProcessCompleted("Order completed successfully");
      ProcessInstance completedInstance = runningInstance.withStatus(ProcessStatus.SUCCEEDED);
      repository.update(completedInstance, processCompleted);

      // Then - verify event log contains all events
      List<ProcessLogEntry> logEntries = repository.getLog(processId);
      assertThat(logEntries).isNotEmpty();
      assertThat(logEntries.size()).isGreaterThanOrEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Query Tests")
  class QueryTests {

    @Test
    @DisplayName("should find process by business key")
    void testFindByBusinessKey() {
      // Given - create process
      UUID processId = UUID.randomUUID();
      String processType = "Order";
      String businessKey = "order-999";
      Map<String, Object> initialData = new HashMap<>();

      ProcessInstance instance = ProcessInstance.create(
          processId,
          processType,
          businessKey,
          "step",
          initialData);

      ProcessEvent initialEvent = new ProcessEvent.ProcessStarted(
          processType,
          businessKey,
          initialData);

      // When - insert and query
      repository.insert(instance, initialEvent);

      // Then - find by business key
      Optional<ProcessInstance> found = repository.findByBusinessKey(processType, businessKey);
      assertThat(found).isPresent();
      assertThat(found.get().processId()).isEqualTo(processId);
      assertThat(found.get().businessKey()).isEqualTo(businessKey);
    }

    @Test
    @DisplayName("should find processes by status")
    void testFindByStatus() {
      // Given - create processes with different statuses
      Map<String, Object> initialData = new HashMap<>();
      ProcessEvent initialEvent = new ProcessEvent.ProcessStarted(
          "Order",
          "order-1",
          initialData);

      for (int i = 0; i < 5; i++) {
        UUID processId = UUID.randomUUID();
        ProcessStatus status = i < 3 ? ProcessStatus.RUNNING : ProcessStatus.SUCCEEDED;

        ProcessInstance instance = new ProcessInstance(
            processId,
            "Order",
            "order-" + i,
            status,
            "step",
            initialData,
            0,
            java.time.Instant.now(),
            java.time.Instant.now());

        repository.insert(instance, initialEvent);
      }

      // Then - query by status
      List<ProcessInstance> runningProcesses = repository.findByStatus(ProcessStatus.RUNNING, 10);
      assertThat(runningProcesses).hasSize(3);

      List<ProcessInstance> succeededProcesses = repository.findByStatus(ProcessStatus.SUCCEEDED, 10);
      assertThat(succeededProcesses).hasSize(2);
    }

    @Test
    @DisplayName("should find processes by type and status")
    void testFindByTypeAndStatus() {
      // Given - create processes with different types and statuses
      Map<String, Object> initialData = new HashMap<>();
      ProcessEvent initialEvent = new ProcessEvent.ProcessStarted(
          "Order",
          "order-1",
          initialData);

      for (int i = 0; i < 5; i++) {
        UUID processId = UUID.randomUUID();
        String type = i < 3 ? "Order" : "Payment";
        ProcessStatus status = i < 3 ? ProcessStatus.RUNNING : ProcessStatus.SUCCEEDED;

        ProcessInstance instance = new ProcessInstance(
            processId,
            type,
            type.toLowerCase() + "-" + i,
            status,
            "step",
            initialData,
            0,
            java.time.Instant.now(),
            java.time.Instant.now());

        repository.insert(instance, initialEvent);
      }

      // Then - query by type and status
      List<ProcessInstance> orderInstances = repository.findByTypeAndStatus("Order", ProcessStatus.RUNNING, 10);
      assertThat(orderInstances).hasSize(3);
    }
  }

  @Nested
  @DisplayName("Data Integrity Tests")
  class DataIntegrityTests {

    @Test
    @DisplayName("should preserve JSON data through insert and retrieval")
    void testJsonDataPreservation() {
      // Given
      UUID processId = UUID.randomUUID();
      Map<String, Object> complexData = new HashMap<>();
      complexData.put("orderId", "123");
      complexData.put("qty", 2);
      complexData.put("total", 99.99);

      ProcessInstance instance = ProcessInstance.create(
          processId,
          "Order",
          "order-1",
          "step",
          complexData);

      ProcessEvent initialEvent = new ProcessEvent.ProcessStarted(
          "Order",
          "order-1",
          complexData);

      // When - insert process with complex data
      repository.insert(instance, initialEvent);

      // Then - verify data preserved
      Optional<ProcessInstance> found = repository.findById(processId);
      assertThat(found).isPresent();
      assertThat(found.get().data()).containsEntry("orderId", "123")
          .containsEntry("qty", 2)
          .containsEntry("total", 99.99);
    }

    @Test
    @DisplayName("should update process and log new event")
    void testUpdateProcess() {
      // Given
      UUID processId = UUID.randomUUID();
      Map<String, Object> initialData = new HashMap<>();
      initialData.put("status", "pending");

      ProcessInstance instance = ProcessInstance.create(
          processId,
          "Order",
          "order-update-1",
          "processing",
          initialData);

      ProcessEvent initialEvent = new ProcessEvent.ProcessStarted(
          "Order",
          "order-update-1",
          initialData);

      repository.insert(instance, initialEvent);

      // When - update process
      Map<String, Object> updatedData = new HashMap<>(initialData);
      updatedData.put("status", "completed");

      ProcessInstance updatedInstance = instance.withData(updatedData);
      ProcessEvent updateEvent = new ProcessEvent.StepCompleted("processing", "cmd-001", updatedData);
      repository.update(updatedInstance, updateEvent);

      // Then - verify update
      Optional<ProcessInstance> found = repository.findById(processId);
      assertThat(found).isPresent();
      assertThat(found.get().data()).containsEntry("status", "completed");

      List<ProcessLogEntry> log = repository.getLog(processId);
      assertThat(log).hasSize(2);
    }

    @Test
    @DisplayName("should handle non-existent process gracefully")
    void testFindNonExistentProcess() {
      // Given
      UUID processId = UUID.randomUUID();

      // When & Then
      Optional<ProcessInstance> found = repository.findById(processId);
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should return empty list for non-existent business key")
    void testFindNonExistentBusinessKey() {
      // When
      Optional<ProcessInstance> found = repository.findByBusinessKey("Order", "non-existent");

      // Then
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should handle retries and complex state transitions")
    void testProcessWithMultipleStateTransitions() {
      // Given
      UUID processId = UUID.randomUUID();
      Map<String, Object> initialData = new HashMap<>();
      initialData.put("retryCount", 0);

      ProcessInstance instance = ProcessInstance.create(
          processId,
          "Order",
          "order-state-test",
          "validate",
          initialData);

      ProcessEvent initialEvent = new ProcessEvent.ProcessStarted(
          "Order",
          "order-state-test",
          initialData);

      // When - insert
      repository.insert(instance, initialEvent);

      // Transition 1: NEW -> RUNNING (Step validation)
      ProcessEvent stepStart = new ProcessEvent.StepStarted("validate", "cmd-001");
      ProcessInstance running = instance.withStatus(ProcessStatus.RUNNING).withCurrentStep("validate");
      repository.update(running, stepStart);

      // Transition 2: RUNNING -> RUNNING (Step failed with retry)
      Map<String, Object> dataAfterFailure = new HashMap<>(initialData);
      dataAfterFailure.put("retryCount", 1);
      ProcessEvent stepFailed = new ProcessEvent.StepFailed(
          "validate",
          "cmd-001",
          "Validation error",
          true);
      ProcessInstance withRetry = running.withData(dataAfterFailure).withRetries(1);
      repository.update(withRetry, stepFailed);

      // Transition 3: RUNNING -> SUCCEEDED
      ProcessEvent stepCompleted = new ProcessEvent.StepCompleted("validate", "cmd-001", dataAfterFailure);
      ProcessInstance succeeded = withRetry.withStatus(ProcessStatus.SUCCEEDED);
      repository.update(succeeded, stepCompleted);

      // Then - verify final state and complete event log
      Optional<ProcessInstance> final_instance = repository.findById(processId);
      assertThat(final_instance).isPresent();
      assertThat(final_instance.get().status()).isEqualTo(ProcessStatus.SUCCEEDED);
      assertThat(final_instance.get().retries()).isEqualTo(1);

      List<ProcessLogEntry> log = repository.getLog(processId);
      assertThat(log).hasSize(4); // ProcessStarted, StepStarted, StepFailed, StepCompleted
    }

    @Test
    @DisplayName("should query processes with limit constraint")
    void testFindByStatusWithLimit() {
      // Given - create 10 processes
      Map<String, Object> initialData = new HashMap<>();
      ProcessEvent initialEvent = new ProcessEvent.ProcessStarted(
          "Order",
          "order-1",
          initialData);

      for (int i = 0; i < 10; i++) {
        UUID processId = UUID.randomUUID();
        ProcessInstance instance = new ProcessInstance(
            processId,
            "Order",
            "order-" + i,
            ProcessStatus.RUNNING,
            "step",
            initialData,
            0,
            java.time.Instant.now(),
            java.time.Instant.now());
        repository.insert(instance, initialEvent);
      }

      // When - query with limit of 5
      List<ProcessInstance> limited = repository.findByStatus(ProcessStatus.RUNNING, 5);

      // Then
      assertThat(limited).hasSize(5);
    }
  }

  @Nested
  @DisplayName("Empty Results and Zero Rows Tests")
  class EmptyResultsTests {

    @Test
    @DisplayName("findByTypeAndStatus should return empty list when no processes match")
    void testFindByTypeAndStatusEmptyResults() {
      // When - query for processes that don't exist
      List<ProcessInstance> results = repository.findByTypeAndStatus("NonExistentType", ProcessStatus.RUNNING, 10);

      // Then - should return empty list
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("findByStatus should return empty list when no processes in that status")
    void testFindByStatusEmptyResults() {
      // Given - create a process in RUNNING status
      UUID processId = UUID.randomUUID();
      ProcessInstance instance = ProcessInstance.create(
          processId,
          "Order",
          "order-for-status",
          "step1",
          new HashMap<>());
      ProcessEvent event = new ProcessEvent.ProcessStarted("Order", "order-for-status", new HashMap<>());
      repository.insert(instance, event);

      // When - query for processes in SUCCEEDED status (none exist)
      List<ProcessInstance> results = repository.findByStatus(ProcessStatus.SUCCEEDED, 10);

      // Then - should return empty list
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("update should handle non-existent process (0 rows updated)")
    void testUpdateNonExistentProcess() {
      // Given - a process that doesn't exist
      UUID processId = UUID.randomUUID();
      ProcessInstance nonExistent = new ProcessInstance(
          processId,
          "Order",
          "order-not-exist",
          ProcessStatus.RUNNING,
          "step2",
          new HashMap<>(),
          1,
          java.time.Instant.now(),
          java.time.Instant.now());
      ProcessEvent event = new ProcessEvent.StepCompleted("step1", "result", new HashMap<>());

      // When - update non-existent process (should silently return, 0 rows updated)
      // This should not throw an exception
      repository.update(nonExistent, event);

      // Then - verify the process still doesn't exist
      Optional<ProcessInstance> found = repository.findById(processId);
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByBusinessKey should return empty when not found")
    void testFindByBusinessKeyNotFound() {
      // Given - business key that doesn't exist
      String businessKey = "order-never-created";

      // When - query for non-existent business key
      Optional<ProcessInstance> found = repository.findByBusinessKey("Order", businessKey);

      // Then - should return empty
      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("getLog should return empty list when no log entries exist")
    void testGetLogEmptyList() {
      // Given - a random process ID with no log entries
      UUID processId = UUID.randomUUID();

      // When - query log for non-existent process
      List<ProcessLogEntry> log = repository.getLog(processId);

      // Then - should return empty list
      assertThat(log).isEmpty();
    }

    @Test
    @DisplayName("findById should handle minimal process data")
    void testFindByIdWithMinimalData() {
      // Given - create and insert a process with minimal data
      UUID processId = UUID.randomUUID();
      Map<String, Object> minimalData = new HashMap<>();

      ProcessInstance instance = ProcessInstance.create(
          processId,
          "Payment",
          "payment-123",
          "validate",
          minimalData);

      ProcessEvent event = new ProcessEvent.ProcessStarted("Payment", "payment-123", minimalData);

      // When - insert and retrieve
      repository.insert(instance, event);
      Optional<ProcessInstance> found = repository.findById(processId);

      // Then - should retrieve successfully
      assertThat(found).isPresent();
      assertThat(found.get().data()).isEmpty();
    }
  }
}
