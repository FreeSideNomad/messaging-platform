package com.acme.reliable.persistence.jdbc.dlq;

import static org.assertj.core.api.Assertions.*;

import com.acme.reliable.persistence.jdbc.H2RepositoryFaultyTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exception handling tests for H2DlqRepository.
 * Tests catch blocks by operating on a database with no tables.
 */
class H2DlqRepositoryExceptionTests extends H2RepositoryFaultyTestBase {

  private H2DlqRepository repository;

  private void setupRepository() {
    repository = new H2DlqRepository(getDataSource());
  }

  @Nested
  @DisplayName("Insert Exception Handling")
  class InsertExceptionTests {

    @Test
    @DisplayName("insertDlqEntry should throw exception when table doesn't exist")
    void testInsertDlqEntryTableNotFound() {
      setupRepository();

      assertThatThrownBy(
          () ->
              repository.insertDlqEntry(
                  UUID.randomUUID(),
                  "test-command",
                  "business-key",
                  "payload",
                  "FAILED",
                  "TestException",
                  "error message",
                  0,
                  "test-service"))
          .isInstanceOf(RuntimeException.class);
    }
  }
}
