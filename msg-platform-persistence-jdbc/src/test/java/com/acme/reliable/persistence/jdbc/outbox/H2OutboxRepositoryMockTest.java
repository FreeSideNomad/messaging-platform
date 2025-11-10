package com.acme.reliable.persistence.jdbc.outbox;

import com.acme.reliable.domain.Outbox;
import com.acme.reliable.persistence.jdbc.JdbcOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mock-based tests for comprehensive branch coverage of JdbcOutboxRepository.
 * Uses Mockito to simulate database failures and edge cases that are hard to trigger naturally.
 */
@DisplayName("JdbcOutboxRepository Mock-Based Branch Coverage Tests")
class H2OutboxRepositoryMockTest {

    private JdbcOutboxRepository createMockRepository(DataSource mockDataSource) {
        return new JdbcOutboxRepository(mockDataSource) {
            @Override
            protected String getInsertSql() {
                return "INSERT INTO outbox (category, topic, \"key\", \"type\", payload, headers, status, attempts, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            }

            @Override
            protected String getClaimIfNewSql() {
                return "UPDATE outbox SET status = 'CLAIMED' WHERE id = ? AND status = 'NEW'";
            }

            @Override
            protected String getSweepBatchSql() {
                return "SELECT id, category, topic, \"key\", \"type\", payload, headers, status, attempts, next_at, claimed_by, created_at, published_at, last_error FROM outbox WHERE status = 'NEW' LIMIT ?";
            }

            @Override
            protected String getMarkPublishedSql() {
                return "UPDATE outbox SET published_at = ?, status = 'PUBLISHED' WHERE id = ?";
            }

            @Override
            protected String getMarkFailedSql() {
                return "UPDATE outbox SET last_error = ?, next_at = ? WHERE id = ?";
            }

            @Override
            protected String getRescheduleSql() {
                return "UPDATE outbox SET next_at = ?, last_error = ? WHERE id = ?";
            }

            @Override
            protected String getRecoverStuckSql() {
                return "UPDATE outbox SET status = 'NEW' WHERE status = 'CLAIMED' AND created_at < ?";
            }
        };
    }

    @Nested
    @DisplayName("insertReturningId Failure Scenarios")
    class InsertReturningIdFailureTests {

        @Test
        @DisplayName("should throw exception when no generated keys returned (rs.next() false)")
        void testInsertReturningIdNoGeneratedKeys() throws SQLException {
            // Given: Mock connection with no generated keys
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);
            ResultSet mockGeneratedKeys = mock(ResultSet.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString(), anyInt())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1); // Row inserted
            when(mockPs.getGeneratedKeys()).thenReturn(mockGeneratedKeys);
            when(mockGeneratedKeys.next()).thenReturn(false); // No generated keys (false branch)

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When & Then
            assertThatThrownBy(() -> repository.insertReturningId("events", "test", "key", "type", "{}", "{}"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to insert");
        }

        @Test
        @DisplayName("should throw exception when executeUpdate returns 0 rows")
        void testInsertReturningIdZeroRowsInserted() throws SQLException {
            // Given: Mock that simulates 0 rows inserted
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString(), anyInt())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(0); // Zero rows inserted (false branch)

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When & Then
            assertThatThrownBy(() -> repository.insertReturningId("events", "test", "key", "type", "{}", "{}"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to insert");
        }

        @Test
        @DisplayName("should handle SQLException in executeUpdate")
        void testInsertReturningIdExecuteUpdateException() throws SQLException {
            // Given: Mock that throws SQLException
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString(), anyInt())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenThrow(new SQLException("Database error"));

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When & Then
            assertThatThrownBy(() -> repository.insertReturningId("events", "test", "key", "type", "{}", "{}"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should handle SQLException when getting connection")
        void testInsertReturningIdConnectionException() throws SQLException {
            // Given: Mock that throws SQLException on getConnection
            DataSource mockDataSource = mock(DataSource.class);
            when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When & Then
            assertThatThrownBy(() -> repository.insertReturningId("events", "test", "key", "type", "{}", "{}"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Header NULL/Empty Branch Coverage")
    class HeaderBranchCoverageTests {

        @Test
        @DisplayName("should handle NULL headers in mapping (false branch of null check)")
        void testMapNullHeaders() throws SQLException {
            // Given: Mock ResultSet with NULL headers column
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);
            ResultSet mockRs = mock(ResultSet.class);

            setupMockForMapping(mockDataSource, mockConnection, mockPs, mockRs, null); // NULL headers

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When
            Outbox result = repository.claimIfNew(1L).orElse(null);

            // Then: Should handle null and provide default
            assertThat(result).isNotNull();
            assertThat(result.getHeaders()).isNotNull();
        }

        @Test
        @DisplayName("should handle EMPTY string headers in mapping")
        void testMapEmptyHeaders() throws SQLException {
            // Given: Mock ResultSet with empty string headers
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);
            ResultSet mockRs = mock(ResultSet.class);

            setupMockForMapping(mockDataSource, mockConnection, mockPs, mockRs, ""); // Empty headers

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When
            Outbox result = repository.claimIfNew(1L).orElse(null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHeaders()).isNotNull();
        }

        @Test
        @DisplayName("should handle NULL createdAt in mapping")
        void testMapNullCreatedAt() throws SQLException {
            // Given: Mock ResultSet with NULL createdAt (edge case)
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);
            ResultSet mockRs = mock(ResultSet.class);

            // Setup mapping with specific createdAt handling
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1);
            when(mockPs.executeQuery()).thenReturn(mockRs);
            when(mockRs.next()).thenReturn(true);

            // Required columns for mapping
            when(mockRs.getLong("id")).thenReturn(1L);
            when(mockRs.getString("category")).thenReturn("test");
            when(mockRs.getString("topic")).thenReturn("test");
            when(mockRs.getString("key")).thenReturn("test");
            when(mockRs.getString("type")).thenReturn("test");
            when(mockRs.getString("payload")).thenReturn("{}");
            when(mockRs.getString("headers")).thenReturn("{}");
            when(mockRs.getString("status")).thenReturn("NEW");
            when(mockRs.getInt("attempts")).thenReturn(0);
            when(mockRs.getTimestamp("next_at")).thenReturn(null);
            when(mockRs.getString("claimed_by")).thenReturn(null);
            when(mockRs.getTimestamp("created_at")).thenReturn(null); // NULL createdAt
            when(mockRs.getTimestamp("published_at")).thenReturn(null);
            when(mockRs.getString("last_error")).thenReturn(null);

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When & Then: Should handle NULL createdAt gracefully
            assertThatCode(() -> repository.claimIfNew(1L))
                    .doesNotThrowAnyException();
        }

        private void setupMockForMapping(
                DataSource mockDataSource,
                Connection mockConnection,
                PreparedStatement mockPs,
                ResultSet mockRs,
                String headers) throws SQLException {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1);
            when(mockPs.executeQuery()).thenReturn(mockRs);
            when(mockRs.next()).thenReturn(true);

            // Setup all required columns
            when(mockRs.getLong("id")).thenReturn(1L);
            when(mockRs.getString("category")).thenReturn("test");
            when(mockRs.getString("topic")).thenReturn("test");
            when(mockRs.getString("key")).thenReturn("test");
            when(mockRs.getString("type")).thenReturn("test");
            when(mockRs.getString("payload")).thenReturn("{}");
            when(mockRs.getString("headers")).thenReturn(headers);
            when(mockRs.getString("status")).thenReturn("NEW");
            when(mockRs.getInt("attempts")).thenReturn(0);
            when(mockRs.getTimestamp("next_at")).thenReturn(null);
            when(mockRs.getString("claimed_by")).thenReturn(null);
            when(mockRs.getTimestamp("created_at")).thenReturn(mock());
            when(mockRs.getTimestamp("published_at")).thenReturn(null);
            when(mockRs.getString("last_error")).thenReturn(null);
        }
    }


    @Nested
    @DisplayName("insert Method Branches")
    class InsertBranchTests {

        @Test
        @DisplayName("insert should throw SQLException wrapped in RuntimeException")
        void testInsertTableNotFound() throws SQLException {
            // Given: Mock that throws
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenThrow(new SQLException("Table not found"));

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When & Then
            assertThatThrownBy(
                    () -> repository.insert(1L, "events", "test", "key", "type", "{}", "{}", "NEW", 0))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("sweepBatch Exception Branches")
    class SweepBatchExceptionTests {

        @Test
        @DisplayName("sweepBatch should handle empty result set exception")
        void testSweepBatchQueryException() throws SQLException {
            // Given: Mock that throws on query
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeQuery()).thenThrow(new SQLException("Query failed"));

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When & Then
            assertThatThrownBy(() -> repository.sweepBatch(10))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Mark Operations Exception Branches")
    class MarkOperationsExceptionTests {

        @Test
        @DisplayName("markPublished should handle SQLException")
        void testMarkPublishedException() throws SQLException {
            // Given: Mock that throws
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenThrow(new SQLException("Mark failed"));

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When & Then
            assertThatThrownBy(() -> repository.markPublished(1L))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("markFailed should handle SQLException")
        void testMarkFailedException() throws SQLException {
            // Given: Mock that throws
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenThrow(new SQLException("Mark failed"));

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When & Then
            assertThatThrownBy(() -> repository.markFailed(1L, "error", java.time.Instant.now()))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("reschedule should handle SQLException")
        void testRescheduleException() throws SQLException {
            // Given: Mock that throws
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenThrow(new SQLException("Reschedule failed"));

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When & Then
            assertThatThrownBy(() -> repository.reschedule(1L, 5000, "error"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("recoverStuck Exception Branches")
    class RecoverStuckExceptionTests {

        @Test
        @DisplayName("recoverStuck should handle SQLException")
        void testRecoverStuckException() throws SQLException {
            // Given: Mock that throws
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenThrow(new SQLException("Recover failed"));

            JdbcOutboxRepository repository = createMockRepository(mockDataSource);

            // When & Then
            assertThatThrownBy(() -> repository.recoverStuck(java.time.Duration.ofHours(1)))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
