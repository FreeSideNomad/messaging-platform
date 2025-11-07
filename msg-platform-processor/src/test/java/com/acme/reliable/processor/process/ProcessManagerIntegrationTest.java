package com.acme.reliable.processor.process;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.acme.reliable.command.CommandBus;
import com.acme.reliable.command.DomainCommand;
import com.acme.reliable.process.*;
import com.acme.reliable.repository.ProcessRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.util.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration tests for ProcessManager with real database (Testcontainers) */
@MicronautTest(transactional = false, environments = "test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessManagerIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("test")
          .withUsername("test")
          .withPassword("test");

  @Inject ProcessRepository processRepository;

  private ProcessManager processManager;
  private CommandBus mockCommandBus;
  private IntegrationTestProcessConfiguration testDefinition;

  @Override
  public Map<String, String> getProperties() {
    postgres.start();
    Map<String, String> props = new HashMap<>();
    props.put("datasources.default.url", postgres.getJdbcUrl());
    props.put("datasources.default.username", postgres.getUsername());
    props.put("datasources.default.password", postgres.getPassword());
    props.put("datasources.default.driver-class-name", "org.postgresql.Driver");
    props.put("datasources.default.maximum-pool-size", "10");
    props.put("datasources.default.minimum-idle", "2");
    props.put("flyway.datasources.default.enabled", "true");
    props.put("flyway.datasources.default.locations", "classpath:db/migration");
    return props;
  }

  @BeforeAll
  void setupAll() {
    mockCommandBus = mock(CommandBus.class);
    testDefinition = new IntegrationTestProcessConfiguration();

    // Create ProcessManager with mocked CommandBus
    var mockBeanContext = mock(io.micronaut.context.BeanContext.class);
    processManager = new ProcessManager(processRepository, mockCommandBus, mockBeanContext);
    processManager.register(testDefinition);

    when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());
  }

  @BeforeEach
  void setup() {
    reset(mockCommandBus);
    when(mockCommandBus.accept(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID());
  }

  @Test
  @DisplayName("Integration: Start process and verify persistence")
  void testStartProcess_PersistsToDatabase() {
    // Given
    Map<String, Object> initialData = Map.of("customerId", "cust-123", "amount", 100.50);

    // When
    UUID processId =
        processManager.startProcess("IntegrationTestProcess", "test-key-1", initialData);

    // Then
    assertNotNull(processId);

    // Verify process was persisted
    Optional<ProcessInstance> saved = processRepository.findById(processId);
    assertTrue(saved.isPresent());

    ProcessInstance instance = saved.get();
    assertEquals("IntegrationTestProcess", instance.processType());
    assertEquals("test-key-1", instance.businessKey());
    assertEquals("IntegrationStep1", instance.currentStep());
    assertEquals(ProcessStatus.RUNNING, instance.status());
    assertEquals(100.50, instance.data().get("amount"));
    assertEquals("cust-123", instance.data().get("customerId"));
  }

  @Test
  @DisplayName("Integration: Complete multi-step process with data flow")
  void testCompleteProcess_WithDataMerging() {
    // Given
    Map<String, Object> initialData = Map.of("initialValue", "start");

    // When - Start process
    UUID processId =
        processManager.startProcess("IntegrationTestProcess", "test-key-2", initialData);

    // Simulate Step1 completion
    UUID commandId1 = UUID.randomUUID();
    CommandReply reply1 =
        CommandReply.completed(
            commandId1, processId, Map.of("step1Result", "completed", "amount", 200.0));
    processManager.handleReply(processId, commandId1, reply1);

    // Simulate Step2 completion
    UUID commandId2 = UUID.randomUUID();
    CommandReply reply2 =
        CommandReply.completed(
            commandId2, processId, Map.of("step2Result", "done", "finalAmount", 250.0));
    processManager.handleReply(processId, commandId2, reply2);

    // Then - Verify final state
    Optional<ProcessInstance> completed = processRepository.findById(processId);
    assertTrue(completed.isPresent());

    ProcessInstance instance = completed.get();
    assertEquals(ProcessStatus.SUCCEEDED, instance.status());

    // Verify data was merged across all steps
    assertTrue(instance.data().containsKey("initialValue"));
    assertTrue(instance.data().containsKey("step1Result"));
    assertTrue(instance.data().containsKey("step2Result"));
    assertEquals("start", instance.data().get("initialValue"));
    assertEquals("completed", instance.data().get("step1Result"));
    assertEquals("done", instance.data().get("step2Result"));
    assertEquals(250.0, instance.data().get("finalAmount"));

    // Verify process log contains all events
    List<ProcessLogEntry> log = processRepository.getLog(processId);
    assertFalse(log.isEmpty());
    assertTrue(
        log.size() >= 5); // ProcessStarted, StepStarted, StepCompleted (x2), ProcessCompleted
  }

  @Test
  @DisplayName("Integration: Handle process failure with retry")
  void testProcessFailure_WithRetry() {
    // Given
    Map<String, Object> initialData = Map.of("test", "retry");

    // When - Start process
    UUID processId =
        processManager.startProcess("IntegrationTestProcess", "test-key-3", initialData);

    // Simulate Step1 failure (retryable)
    UUID commandId1 = UUID.randomUUID();
    CommandReply reply1 = CommandReply.failed(commandId1, processId, "Temporary timeout error");
    processManager.handleReply(processId, commandId1, reply1);

    // Then - Verify retry was attempted
    Optional<ProcessInstance> retried = processRepository.findById(processId);
    assertTrue(retried.isPresent());

    ProcessInstance instance = retried.get();
    assertEquals(1, instance.retries());
    assertEquals("IntegrationStep1", instance.currentStep()); // Still on IntegrationStep1

    // Verify command was sent twice (initial + retry)
    verify(mockCommandBus, times(2)).accept(eq("IntegrationStep1"), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Integration: Find processes by status")
  void testFindByStatus() {
    // Given - Create multiple processes
    UUID processId1 =
        processManager.startProcess(
            "IntegrationTestProcess", "test-key-4", Map.of("test", "status1"));

    UUID processId2 =
        processManager.startProcess(
            "IntegrationTestProcess", "test-key-5", Map.of("test", "status2"));

    // Complete one process
    UUID commandId1 = UUID.randomUUID();
    CommandReply reply1 = CommandReply.completed(commandId1, processId1, Map.of());
    processManager.handleReply(processId1, commandId1, reply1);

    UUID commandId2 = UUID.randomUUID();
    CommandReply reply2 = CommandReply.completed(commandId2, processId1, Map.of());
    processManager.handleReply(processId1, commandId2, reply2);

    // When - Query by status
    List<ProcessInstance> running = processRepository.findByStatus(ProcessStatus.RUNNING, 10);
    List<ProcessInstance> succeeded = processRepository.findByStatus(ProcessStatus.SUCCEEDED, 10);

    // Then
    assertTrue(running.stream().anyMatch(p -> p.processId().equals(processId2)));
    assertTrue(succeeded.stream().anyMatch(p -> p.processId().equals(processId1)));
  }

  @Test
  @DisplayName("Integration: Find process by business key")
  void testFindByBusinessKey() {
    // Given
    String businessKey = "unique-business-key-" + UUID.randomUUID();
    UUID processId =
        processManager.startProcess(
            "IntegrationTestProcess", businessKey, Map.of("test", "findByKey"));

    // When
    Optional<ProcessInstance> found =
        processRepository.findByBusinessKey("IntegrationTestProcess", businessKey);

    // Then
    assertTrue(found.isPresent());
    assertEquals(processId, found.get().processId());
    assertEquals(businessKey, found.get().businessKey());
  }

  /** Test ProcessConfiguration for integration tests */
  static class IntegrationTestProcessConfiguration implements ProcessConfiguration {
    @Override
    public String getProcessType() {
      return "IntegrationTestProcess";
    }

    @Override
    public Class<? extends DomainCommand> getInitiationCommandType() {
      return IntegrationStep1Command.class;
    }

    @Override
    public Map<String, Object> initializeProcessState(DomainCommand initiationCommand) {
      return Map.of();
    }

    @Override
    public ProcessGraph defineProcess() {
      return ProcessGraphBuilder.process()
          .startWith(IntegrationStep1Command.class)
          .then(IntegrationStep2Command.class)
          .end();
    }

    @Override
    public boolean isRetryable(String step, String error) {
      return error != null && error.contains("timeout");
    }

    @Override
    public int getMaxRetries(String step) {
      return 3;
    }
  }

  // Dummy command classes for testing
  static class IntegrationStep1Command implements DomainCommand {}

  static class IntegrationStep2Command implements DomainCommand {}
}
