package com.acme.reliable.persistence.jdbc.inbox;

import static org.assertj.core.api.Assertions.*;

import com.acme.reliable.persistence.jdbc.H2RepositoryFaultyTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exception handling tests for H2InboxRepository.
 * Tests catch blocks by operating on a database with no tables.
 */
class H2InboxRepositoryExceptionTests extends H2RepositoryFaultyTestBase {

  private H2InboxRepository repository;

  private void setupRepository() {
    repository = new H2InboxRepository(getDataSource());
  }

  @Nested
  @DisplayName("Insert Exception Handling")
  class InsertExceptionTests {

    @Test
    @DisplayName("insertIfAbsent should throw exception when table doesn't exist")
    void testInsertIfAbsentTableNotFound() {
      setupRepository();

      assertThatThrownBy(() -> repository.insertIfAbsent("msg-123", "handler-1"))
          .isInstanceOf(RuntimeException.class);
    }
  }
}
