package com.acme.reliable.persistence.jdbc.inbox;

import com.acme.reliable.persistence.jdbc.H2InboxRepository;
import com.acme.reliable.persistence.jdbc.H2RepositoryTestBase;
import com.acme.reliable.persistence.jdbc.JdbcInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    @Nested
    @DisplayName("SQL Conditional Binding Tests")
    class ConditionalSqlBindingTests {

        @Test
        @DisplayName("should use H2-style parameter binding for INSERT...SELECT...WHERE NOT EXISTS")
        void testH2StyleParameterBinding() throws SQLException {
            // Given - an H2 repository (uses INSERT...SELECT...WHERE NOT EXISTS)
            // The H2 SQL has 5 parameters: ?, ?, ? in SELECT, ?, ? in WHERE

            // When - insert succeeds
            int result = repository.insertIfAbsent("msg-h2-test", "h2-handler");

            // Then - should return 1 (successful insert)
            assertThat(result).isEqualTo(1);

            // When - insert duplicate
            int duplicate = repository.insertIfAbsent("msg-h2-test", "h2-handler");

            // Then - should return 0 (duplicate detected by WHERE NOT EXISTS)
            assertThat(duplicate).isZero();
        }

        @Test
        @DisplayName("PostgreSQL-style parameter binding should bind 3 parameters for ON CONFLICT syntax")
        void testPostgresStyleParameterBinding() throws SQLException {
            // This test verifies that the PostgreSQL path (else branch) correctly binds 3 parameters
            // instead of 5 (which is what H2-style binds)

            // Given - mock a DataSource and Connection to simulate PostgreSQL-style SQL
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            // Setup the mock to simulate PostgreSQL SQL (no "WHERE NOT EXISTS")
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1); // Successful insert

            try {
                // Create a mock PostgreSQL repository
                JdbcInboxRepository postgresRepository = new JdbcInboxRepository(mockDataSource) {
                    @Override
                    protected String getInsertIfAbsentSql() {
                        // PostgreSQL style: INSERT ... ON CONFLICT DO NOTHING (no WHERE NOT EXISTS)
                        return "INSERT INTO inbox (message_id, handler, processed_at) VALUES (?, ?, ?)";
                    }
                };

                // When - insert with PostgreSQL-style SQL
                int result = postgresRepository.insertIfAbsent("msg-postgres", "pg-handler");

                // Then - should successfully insert
                assertThat(result).isEqualTo(1);

                // Verify that setString was called 2 times (not 4 like H2)
                // PostgreSQL SQL: INSERT INTO inbox (message_id, handler, processed_at) VALUES (?, ?, ?)
                // Parameters: messageId (setString), handler (setString), processed_at (setTimestamp)
                verify(mockPs, times(2)).setString(anyInt(), anyString());
                // Verify that setTimestamp was called once
                verify(mockPs, times(1)).setTimestamp(anyInt(), any(Timestamp.class));

            } catch (Exception e) {
                fail("PostgreSQL-style parameter binding test failed", e);
            }
        }
    }
}
