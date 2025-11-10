package com.acme.reliable.persistence.jdbc.command;

import static org.assertj.core.api.Assertions.*;

import com.acme.reliable.persistence.jdbc.H2CommandRepository;
import com.acme.reliable.persistence.jdbc.H2RepositoryFaultyTestBase;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exception handling tests for H2CommandRepository.
 * Tests catch blocks by operating on a database with no tables.
 */
class H2CommandRepositoryExceptionTests extends H2RepositoryFaultyTestBase {

  private H2CommandRepository repository;

  private void setupRepository() {
    repository = new H2CommandRepository(getDataSource());
  }

  @Nested
  @DisplayName("Insert Exception Handling")
  class InsertExceptionTests {

    @Test
    @DisplayName("insertPending should throw exception when table doesn't exist")
    void testInsertPendingTableNotFound() {
      setupRepository();

      assertThatThrownBy(
          () ->
              repository.insertPending(
                  UUID.randomUUID(), "TestCommand", "test-key", "{}", "idempotent-key", null))
          .isInstanceOf(RuntimeException.class);
    }
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
    @DisplayName("existsByIdempotencyKey should throw exception when table doesn't exist")
    void testExistsByIdempotencyKeyTableNotFound() {
      setupRepository();

      assertThatThrownBy(() -> repository.existsByIdempotencyKey("key"))
          .isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("Status Update Exception Handling")
  class StatusUpdateExceptionTests {

    @Test
    @DisplayName("updateToRunning should throw exception when table doesn't exist")
    void testUpdateToRunningTableNotFound() {
      setupRepository();

      assertThatThrownBy(
          () ->
              repository.updateToRunning(
                  UUID.randomUUID(), Timestamp.from(java.time.Instant.now())))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("updateToSucceeded should throw exception when table doesn't exist")
    void testUpdateToSucceededTableNotFound() {
      setupRepository();

      assertThatThrownBy(() -> repository.updateToSucceeded(UUID.randomUUID()))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("updateToFailed should throw exception when table doesn't exist")
    void testUpdateToFailedTableNotFound() {
      setupRepository();

      assertThatThrownBy(() -> repository.updateToFailed(UUID.randomUUID(), "error"))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("incrementRetries should throw exception when table doesn't exist")
    void testIncrementRetriesTableNotFound() {
      setupRepository();

      assertThatThrownBy(() -> repository.incrementRetries(UUID.randomUUID(), "error"))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("updateToTimedOut should throw exception when table doesn't exist")
    void testUpdateToTimedOutTableNotFound() {
      setupRepository();

      assertThatThrownBy(() -> repository.updateToTimedOut(UUID.randomUUID(), "timeout"))
          .isInstanceOf(RuntimeException.class);
    }
  }
}
