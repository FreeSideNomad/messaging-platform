package com.acme.reliable.persistence.jdbc.inbox;

import com.acme.reliable.persistence.jdbc.JdbcInboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive branch coverage tests for JdbcInboxRepository SQL dialect branching.
 * Targets the critical if/else logic at lines 34-46 in JdbcInboxRepository.java.
 * <p>
 * This test class focuses on:
 * 1. PostgreSQL path (ON CONFLICT) - else branch (lines 41-45)
 * 2. H2 path (WHERE NOT EXISTS) - if branch (lines 34-40)
 * 3. Both success and duplicate scenarios for each dialect
 * 4. Correct parameter binding for each SQL dialect
 */
@DisplayName("JdbcInboxRepository Branch Coverage Tests")
class JdbcInboxRepositoryBranchCoverageTest {

    @Nested
    @DisplayName("PostgreSQL Dialect Branch Coverage")
    class PostgresDialectBranchTests {

        @Test
        @DisplayName("PostgreSQL path should bind exactly 3 parameters on successful insert")
        void testPostgresPathSuccessfulInsert() throws SQLException {
            // Given - mock setup for PostgreSQL-style SQL
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1); // Successful insert

            // Create PostgreSQL repository (no "WHERE NOT EXISTS" in SQL)
            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox (message_id, handler, processed_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
                }
            };

            // When - insert executes the ELSE branch (lines 41-45)
            int result = repository.insertIfAbsent("msg-postgres-123", "pg-handler");

            // Then - verify result and parameter binding
            assertThat(result).isEqualTo(1);

            // Verify ELSE branch: 3 parameters bound (messageId, handler, timestamp)
            verify(mockPs, times(1)).setString(1, "msg-postgres-123");
            verify(mockPs, times(1)).setString(2, "pg-handler");
            verify(mockPs, times(1)).setTimestamp(eq(3), any(Timestamp.class));

            // Verify no additional parameters (H2 binds 5, PostgreSQL binds 3)
            verify(mockPs, times(2)).setString(anyInt(), anyString());
            verify(mockPs, times(1)).setTimestamp(anyInt(), any(Timestamp.class));

