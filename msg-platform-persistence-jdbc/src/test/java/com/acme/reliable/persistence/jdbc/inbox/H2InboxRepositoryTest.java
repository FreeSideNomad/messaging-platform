package com.acme.reliable.persistence.jdbc.inbox;

import static org.assertj.core.api.Assertions.*;

import com.acme.reliable.persistence.jdbc.H2RepositoryTestBase;
import java.sql.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive H2-based integration tests for JDBC InboxRepository implementation.
 * Tests idempotent insert operations and duplicate detection.
 */
class H2InboxRepositoryTest extends H2RepositoryTestBase {

  private H2InboxRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    repository = new H2InboxRepository(dataSource);

    try (Connection conn = dataSource.getConnection()) {
      conn.createStatement().execute("DELETE FROM inbox");
    }
  }

  @Nested
  @DisplayName("Idempotent Insert Tests")
  class IdempotentInsertTests {

    @Test
    @DisplayName("insertIfAbsent should insert and return 1 on first insert")
    void testInsertFirstTime() {
      // When
      int result = repository.insertIfAbsent("msg-123", "email-handler");

      // Then
      assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("insertIfAbsent should return 0 on duplicate message")
    void testInsertDuplicate() {
      // Given - first insert succeeds
      repository.insertIfAbsent("msg-456", "sms-handler");

      // When - try to insert same message with same handler
      int result = repository.insertIfAbsent("msg-456", "sms-handler");

      // Then - should return 0 (duplicate)
      assertThat(result).isZero();
    }

    @Test
    @DisplayName("insertIfAbsent should allow same message for different handlers")
    void testSameMessageDifferentHandler() {
      // Given
      int first = repository.insertIfAbsent("msg-789", "handler-a");

      // When - same message, different handler
      int second = repository.insertIfAbsent("msg-789", "handler-b");

      // Then
      assertThat(first).isEqualTo(1);
      assertThat(second).isEqualTo(1); // Different handler, should insert
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("should handle long message IDs")
    void testLongMessageIds() {
      // Given
      String longId = "msg-" + "x".repeat(250);

      // When
      int result = repository.insertIfAbsent(longId, "handler");

      // Then
      assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("should handle special characters in identifiers")
    void testSpecialCharacters() {
      // Given
      String messageId = "msg:order-123@domain.com";
      String handler = "handler/processor-1";

      // When
      int result = repository.insertIfAbsent(messageId, handler);

      // Then
      assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("should maintain composite key uniqueness")
    void testCompositeKeyUniqueness() {
      // Given - multiple entries with overlapping IDs/handlers
      repository.insertIfAbsent("msg-A", "handler-1");
      repository.insertIfAbsent("msg-A", "handler-2");
      repository.insertIfAbsent("msg-B", "handler-1");

      // When - try duplicate combination
      int duplicate = repository.insertIfAbsent("msg-A", "handler-1");

      // Then
      assertThat(duplicate).isZero();

      // And other combinations should still work
      int newCombination = repository.insertIfAbsent("msg-B", "handler-2");
      assertThat(newCombination).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Bulk Operation Tests")
  class BulkOperationTests {

    @Test
    @DisplayName("should handle multiple concurrent inserts")
    void testMultipleConcurrentInserts() {
      // When
      for (int i = 0; i < 100; i++) {
        int result = repository.insertIfAbsent("msg-" + i, "handler-" + (i % 5));
        assertThat(result).isEqualTo(1); // All new
      }

      // Then - try to re-insert all
      int duplicateCount = 0;
      for (int i = 0; i < 100; i++) {
        int result = repository.insertIfAbsent("msg-" + i, "handler-" + (i % 5));
        if (result == 0) {
          duplicateCount++;
        }
      }

      assertThat(duplicateCount).isEqualTo(100); // All duplicates
    }
  }
}
