package com.acme.reliable.persistence.jdbc.process;

import static org.assertj.core.api.Assertions.*;

import com.acme.reliable.persistence.jdbc.H2ProcessRepository;
import com.acme.reliable.persistence.jdbc.H2RepositoryFaultyTestBase;
import com.acme.reliable.process.ProcessEvent;
import com.acme.reliable.process.ProcessInstance;
import com.acme.reliable.process.ProcessStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exception handling tests for H2ProcessRepository.
 * Tests catch blocks by operating on a database with no tables.
 */
class H2ProcessRepositoryExceptionTests extends H2RepositoryFaultyTestBase {

  private H2ProcessRepository repository;

  private void setupRepository() {
    repository = new H2ProcessRepository(getDataSource());
  }

  /**
   * Creates a test ProcessInstance for insert/update testing.
   */
  private ProcessInstance createTestProcessInstance() {
    return new ProcessInstance(
        UUID.randomUUID(),
        "TestProcess",
        "test-key",
        ProcessStatus.NEW,
        "Step1",
        Map.of("data", "value"),
        0,
        Instant.now(),
        Instant.now());
  }

  @Nested
  @DisplayName("Query Exception Handling")
  class QueryExceptionTests {

    @Test
    @DisplayName("findById should throw exception when table doesn't exist")
    void testFindByIdTableNotFound() {
      setupRepository();

      assertThatThrownBy(() -> repository.findById(UUID.randomUUID()))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("findByBusinessKey should throw exception when table doesn't exist")
    void testFindByBusinessKeyTableNotFound() {
      setupRepository();

      assertThatThrownBy(() -> repository.findByBusinessKey("TestProcess", "key"))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("findByStatus should throw exception when table doesn't exist")
    void testFindByStatusTableNotFound() {
      setupRepository();

      assertThatThrownBy(() -> repository.findByStatus(ProcessStatus.NEW, 10))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("findByTypeAndStatus should throw exception when table doesn't exist")
    void testFindByTypeAndStatusTableNotFound() {
      setupRepository();

      assertThatThrownBy(
          () -> repository.findByTypeAndStatus("TestProcess", ProcessStatus.NEW, 10))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("getLog should throw exception when table doesn't exist")
    void testGetLogTableNotFound() {
      setupRepository();

      assertThatThrownBy(() -> repository.getLog(UUID.randomUUID()))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("getLog with limit should throw exception when table doesn't exist")
    void testGetLogWithLimitTableNotFound() {
      setupRepository();

      assertThatThrownBy(() -> repository.getLog(UUID.randomUUID(), 10))
          .isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("Write Exception Handling")
  class WriteExceptionTests {

    @Test
    @DisplayName("insert should throw exception when table doesn't exist")
    void testInsertTableNotFound() {
      setupRepository();
      ProcessInstance processInstance = createTestProcessInstance();
      ProcessEvent event = new ProcessEvent.ProcessStarted(
          "TestProcess", "test-key", Map.of("data", "value"));

      assertThatThrownBy(() -> repository.insert(processInstance, event))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Permanent database error during insert process instance");
    }

    @Test
    @DisplayName("update should throw exception when table doesn't exist")
    void testUpdateTableNotFound() {
      setupRepository();
      ProcessInstance processInstance = createTestProcessInstance();
      ProcessEvent event = new ProcessEvent.StepCompleted(
          "Step1", UUID.randomUUID().toString(), Map.of("result", "data"));

      assertThatThrownBy(() -> repository.update(processInstance, event))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Permanent database error during update process instance");
    }
  }
}
