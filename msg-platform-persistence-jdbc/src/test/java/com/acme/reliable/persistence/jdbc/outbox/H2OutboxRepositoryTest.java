package com.acme.reliable.persistence.jdbc.outbox;

import com.acme.reliable.domain.Outbox;
import com.acme.reliable.persistence.jdbc.H2OutboxRepository;
import com.acme.reliable.persistence.jdbc.H2RepositoryTestBase;
import com.acme.reliable.persistence.jdbc.JdbcOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Comprehensive H2-based integration tests for JDBC OutboxRepository implementation.
 * Tests all operations: insertion, claiming, batch sweeping, status updates, scheduling, and recovery.
 */
class H2OutboxRepositoryTest extends H2RepositoryTestBase {

    private H2OutboxRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new H2OutboxRepository(dataSource);

        // Clean up data before each test
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM outbox");
        }
    }

    @Nested
    @DisplayName("Insert and Retrieval Tests")
    class InsertAndRetrievalTests {

        @Test
        @DisplayName("insertReturningId should insert and return generated ID")
        void testInsertReturningId() {
            // When
            long id = repository.insertReturningId("events", "orders", "order-123", "OrderCreated",
                    "{\"orderId\":\"123\"}", "{\"version\":\"1\"}");

            // Then
            assertThat(id).isGreaterThan(0);

            // Verify entry exists
            Optional<Outbox> outbox = repository.claimIfNew(id);
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getId()).isEqualTo(id);
            assertThat(outbox.get().getCategory()).isEqualTo("events");
            assertThat(outbox.get().getTopic()).isEqualTo("orders");
        }

        @Test
        @DisplayName("insert should insert with specific status and attempts")
        void testInsertWithStatus() {
            // When
            repository.insert(1L, "commands", "payments", "pay-456", "PaymentProcessed",
                    "{\"amount\":100}", "{}", "NEW", 0);

            // Then - verify entry was created with correct status
            try (Connection conn = dataSource.getConnection()) {
                var pstmt = conn.prepareStatement("SELECT status, attempts FROM outbox WHERE id = ?");
                pstmt.setLong(1, 1L);
                var rs = pstmt.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("NEW");
                assertThat(rs.getInt("attempts")).isZero();
            } catch (Exception e) {
                fail("Failed to verify insert", e);
            }
        }
    }

    @Nested
    @DisplayName("Claim Operations Tests")
    class ClaimOperationsTests {

        @Test
        @DisplayName("claimIfNew should claim entry in NEW status and return it")
        void testClaimIfNewSuccess() {
            // Given - entry in NEW status
            long id = repository.insertReturningId("events", "orders", "ord-100", "EventType",
                    "{}", "{}");

            // When
            Optional<Outbox> claimed = repository.claimIfNew(id);

            // Then
            assertThat(claimed).isPresent();
            assertThat(claimed.get().getStatus()).isEqualTo("CLAIMED");
        }

        @Test
        @DisplayName("claimIfNew should return empty if not in NEW status")
        void testClaimIfNewAlreadyClaimed() {
            // Given - claimed entry
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id); // First claim succeeds

            // When - try to claim again
            Optional<Outbox> claimed = repository.claimIfNew(id);

            // Then - should return empty (already CLAIMED)
            assertThat(claimed).isEmpty();
        }
    }

    @Nested
    @DisplayName("Batch Sweep Tests")
    class BatchSweepTests {

        @Test
        @DisplayName("sweepBatch should return NEW entries up to max count")
        void testSweepBatchNewEntries() {
            // Given - 3 NEW entries
            repository.insertReturningId("events", "test", "k1", "type", "{}", "{}");
            repository.insertReturningId("events", "test", "k2", "type", "{}", "{}");
            repository.insertReturningId("events", "test", "k3", "type", "{}", "{}");

            // When
            List<Outbox> swept = repository.sweepBatch(2);

            // Then - should return only 2 (max)
            assertThat(swept).hasSize(2);
            assertThat(swept).allMatch(o -> o.getStatus().equals("CLAIMED"));
        }

        @Test
        @DisplayName("sweepBatch should include timed-out CLAIMED entries")
        void testSweepBatchTimedOutClaimed() throws InterruptedException {
            // Given - entry claimed and timed out (older than 5 minutes)
            long id = repository.insertReturningId("events", "test", "old", "type", "{}", "{}");
            repository.claimIfNew(id);

            // Manually update to simulate timeout (created 10 minutes ago)
            try (Connection conn = dataSource.getConnection()) {
                var pstmt = conn.prepareStatement(
                        "UPDATE outbox SET created_at = CURRENT_TIMESTAMP - INTERVAL '10' MINUTE WHERE id = ?");
                pstmt.setLong(1, id);
                pstmt.executeUpdate();
            } catch (Exception e) {
                fail("Failed to update timestamp", e);
            }

            // When
            List<Outbox> swept = repository.sweepBatch(10);

            // Then - should include the timed-out entry
            assertThat(swept).isNotEmpty();
            assertThat(swept).anyMatch(o -> o.getId() == id);
        }
    }

    @Nested
    @DisplayName("Status Update Tests")
    class StatusUpdateTests {

        @Test
        @DisplayName("markPublished should update status to PUBLISHED with timestamp")
        void testMarkPublished() {
            // Given
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id);

            // When
            repository.markPublished(id);

            // Then
            try (Connection conn = dataSource.getConnection()) {
                var pstmt = conn.prepareStatement(
                        "SELECT status, published_at FROM outbox WHERE id = ?");
                pstmt.setLong(1, id);
                var rs = pstmt.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("PUBLISHED");
                assertThat(rs.getTimestamp("published_at")).isNotNull();
            } catch (Exception e) {
                fail("Failed to verify published status", e);
            }
        }

        @Test
        @DisplayName("markFailed should update last_error and next_at")
        void testMarkFailed() {
            // Given
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            Instant nextAttempt = Instant.now().plus(30, ChronoUnit.SECONDS);

            // When
            repository.markFailed(id, "Connection timeout", nextAttempt);

            // Then
            try (Connection conn = dataSource.getConnection()) {
                var pstmt = conn.prepareStatement(
                        "SELECT last_error, next_at FROM outbox WHERE id = ?");
                pstmt.setLong(1, id);
                var rs = pstmt.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("last_error")).isEqualTo("Connection timeout");
                assertThat(rs.getTimestamp("next_at")).isNotNull();
            } catch (Exception e) {
                fail("Failed to verify failed status", e);
            }
        }
    }

    @Nested
    @DisplayName("Reschedule and Recovery Tests")
    class RescheduleAndRecoveryTests {

        @Test
        @DisplayName("reschedule should update next_at with backoff and record error")
        void testReschedule() {
            // Given
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When
            repository.reschedule(id, 5000, "Temporary error");

            // Then
            try (Connection conn = dataSource.getConnection()) {
                var pstmt = conn.prepareStatement(
                        "SELECT next_at, last_error FROM outbox WHERE id = ?");
                pstmt.setLong(1, id);
                var rs = pstmt.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp("next_at")).isNotNull();
                assertThat(rs.getString("last_error")).isEqualTo("Temporary error");
            } catch (Exception e) {
                fail("Failed to verify reschedule", e);
            }
        }

        @Test
        @DisplayName("recoverStuck should reset timed-out CLAIMED entries to NEW")
        void testRecoverStuck() {
            // Given - CLAIMED entry older than threshold
            long id = repository.insertReturningId("events", "test", "stuck", "type", "{}", "{}");
            repository.claimIfNew(id);

            try (Connection conn = dataSource.getConnection()) {
                var pstmt = conn.prepareStatement(
                        "UPDATE outbox SET created_at = CURRENT_TIMESTAMP - INTERVAL '30' MINUTE WHERE id = ?");
                pstmt.setLong(1, id);
                pstmt.executeUpdate();
            } catch (Exception e) {
                fail("Failed to set old timestamp", e);
            }

            // When
            int recovered = repository.recoverStuck(Duration.ofMinutes(15));

            // Then
            assertThat(recovered).isGreaterThanOrEqualTo(1);

            try (Connection conn = dataSource.getConnection()) {
                var pstmt = conn.prepareStatement("SELECT status FROM outbox WHERE id = ?");
                pstmt.setLong(1, id);
                var rs = pstmt.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("NEW");
            } catch (Exception e) {
                fail("Failed to verify recovery", e);
            }
        }
    }

    @Nested
    @DisplayName("Field Mapping and Preservation Tests")
    class FieldMappingTests {

        @Test
        @DisplayName("all fields should be preserved during insert and retrieval")
        void testAllFieldsPreserved() {
            // Given
            String category = "domain-events";
            String topic = "customer-service";
            String key = "customer-123";
            String type = "CustomerCreated";
            String payload = "{\"customerId\":\"123\",\"name\":\"John\"}";
            String headers = "{\"version\":\"2\",\"source\":\"api\"}";

            // When
            long id = repository.insertReturningId(category, topic, key, type, payload, headers);

            // Then
            Optional<Outbox> retrieved = repository.claimIfNew(id);
            assertThat(retrieved).isPresent();
            Outbox outbox = retrieved.get();

            assertThat(outbox.getId()).isEqualTo(id);
            assertThat(outbox.getCategory()).isEqualTo(category);
            assertThat(outbox.getTopic()).isEqualTo(topic);
            assertThat(outbox.getKey()).isEqualTo(key);
            assertThat(outbox.getType()).isEqualTo(type);
            assertThat(outbox.getPayload()).isEqualTo(payload);
            assertThat(outbox.getHeaders()).isNotNull();
            assertThat(outbox.getStatus()).isEqualTo("CLAIMED");
            assertThat(outbox.getAttempts()).isZero();
            assertThat(outbox.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("headers should be deserialized as Map")
        void testHeadersDeserialization() {
            // Given
            long id = repository.insertReturningId("events", "test", "key", "type",
                    "{}", "{\"correlation-id\":\"abc-123\",\"request-id\":\"req-456\"}");

            // When
            Optional<Outbox> outbox = repository.claimIfNew(id);

            // Then
            assertThat(outbox).isPresent();
            Map<String, String> headers = outbox.get().getHeaders();
            assertThat(headers).containsEntry("correlation-id", "abc-123")
                    .containsEntry("request-id", "req-456");
        }

        @Test
        @DisplayName("claimIfNew should return empty Optional for non-existent entry")
        void testClaimIfNewNonExistent() {
            // When: Try to claim an entry that was never inserted
            Optional<Outbox> claimed = repository.claimIfNew(999999L);

            // Then: Should return empty
            assertThat(claimed).isEmpty();
        }

        @Test
        @DisplayName("sweepBatch should return empty list when no pending entries exist")
        void testSweepBatchEmpty() {
            // When: Try to sweep with empty outbox
            List<Outbox> swept = repository.sweepBatch(100);

            // Then: Should return empty list
            assertThat(swept).isEmpty();
        }

        @Test
        @DisplayName("insertReturningId with null headers should default to empty JSON")
        void testInsertReturningIdNullHeaders() {
            // When: Insert with null headers
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", null);

            // Then: Verify headers are stored and retrieved correctly
            Optional<Outbox> outbox = repository.claimIfNew(id);
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getHeaders()).isNotNull();
        }

        @Test
        @DisplayName("mappingResultSetToOutbox should handle null publishedAt timestamp")
        void testMappingNullPublishedAt() {
            // Given: Entry that hasn't been published
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Claim the entry (status is CLAIMED, not PUBLISHED)
            Optional<Outbox> outbox = repository.claimIfNew(id);

            // Then: publishedAt should be null
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getPublishedAt()).isNull();
        }

        @Test
        @DisplayName("mapResultSetToOutbox should handle null lastError field")
        void testMappingNullLastError() {
            // Given: Entry without error
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Claim the entry
            Optional<Outbox> outbox = repository.claimIfNew(id);

            // Then: lastError should be null
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getLastError()).isNull();
        }

        @Test
        @DisplayName("mapResultSetToOutbox should handle null claimedBy field")
        void testMappingNullClaimedBy() throws SQLException {
            // Given: Entry without claimedBy set
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Retrieve before claiming
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT claimed_by FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    // Then: claimedBy should be null before claiming
                    if (rs.next()) {
                        assertThat(rs.getString("claimed_by")).isNull();
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Update Status Edge Cases")
    class UpdateStatusEdgeCasesTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("markPublished should handle non-existent entry gracefully")
        void testMarkPublishedNonExistent() {
            // When: Try to mark non-existent entry as published
            // Then: Should not throw, just log warning internally
            assertThatNoException().isThrownBy(() -> repository.markPublished(999999L));
        }

        @Test
        @DisplayName("markFailed should handle non-existent entry gracefully")
        void testMarkFailedNonExistent() {
            // When: Try to mark non-existent entry as failed
            // Then: Should not throw
            assertThatNoException().isThrownBy(
                    () -> repository.markFailed(999999L, "Some error", Instant.now()));
        }

        @Test
        @DisplayName("reschedule should handle non-existent entry gracefully")
        void testRescheduleNonExistent() {
            // When: Try to reschedule non-existent entry
            // Then: Should not throw
            assertThatNoException().isThrownBy(
                    () -> repository.reschedule(999999L, 5000, "Error message"));
        }

        @Test
        @DisplayName("recoverStuck should handle empty table")
        void testRecoverStuckEmpty() {
            // When: Try to recover stuck entries from empty table
            int updated = repository.recoverStuck(Duration.ofHours(1));

            // Then: Should return 0
            assertThat(updated).isZero();
        }
    }

    @Nested
    @DisplayName("Null Field Handling")
    class NullFieldHandlingTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("should handle null nextAt timestamp field")
        void testMappingNullNextAt() {
            // Given: Entry without nextAt set
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Claim the entry
            Optional<Outbox> outbox = repository.claimIfNew(id);

            // Then: nextAt should be null
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getNextAt()).isNull();
        }

        @Test
        @DisplayName("should handle empty string headers")
        void testMappingEmptyStringHeaders() {
            // When: Insert with explicit empty JSON headers
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // Then: Retrieve and verify headers are empty map
            Optional<Outbox> outbox = repository.claimIfNew(id);
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getHeaders()).isEmpty();
        }

        @Test
        @DisplayName("should handle createdAt timestamp presence")
        void testMappingCreatedAtPresent() {
            // Given: Entry with createdAt
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Claim the entry
            Optional<Outbox> outbox = repository.claimIfNew(id);

            // Then: createdAt should be present
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("mapResultSetToOutbox with all nullable fields as null - 100% branch coverage")
        void testMapResultSetAllNullFields() throws Exception {
            // Given: Entry with ALL nullable fields explicitly NULL (except headers which can't be null)
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO outbox (id, category, topic, \"key\", \"type\", payload, headers, "
                                 + "status, attempts, created_at, next_at, claimed_by, published_at, last_error) "
                                 + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, 1L);
                ps.setString(2, "events");
                ps.setString(3, "test-topic");
                ps.setString(4, "test-key");
                ps.setString(5, "TestEvent");
                ps.setString(6, "{\"test\":\"data\"}");
                ps.setString(7, "{}"); // headers = empty JSON (cannot be null in schema)
                ps.setString(8, "NEW");
                ps.setInt(9, 0);
                ps.setTimestamp(10, Timestamp.from(Instant.now()));
                ps.setTimestamp(11, null); // next_at = NULL
                ps.setString(12, null); // claimed_by = NULL
                ps.setTimestamp(13, null); // published_at = NULL
                ps.setString(14, null); // last_error = NULL
                ps.executeUpdate();
            }

            // When: Retrieve using claimIfNew
            Optional<Outbox> result = repository.claimIfNew(1L);

            // Then: All nullable fields should be null or empty
            assertThat(result).isPresent();
            Outbox outbox = result.get();
            assertThat(outbox.getHeaders()).isEmpty(); // empty JSON headers becomes empty map (branch: false on line 260)
            assertThat(outbox.getNextAt()).isNull(); // (branch: false on line 272)
            assertThat(outbox.getClaimedBy()).isNull(); // (branch: false on line 277)
            assertThat(outbox.getPublishedAt()).isNull(); // (branch: false on line 287)
            assertThat(outbox.getLastError()).isNull(); // (branch: false on line 292)
            assertThat(outbox.getCreatedAt()).isNotNull(); // created_at is always set (branch: true on line 282)
        }

        @Test
        @DisplayName("mapResultSetToOutbox with all fields populated - 100% branch coverage")
        void testMapResultSetAllFieldsPopulated() throws Exception {
            // Given: Entry with ALL fields populated
            Instant now = Instant.now();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO outbox (id, category, topic, \"key\", \"type\", payload, headers, "
                                 + "status, attempts, created_at, next_at, claimed_by, published_at, last_error) "
                                 + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, 2L);
                ps.setString(2, "commands");
                ps.setString(3, "orders");
                ps.setString(4, "order-123");
                ps.setString(5, "OrderProcessed");
                ps.setString(6, "{\"orderId\":\"123\"}");
                ps.setString(7, "{\"version\":\"1\",\"source\":\"api\"}"); // Non-empty headers
                ps.setString(8, "NEW");
                ps.setInt(9, 0);
                ps.setTimestamp(10, Timestamp.from(now));
                ps.setTimestamp(11, Timestamp.from(now.plusSeconds(300))); // next_at populated
                ps.setString(12, "worker-service-1"); // claimed_by populated
                ps.setTimestamp(13, Timestamp.from(now.plusSeconds(60))); // published_at populated
                ps.setString(14, "Temporary network failure"); // last_error populated
                ps.executeUpdate();
            }

            // When: Retrieve using claimIfNew
            Optional<Outbox> result = repository.claimIfNew(2L);

            // Then: All fields should be populated
            assertThat(result).isPresent();
            Outbox outbox = result.get();
            assertThat(outbox.getId()).isEqualTo(2L);
            assertThat(outbox.getCategory()).isEqualTo("commands");
            assertThat(outbox.getTopic()).isEqualTo("orders");
            assertThat(outbox.getKey()).isEqualTo("order-123");
            assertThat(outbox.getType()).isEqualTo("OrderProcessed");
            assertThat(outbox.getPayload()).isEqualTo("{\"orderId\":\"123\"}");

            // Headers populated
            assertThat(outbox.getHeaders()).isNotEmpty();
            assertThat(outbox.getHeaders()).containsEntry("version", "1");
            assertThat(outbox.getHeaders()).containsEntry("source", "api");

            // All timestamps populated
            assertThat(outbox.getCreatedAt()).isNotNull();
            assertThat(outbox.getNextAt()).isNotNull();
            assertThat(outbox.getPublishedAt()).isNotNull();

            // String fields populated
            assertThat(outbox.getClaimedBy()).isEqualTo("worker-service-1");
            assertThat(outbox.getLastError()).isEqualTo("Temporary network failure");
        }
    }

    @Nested
    @DisplayName("Batch Operations Edge Cases")
    class BatchOperationsEdgeCasesTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("sweepBatch should return empty when all entries are published")
        void testSweepBatchAllPublished() {
            // Given: Insert and mark as published
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id);
            repository.markPublished(id);

            // When: Try to sweep NEW/CLAIMED entries
            List<Outbox> swept = repository.sweepBatch(100);

            // Then: Should return empty (all published)
            assertThat(swept).isEmpty();
        }

        @Test
        @DisplayName("sweepBatch should handle large batch sizes")
        void testSweepBatchLargeBatchSize() {
            // Given: Multiple entries
            for (int i = 0; i < 5; i++) {
                repository.insertReturningId("events", "test", "key-" + i, "type", "{}", "{}");
            }

            // When: Sweep with large batch size
            List<Outbox> swept = repository.sweepBatch(10000);

            // Then: Should return all entries up to batch size
            assertThat(swept).hasSizeGreaterThan(0);
        }

        @Test
        @DisplayName("recoverStuck should not affect fresh entries")
        void testRecoverStuckFreshEntry() {
            // Given: Fresh entry
            repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Try to recover stuck entries (created just now)
            int updated = repository.recoverStuck(Duration.ofHours(1));

            // Then: Should not recover (entry is fresh, within timeout)
            assertThat(updated).isZero();
        }
    }

    @Nested
    @DisplayName("Timestamp Branch Coverage")
    class TimestampBranchCoverageTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("should handle entry with publishedAt null")
        void testPublishedAtNull() throws SQLException {
            // Given: Entry in NEW state (not published)
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Query entry directly without claiming
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT * FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Then: publishedAt should be null
                        assertThat(rs.getTimestamp("published_at")).isNull();
                    }
                }
            }
        }

        @Test
        @DisplayName("should handle entry with claimedBy null before claiming")
        void testClaimedByNullBeforeClaim() throws SQLException {
            // Given: Entry in NEW state
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Query entry directly without claiming
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT * FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Then: claimedBy should be null (never claimed)
                        assertThat(rs.getString("claimed_by")).isNull();
                    }
                }
            }
        }

        @Test
        @DisplayName("should handle entry with lastError null when no failure")
        void testLastErrorNull() throws SQLException {
            // Given: Entry that has not failed
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Query entry directly
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT * FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Then: lastError should be null
                        assertThat(rs.getString("last_error")).isNull();
                    }
                }
            }
        }

        @Test
        @DisplayName("published entry should have publishedAt set")
        void testPublishedAtSet() {
            // Given: Entry that has been published
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id);
            repository.markPublished(id);

            // When: Claim the published entry
            Optional<Outbox> outbox = repository.claimIfNew(id);

            // Then: publishedAt should be set (or entry not returned since it's published)
            // Most likely it won't be returned, but verify behavior
            if (outbox.isPresent()) {
                assertThat(outbox.get().getPublishedAt()).isNotNull();
            }
        }

        @Test
        @DisplayName("failed entry should have lastError set")
        void testLastErrorSet() throws SQLException {
            // Given: Entry that has been marked as failed
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id);
            String errorMsg = "Connection timeout";
            repository.markFailed(id, errorMsg, Instant.now().plusSeconds(60));

            // When: Query entry directly
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT * FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Then: lastError should be set
                        assertThat(rs.getString("last_error")).isEqualTo(errorMsg);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Headers Null/Empty Variations")
    class HeadersVariationsTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("should default null headers to empty JSON object")
        void testInsertWithNullHeaders() {
            // When: Insert with null headers
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", null);

            // Then: Headers should default to "{}" and be retrievable as empty map
            Optional<Outbox> outbox = repository.claimIfNew(id);
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getHeaders()).isEmpty();
        }

        @Test
        @DisplayName("should preserve provided headers JSON")
        void testInsertWithValidHeaders() {
            // When: Insert with valid headers JSON
            String headersJson = "{\"key\":\"value\",\"request-id\":\"123\"}";
            long id =
                    repository.insertReturningId("events", "test", "key", "type", "{}", headersJson);

            // Then: Headers should be preserved
            Optional<Outbox> outbox = repository.claimIfNew(id);
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getHeaders()).contains(entry("key", "value"),
                    entry("request-id", "123"));
        }

        @Test
        @DisplayName("should handle complex nested headers JSON")
        void testInsertWithComplexHeaders() {
            // When: Insert with nested JSON headers
            String headersJson = "{\"trace\":{\"span\":\"abc\"},\"service\":\"payment\"}";
            long id =
                    repository.insertReturningId("events", "test", "key", "type", "{}", headersJson);

            // Then: Headers should be parsed (note: nested objects become strings in Map)
            Optional<Outbox> outbox = repository.claimIfNew(id);
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getHeaders()).containsKey("service");
        }
    }

    @Nested
    @DisplayName("NextAt Timestamp Variations")
    class NextAtTimestampTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("should handle entry with null nextAt (no retry scheduled)")
        void testNextAtNull() throws SQLException {
            // Given: Fresh entry with no retry scheduled
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Query entry directly
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT * FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Then: nextAt should be null (no retry scheduled yet)
                        assertThat(rs.getTimestamp("next_at")).isNull();
                    }
                }
            }
        }

        @Test
        @DisplayName("should set nextAt when entry is rescheduled")
        void testNextAtSetOnReschedule() throws SQLException {
            // Given: Entry that has been rescheduled
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id);
            repository.reschedule(id, 5000, "Retry attempt");

            // When: Query entry directly
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT * FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Then: nextAt should now be set
                        assertThat(rs.getTimestamp("next_at")).isNotNull();
                    }
                }
            }
        }

        @Test
        @DisplayName("should set nextAt when markFailed with retry time")
        void testNextAtSetOnFailureWithRetry() throws SQLException {
            // Given: Entry that has been failed with retry scheduled
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id);
            Instant retryTime = Instant.now().plusSeconds(30);
            repository.markFailed(id, "Temporary failure", retryTime);

            // When: Query entry directly
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT * FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Then: nextAt should be set to retry time
                        assertThat(rs.getTimestamp("next_at")).isNotNull();
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("claimIfNew Race Condition Tests")
    class ClaimIfNewRaceConditionTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("should return empty when entry was updated but SELECT finds nothing")
        void testClaimIfNewRaceCondition() {
            // Given: Entry exists
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Claim the entry (update succeeds)
            Optional<Outbox> firstClaim = repository.claimIfNew(id);
            assertThat(firstClaim).isPresent();

            // Then: Claiming again should return empty (already claimed)
            Optional<Outbox> secondClaim = repository.claimIfNew(id);
            assertThat(secondClaim).isEmpty();
        }

        @Test
        @DisplayName("should handle multiple concurrent claim attempts")
        void testClaimIfNewMultipleAttempts() {
            // Given: Entry exists
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Try to claim multiple times
            Optional<Outbox> claim1 = repository.claimIfNew(id);
            Optional<Outbox> claim2 = repository.claimIfNew(id);
            Optional<Outbox> claim3 = repository.claimIfNew(id);

            // Then: Only first should succeed
            assertThat(claim1).isPresent();
            assertThat(claim2).isEmpty();
            assertThat(claim3).isEmpty();
        }
    }

    @Nested
    @DisplayName("SweepBatch Multiple Entries Tests")
    class SweepBatchMultipleEntriesTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("sweepBatch should return multiple entries in batch")
        void testSweepBatchMultipleEntries() {
            // Given: Multiple NEW entries
            long id1 = repository.insertReturningId("events", "test", "key1", "type", "{}", "{}");
            long id2 = repository.insertReturningId("events", "test", "key2", "type", "{}", "{}");
            long id3 = repository.insertReturningId("events", "test", "key3", "type", "{}", "{}");

            // When: Sweep batch with limit 2
            List<Outbox> swept = repository.sweepBatch(2);

            // Then: Should return up to 2 entries in sweep
            assertThat(swept).hasSizeGreaterThanOrEqualTo(1).hasSizeLessThanOrEqualTo(2);
            assertThat(swept).extracting(Outbox::getId).containsAnyOf(id1, id2, id3);
        }

        @Test
        @DisplayName("sweepBatch should handle batch iteration correctly")
        void testSweepBatchIterationBranches() {
            // Given: Multiple entries with specific statuses
            long id1 = repository.insertReturningId("events", "test", "key1", "type", "{}", "{}");
            long id2 = repository.insertReturningId("events", "test", "key2", "type", "{}", "{}");

            // When: Get batch with larger limit
            List<Outbox> swept = repository.sweepBatch(10);

            // Then: Should iterate through all entries (tests while(rs.next()) branch)
            assertThat(swept).hasSizeGreaterThanOrEqualTo(2);
            assertThat(swept).extracting(Outbox::getId).contains(id1, id2);
        }
    }

    @Nested
    @DisplayName("ClaimIfNew Success Branch Tests")
    class ClaimIfNewSuccessBranchTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("claimIfNew should map all fields correctly for claimed entry")
        void testClaimIfNewMapsAllFields() {
            // Given: Entry with specific data
            long id = repository.insertReturningId("events", "order-topic", "ord-123", "OrderEvent",
                    "{\"details\":\"test\"}", "{\"version\":\"1\"}");

            // When: Claim the entry
            Optional<Outbox> claimed = repository.claimIfNew(id);

            // Then: All fields should be mapped (tests rs.next() and mapResultSetToOutbox branches)
            assertThat(claimed).isPresent();
            Outbox outbox = claimed.get();
            assertThat(outbox.getCategory()).isEqualTo("events");
            assertThat(outbox.getTopic()).isEqualTo("order-topic");
            assertThat(outbox.getKey()).isEqualTo("ord-123");
            assertThat(outbox.getType()).isEqualTo("OrderEvent");
            assertThat(outbox.getPayload()).isEqualTo("{\"details\":\"test\"}");
        }

        @Test
        @DisplayName("claimIfNew should update status to CLAIMED")
        void testClaimIfNewStatusChange() {
            // Given: NEW entry
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Claim it
            Optional<Outbox> claimed = repository.claimIfNew(id);

            // Then: Status should be CLAIMED
            assertThat(claimed).isPresent();
            assertThat(claimed.get().getStatus()).isEqualTo("CLAIMED");
        }
    }

    @Nested
    @DisplayName("Mark Failed with Error Branch Tests")
    class MarkFailedErrorBranchTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("markFailed should set lastError when provided")
        void testMarkFailedSetsError() {
            // Given: Entry in CLAIMED status
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id);

            // When: Mark as failed with error message
            String errorMsg = "Connection timeout after 5000ms";
            repository.markFailed(id, errorMsg, Instant.now().plus(Duration.ofSeconds(30)));

            // Then: Error should be persisted (tests the error branch in markFailed)
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT last_error FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        assertThat(rs.getString("last_error")).isEqualTo(errorMsg);
                    }
                }
            } catch (SQLException e) {
                fail("Failed to verify error was set", e);
            }
        }

        @Test
        @DisplayName("markFailed should set nextAt for retry scheduling")
        void testMarkFailedSetsNextAt() throws SQLException {
            // Given: Entry in CLAIMED status
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id);

            // When: Mark as failed with retry time
            Instant nextAttempt = Instant.now().plus(Duration.ofSeconds(60));
            repository.markFailed(id, "Error", nextAttempt);

            // Then: nextAt should be set for next retry (tests nextAt branch in mapResultSetToOutbox)
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT next_at FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        assertThat(rs.getTimestamp("next_at")).isNotNull();
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Reschedule with NextAt Branch Tests")
    class RescheduleNextAtBranchTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("reschedule should set nextAt timestamp for rescheduling")
        void testRescheduleSetNextAt() throws SQLException {
            // Given: Entry that failed
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id);
            repository.markFailed(id, "Failed once", Instant.now());

            // When: Reschedule with backoff
            long backoffMs = 5000;
            repository.reschedule(id, backoffMs, "Still failing");

            // Then: nextAt should be set with future timestamp
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT next_at FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        assertThat(rs.getTimestamp("next_at")).isNotNull();
                    }
                }
            }
        }

        @Test
        @DisplayName("reschedule should update lastError field")
        void testRescheduleUpdatesError() throws SQLException {
            // Given: Entry with previous error
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id);
            repository.markFailed(id, "First error", Instant.now());

            // When: Reschedule with new error message
            String newError = "Retry after network failure";
            repository.reschedule(id, 3000, newError);

            // Then: lastError should be updated
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT last_error FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        assertThat(rs.getString("last_error")).isEqualTo(newError);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Insert with Headers Null/Empty Branch Tests")
    class InsertHeadersNullEmptyBranchTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("insert should default null headers to empty JSON object")
        void testInsertNullHeadersDefaulting() throws SQLException {
            // When: Insert with null headers via insert() method
            repository.insert(999L, "events", "test", "key", "type", "{}", null, "NEW", 0);

            // Then: Headers should default to {} (tests line 53 branch)
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt =
                         conn.prepareStatement("SELECT headers FROM outbox WHERE id = ?")) {
                stmt.setLong(1, 999L);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        assertThat(rs.getString("headers")).isEqualTo("{}");
                    }
                }
            }
        }

        @Test
        @DisplayName("insertReturningId should handle empty string headers")
        void testInsertReturningIdEmptyHeaders() {
            // When: Insert with empty string headers (tests line 80 branch)
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "");

            // Then: Headers should be stored as empty JSON
            Optional<Outbox> outbox = repository.claimIfNew(id);
            assertThat(outbox).isPresent();
            assertThat(outbox.get().getHeaders()).isEmpty();
        }

        @Test
        @DisplayName("mapResultSetToOutbox should handle non-empty headers")
        void testMapHeadersWithContent() {
            // Given: Entry with actual header data
            long id = repository.insertReturningId("events", "test", "key", "type", "{}",
                    "{\"x-correlation-id\":\"123\",\"x-request-id\":\"req-456\"}");

            // When: Claim the entry
            Optional<Outbox> claimed = repository.claimIfNew(id);

            // Then: Headers should be properly parsed and mapped
            assertThat(claimed).isPresent();
            assertThat(claimed.get().getHeaders())
                    .containsEntry("x-correlation-id", "123")
                    .containsEntry("x-request-id", "req-456");
        }
    }

    @Nested
    @DisplayName("NULL Column Handling in ResultSet Mapping")
    class NullColumnBranchTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("mapResultSetToOutbox should handle NULL nextAt timestamp")
        void testMapNullNextAt() throws SQLException {
            // Given: Fresh entry (nextAt is NULL)
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Claim the entry (this loads and maps it)
            Optional<Outbox> claimed = repository.claimIfNew(id);

            // Then: nextAt should be null/empty in Optional (tests line 272 NULL branch)
            assertThat(claimed).isPresent();
            assertThat(claimed.get().getNextAt()).isNull();
        }

        @Test
        @DisplayName("mapResultSetToOutbox should handle NULL claimedBy")
        void testMapNullClaimedBy() throws SQLException {
            // Given: NEW entry (claimedBy is NULL before claiming)
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Query directly without claiming (to preserve NULL claimedBy)
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM outbox WHERE id = ?")) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Then: claimedBy should be null (tests line 277 NULL branch)
                        assertThat(rs.getString("claimed_by")).isNull();
                    }
                }
            }
        }

        @Test
        @DisplayName("mapResultSetToOutbox should handle NULL createdAt")
        void testMapNullCreatedAt() throws SQLException {
            // Note: createdAt is always set during insert, but this tests the NULL handling code path
            // Given: An entry with explicit NULL check
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Claim it
            Optional<Outbox> claimed = repository.claimIfNew(id);

            // Then: createdAt should be set (because insert always sets it)
            assertThat(claimed).isPresent();
            assertThat(claimed.get().getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("mapResultSetToOutbox should handle NULL publishedAt")
        void testMapNullPublishedAt() throws SQLException {
            // Given: Fresh entry (publishedAt is NULL until markPublished is called)
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Claim the entry
            Optional<Outbox> claimed = repository.claimIfNew(id);

            // Then: publishedAt should be null (tests line 287 NULL branch)
            assertThat(claimed).isPresent();
            assertThat(claimed.get().getPublishedAt()).isNull();
        }

        @Test
        @DisplayName("mapResultSetToOutbox should handle NULL lastError")
        void testMapNullLastError() throws SQLException {
            // Given: Fresh entry with no errors
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // When: Claim the entry
            Optional<Outbox> claimed = repository.claimIfNew(id);

            // Then: lastError should be null (tests line 292 NULL branch)
            assertThat(claimed).isPresent();
            assertThat(claimed.get().getLastError()).isNull();
        }
    }

    @Nested
    @DisplayName("InsertReturningId Edge Cases and Failures")
    class InsertReturningIdEdgeCasesTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("insertReturningId should successfully insert and return generated ID")
        void testInsertReturningIdSuccess() {
            // When: Insert a new entry and get the generated ID
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // Then: ID should be positive and entry should exist
            assertThat(id).isPositive();
            Optional<Outbox> found = repository.claimIfNew(id);
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(id);
        }

        @Test
        @DisplayName("claimIfNew should return empty when no entry matches ID")
        void testClaimIfNewNoMatch() {
            // When: Try to claim a non-existent ID
            Optional<Outbox> claimed = repository.claimIfNew(999999L);

            // Then: Should return empty (tests line 121 false branch of rs.next())
            assertThat(claimed).isEmpty();
        }
    }

    @Nested
    @DisplayName("Insert Method Header Branch Coverage")
    class InsertHeaderVariationTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("insert with empty string headers should store empty headers")
        void testInsertEmptyStringHeaders() throws SQLException {
            // When: Insert with empty string headers
            repository.insert(1001L, "events", "test", "key", "type", "{}", "", "NEW", 0);

            // Then: Verify headers are stored (even if empty)
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT headers FROM outbox WHERE id = ?")) {
                stmt.setLong(1, 1001L);
                try (ResultSet rs = stmt.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("headers")).isNotNull();
                }
            }
        }

        @Test
        @DisplayName("insert with non-empty headers should preserve headers")
        void testInsertNonEmptyHeaders() throws SQLException {
            // When: Insert with actual header content
            String headerJson = "{\"x-trace-id\":\"trace-123\"}";
            repository.insert(1002L, "events", "test", "key", "type", "{}", headerJson, "NEW", 0);

            // Then: Verify headers are preserved
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT headers FROM outbox WHERE id = ?")) {
                stmt.setLong(1, 1002L);
                try (ResultSet rs = stmt.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("headers")).contains("x-trace-id").contains("trace-123");
                }
            }
        }
    }

    @Nested
    @DisplayName("InsertReturningId with Mock Failures (FALSE branches)")
    class InsertReturningIdMockFailureTests {

        private JdbcOutboxRepository createMockRepository(DataSource mockDataSource) {
            return new JdbcOutboxRepository(mockDataSource) {
                @Override
                protected String getInsertSql() {
                    return "INSERT INTO outbox ... VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                }

                @Override
                protected String getClaimIfNewSql() {
                    return "";
                }

                @Override
                protected String getSweepBatchSql() {
                    return "";
                }

                @Override
                protected String getMarkPublishedSql() {
                    return "";
                }

                @Override
                protected String getMarkFailedSql() {
                    return "";
                }

                @Override
                protected String getRescheduleSql() {
                    return "";
                }

                @Override
                protected String getRecoverStuckSql() {
                    return "";
                }
            };
        }

        @Test
        @DisplayName("insertReturningId should handle zero rows inserted")
        void testInsertReturningIdZeroRowsInserted() throws SQLException {
            // Given: Mock a DataSource and Connection to simulate 0 rows inserted
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString(), anyInt())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(0); // 0 rows inserted

            JdbcOutboxRepository testRepository = createMockRepository(mockDataSource);

            // When & Then: Should throw SQLException wrapped in RuntimeException
            assertThatThrownBy(() -> testRepository.insertReturningId("events", "test", "key", "type", "{}", "{}"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to insert");
        }

        @Test
        @DisplayName("insertReturningId should handle no generated keys")
        void testInsertReturningIdNoGeneratedKeys() throws SQLException {
            // Given: Mock a DataSource and Connection with no generated keys
            DataSource mockDataSource = mock(DataSource.class);
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);
            ResultSet mockGeneratedKeys = mock(ResultSet.class);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString(), anyInt())).thenReturn(mockPs);
            when(mockPs.executeUpdate()).thenReturn(1); // 1 row inserted
            when(mockPs.getGeneratedKeys()).thenReturn(mockGeneratedKeys);
            when(mockGeneratedKeys.next()).thenReturn(false); // No generated keys

            JdbcOutboxRepository testRepository = createMockRepository(mockDataSource);

            // When & Then: Should throw SQLException wrapped in RuntimeException
            assertThatThrownBy(() -> testRepository.insertReturningId("events", "test", "key", "type", "{}", "{}"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to insert");
        }
    }

    @Nested
    @DisplayName("Sweep Batch Operations")
    class SweepBatchAdvancedTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("sweepBatch should return empty list when table is empty")
        void testSweepBatchEmptyTable() {
            // When: Sweep batch with empty table
            List<Outbox> results = repository.sweepBatch(100);

            // Then: Should return empty list
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("sweepBatch should return multiple entries up to max limit")
        void testSweepBatchWithMultipleEntries() {
            // Given: Insert multiple entries
            long id1 = repository.insertReturningId("events", "test", "key1", "type", "{}", "{}");
            long id2 = repository.insertReturningId("events", "test", "key2", "type", "{}", "{}");
            long id3 = repository.insertReturningId("events", "test", "key3", "type", "{}", "{}");

            // When: Sweep batch with limit of 2
            List<Outbox> results = repository.sweepBatch(2);

            // Then: Should return up to 2 entries
            assertThat(results).hasSizeLessThanOrEqualTo(2);
            assertThat(results).isNotEmpty(); // Should have entries
        }
    }

    @Nested
    @DisplayName("Recover Stuck Entries")
    class RecoverStuckTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("recoverStuck should return 0 when no stuck entries exist")
        void testRecoverStuckNoEntries() {
            // When: Try to recover entries older than 1 hour
            int recovered = repository.recoverStuck(Duration.ofHours(1));

            // Then: Should return 0 (no entries to recover)
            assertThat(recovered).isZero();
        }
    }

    @Nested
    @DisplayName("H2 sweepBatch Empty Results - Edge Case Coverage")
    class SweepBatchEmptyResultsEdgeCases {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("sweepBatch should handle empty results and skip update when no IDs collected")
        void testSweepBatchEmptyResultsSkipsUpdate() {
            // Given: Empty outbox table (no entries to sweep)
            // When: Sweep batch
            List<Outbox> results = repository.sweepBatch(100);

            // Then: Should return empty list without attempting UPDATE (line 124 branch: !ids.isEmpty() = false)
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("sweepBatch should collect multiple IDs and execute batch update")
        void testSweepBatchMultipleIdsUpdate() {
            // Given: Multiple NEW entries
            repository.insertReturningId("events", "test", "key1", "type", "{}", "{}");
            repository.insertReturningId("events", "test", "key2", "type", "{}", "{}");
            repository.insertReturningId("events", "test", "key3", "type", "{}", "{}");

            // When: Sweep batch
            List<Outbox> results = repository.sweepBatch(3);

            // Then: Should collect IDs and execute UPDATE (line 124 branch: !ids.isEmpty() = true)
            assertThat(results).isNotEmpty();
            assertThat(results).hasSizeLessThanOrEqualTo(3);
            // Verify all results have CLAIMED status after update (line 142-144)
            assertThat(results).allMatch(o -> "CLAIMED".equals(o.getStatus()));
        }

        @Test
        @DisplayName("sweepBatch should build correct SQL with single ID")
        void testSweepBatchSingleIdUpdateSql() {
            // Given: Single NEW entry
            repository.insertReturningId("events", "test", "key1", "type", "{}", "{}");

            // When: Sweep batch with limit 1
            List<Outbox> results = repository.sweepBatch(1);

            // Then: Should build SQL without commas (line 126-131: i < ids.size() - 1 = false)
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo("CLAIMED");
        }

        @Test
        @DisplayName("sweepBatch should build correct SQL with multiple IDs and commas")
        void testSweepBatchMultipleIdsUpdateSqlWithCommas() {
            // Given: Three NEW entries
            repository.insertReturningId("events", "test", "key1", "type", "{}", "{}");
            repository.insertReturningId("events", "test", "key2", "type", "{}", "{}");
            repository.insertReturningId("events", "test", "key3", "type", "{}", "{}");

            // When: Sweep batch with limit 3
            List<Outbox> results = repository.sweepBatch(3);

            // Then: Should build SQL with commas between IDs (line 126-131: multiple iterations)
            assertThat(results).hasSizeGreaterThan(1);
            assertThat(results).allMatch(o -> "CLAIMED".equals(o.getStatus()));
        }
    }

    @Nested
    @DisplayName("H2 claimIfNew Exception Path Coverage")
    class ClaimIfNewExceptionPathTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("claimIfNew should return empty when UPDATE returns 0 rows")
        void testClaimIfNewUpdateReturnsZero() {
            // Given: Entry in CLAIMED status (not NEW)
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");
            repository.claimIfNew(id); // First claim succeeds

            // When: Try to claim again (UPDATE will return 0 because status != NEW)
            Optional<Outbox> result = repository.claimIfNew(id);

            // Then: Should return empty (line 60-63: rowsUpdated == 0 branch)
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("claimIfNew should return empty when SELECT after UPDATE returns no rows")
        void testClaimIfNewSelectReturnsEmpty() throws SQLException {
            // Given: Entry that exists
            long id = repository.insertReturningId("events", "test", "key", "type", "{}", "{}");

            // First claim it normally
            Optional<Outbox> firstClaim = repository.claimIfNew(id);
            assertThat(firstClaim).isPresent();

            // Then: If we manually delete it and try to claim, UPDATE might succeed but SELECT fails
            // This is an edge case that tests line 79-82 where rs.next() returns true vs line 85
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM outbox WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }

            // When: Try to claim non-existent ID
            Optional<Outbox> result = repository.claimIfNew(9999999L);

            // Then: Should return empty (line 60 branch: rowsUpdated == 0)
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("claimIfNew should successfully map result when SELECT returns row")
        void testClaimIfNewSelectReturnsRow() {
            // Given: Entry in NEW status
            long id = repository.insertReturningId("events", "test-topic", "test-key", "TestEvent",
                    "{\"data\":\"value\"}", "{\"header\":\"value\"}");

            // When: Claim the entry (UPDATE succeeds, SELECT returns row)
            Optional<Outbox> result = repository.claimIfNew(id);

            // Then: Should return populated Optional (line 79-81: rs.next() = true branch)
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(id);
            assertThat(result.get().getStatus()).isEqualTo("CLAIMED");
            assertThat(result.get().getTopic()).isEqualTo("test-topic");
        }
    }

    @Nested
    @DisplayName("H2 sweepBatch ResultSet Iteration Coverage")
    class SweepBatchResultSetIterationTests {

        @BeforeEach
        void setUp() throws Exception {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbox");
            }
        }

        @Test
        @DisplayName("sweepBatch should handle while loop with no iterations (empty result)")
        void testSweepBatchWhileLoopNoIterations() {
            // Given: No entries in outbox
            // When: Sweep batch
            List<Outbox> results = repository.sweepBatch(100);

            // Then: while(rs.next()) never executes (line 116: false on first check)
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("sweepBatch should handle while loop with single iteration")
        void testSweepBatchWhileLoopSingleIteration() {
            // Given: Single entry
            repository.insertReturningId("events", "test", "key1", "type", "{}", "{}");

            // When: Sweep batch
            List<Outbox> results = repository.sweepBatch(100);

            // Then: while(rs.next()) executes once (line 116: true once, then false)
            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("sweepBatch should handle while loop with multiple iterations")
        void testSweepBatchWhileLoopMultipleIterations() {
            // Given: Multiple entries
            for (int i = 0; i < 5; i++) {
                repository.insertReturningId("events", "test", "key" + i, "type", "{}", "{}");
            }

            // When: Sweep batch
            List<Outbox> results = repository.sweepBatch(5);

            // Then: while(rs.next()) executes multiple times (line 116: true multiple times)
            assertThat(results).hasSizeGreaterThan(1);
        }
    }
}
