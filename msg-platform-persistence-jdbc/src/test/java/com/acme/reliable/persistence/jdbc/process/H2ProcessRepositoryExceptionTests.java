package com.acme.reliable.persistence.jdbc.process;

import static org.assertj.core.api.Assertions.*;

import com.acme.reliable.persistence.jdbc.H2RepositoryFaultyTestBase;
import com.acme.reliable.process.ProcessStatus;
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
}