            verify(mockPs, times(1)).executeUpdate();
        }

        @Test
        @DisplayName("PostgreSQL path should handle duplicate detection (return 0)")
        void testPostgresPathDuplicateDetection() throws SQLException {
            // Given - mock setup for PostgreSQL-style SQL
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(0); // Duplicate - no rows inserted

            // Create PostgreSQL repository
            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox (message_id, handler, processed_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
                }
            };

            // When - insert duplicate (ELSE branch at lines 41-45)
            int result = repository.insertIfAbsent("msg-duplicate", "handler");

            // Then - should return 0 for duplicate
            assertThat(result).isZero();

            // Verify parameter binding happened correctly
            verify(mockPs, times(2)).setString(anyInt(), anyString());
            verify(mockPs, times(1)).setTimestamp(anyInt(), any(Timestamp.class));
        }

        @Test
        @DisplayName("PostgreSQL path should be taken when SQL contains 'ON CONFLICT'")
        void testPostgresPathWithOnConflictKeyword() throws SQLException {
            // Given
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1);

            // Repository with ON CONFLICT (triggers ELSE branch)
            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox (message_id, handler, processed_at) VALUES (?, ?, ?) ON CONFLICT (message_id, handler) DO NOTHING";
                }
            };

            // When
            repository.insertIfAbsent("test-msg", "test-handler");

            // Then - verify ELSE branch executes (3 parameters, not 5)
            verify(mockPs, times(2)).setString(anyInt(), anyString());
            verify(mockPs, never()).setString(eq(4), anyString()); // Only H2 sets 4th parameter
            verify(mockPs, never()).setString(eq(5), anyString()); // Only H2 sets 5th parameter
        }
    }

    @Nested
    @DisplayName("H2 Dialect Branch Coverage")
    class H2DialectBranchTests {

        @Test
        @DisplayName("H2 path should bind 5 parameters on successful insert")
        void testH2PathSuccessfulInsert() throws SQLException {
            // Given - mock setup for H2-style SQL
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1); // Successful insert

            // Create H2 repository (contains "WHERE NOT EXISTS")
            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox (message_id, handler, processed_at) " +
                            "SELECT ?, ?, ? " +
                            "WHERE NOT EXISTS(SELECT 1 FROM inbox WHERE message_id = ? AND handler = ?)";
                }
            };

            // When - insert executes the IF branch (lines 34-40)
            int result = repository.insertIfAbsent("msg-h2-123", "h2-handler");

            // Then - verify result and parameter binding
            assertThat(result).isEqualTo(1);

            // Verify IF branch: 5 parameters bound
            // Parameters 1-3: SELECT clause
            verify(mockPs, times(1)).setString(1, "msg-h2-123");
            verify(mockPs, times(1)).setString(2, "h2-handler");
            verify(mockPs, times(1)).setTimestamp(eq(3), any(Timestamp.class));

            // Parameters 4-5: WHERE NOT EXISTS clause
            verify(mockPs, times(1)).setString(4, "msg-h2-123");
            verify(mockPs, times(1)).setString(5, "h2-handler");

            // Total: 4 setString calls (positions 1, 2, 4, 5) + 1 setTimestamp (position 3)
            verify(mockPs, times(4)).setString(anyInt(), anyString());
            verify(mockPs, times(1)).setTimestamp(anyInt(), any(Timestamp.class));

            verify(mockPs, times(1)).executeUpdate();
        }

        @Test
        @DisplayName("H2 path should handle duplicate detection (return 0)")
        void testH2PathDuplicateDetection() throws SQLException {
            // Given - mock setup for H2-style SQL
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(0); // Duplicate - no rows inserted

            // Create H2 repository
            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox (message_id, handler, processed_at) " +
                            "SELECT ?, ?, ? " +
                            "WHERE NOT EXISTS(SELECT 1 FROM inbox WHERE message_id = ? AND handler = ?)";
                }
            };

            // When - insert duplicate (IF branch at lines 34-40)
            int result = repository.insertIfAbsent("msg-duplicate", "handler");

            // Then - should return 0 for duplicate
            assertThat(result).isZero();

            // Verify all 5 parameters were bound
            verify(mockPs, times(4)).setString(anyInt(), anyString());
            verify(mockPs, times(1)).setTimestamp(anyInt(), any(Timestamp.class));
        }

        @Test
        @DisplayName("H2 path should be taken when SQL contains 'WHERE NOT EXISTS'")
        void testH2PathWithWhereNotExistsKeyword() throws SQLException {
            // Given
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1);

            // Repository with WHERE NOT EXISTS (triggers IF branch)
            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox (message_id, handler, processed_at) " +
                            "SELECT ?, ?, ? " +
                            "WHERE NOT EXISTS(SELECT 1 FROM inbox WHERE message_id = ? AND handler = ?)";
                }
            };

            // When
            repository.insertIfAbsent("test-msg", "test-handler");

            // Then - verify IF branch executes (5 parameters)
            verify(mockPs, times(4)).setString(anyInt(), anyString()); // 4 string parameters
            verify(mockPs, times(1)).setString(4, "test-msg"); // 4th parameter bound
            verify(mockPs, times(1)).setString(5, "test-handler"); // 5th parameter bound
        }
    }

    @Nested
    @DisplayName("SQL Dialect Detection Logic")
    class DialectDetectionTests {

        @Test
        @DisplayName("Should use H2 path for SQL with 'WHERE NOT EXISTS' (case sensitive)")
        void testWhereNotExistsDetection() throws SQLException {
            // Given
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1);

            // SQL contains exact phrase "WHERE NOT EXISTS"
            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox SELECT ?, ?, ? WHERE NOT EXISTS(SELECT 1 FROM inbox WHERE message_id = ? AND handler = ?)";
                }
            };

            // When
            repository.insertIfAbsent("msg", "handler");

            // Then - should use H2 branch (5 parameters)
            verify(mockPs, times(4)).setString(anyInt(), anyString());
        }

        @Test
        @DisplayName("Should use PostgreSQL path for SQL without 'WHERE NOT EXISTS'")
        void testNoWhereNotExistsUsesPostgresPath() throws SQLException {
            // Given
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1);

            // SQL does NOT contain "WHERE NOT EXISTS"
            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox (message_id, handler, processed_at) VALUES (?, ?, ?)";
                }
            };

            // When
            repository.insertIfAbsent("msg", "handler");

            // Then - should use PostgreSQL branch (3 parameters)
            verify(mockPs, times(2)).setString(anyInt(), anyString());
            verify(mockPs, never()).setString(eq(4), anyString());
            verify(mockPs, never()).setString(eq(5), anyString());
        }
    }

    @Nested
    @DisplayName("Logging Branch Coverage")
    class LoggingBranchTests {

        @Test
        @DisplayName("Should trigger success log when rowsInserted > 0")
        void testSuccessLogging() throws SQLException {
            // Given
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1); // Success branch

            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
                }
            };

            // When - should log "Inserted inbox entry" (line 50)
            int result = repository.insertIfAbsent("new-msg", "handler");

            // Then
            assertThat(result).isEqualTo(1);
            // Note: Actual log verification would require log capture
        }

        @Test
        @DisplayName("Should trigger duplicate log when rowsInserted == 0")
        void testDuplicateLogging() throws SQLException {
            // Given
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(0); // Duplicate branch

            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
                }
            };

            // When - should log "Inbox entry already exists" (line 52)
            int result = repository.insertIfAbsent("duplicate-msg", "handler");

            // Then
            assertThat(result).isZero();
            // Note: Actual log verification would require log capture
        }
    }

    @Nested
    @DisplayName("Exception Handling Branch Coverage")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Should translate SQLException to RuntimeException")
        void testSqlExceptionTranslation() throws SQLException {
            // Given
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString()))
                    .thenThrow(new SQLException("Connection failed", "08001"));

            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox VALUES (?, ?, ?)";
                }
            };

            // When/Then - should translate and throw RuntimeException (line 58)
            assertThatThrownBy(() -> repository.insertIfAbsent("msg", "handler"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("insert inbox entry");
        }

        @Test
        @DisplayName("Should handle SQLException during executeUpdate")
        void testExecuteUpdateException() throws SQLException {
            // Given
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenThrow(new SQLException("Constraint violation"));

            JdbcInboxRepository repository = new JdbcInboxRepository(mockDataSource) {
                @Override
                protected String getInsertIfAbsentSql() {
                    return "INSERT INTO inbox VALUES (?, ?, ?)";
                }
            };

            // When/Then
            assertThatThrownBy(() -> repository.insertIfAbsent("msg", "handler"))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
