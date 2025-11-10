package com.acme.reliable.persistence.jdbc.dlq;

import com.acme.reliable.persistence.jdbc.H2DlqRepository;
import com.acme.reliable.persistence.jdbc.H2RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Integration tests for H2-based DLQ (Dead Letter Queue) Repository.
 * Tests parking of failed commands with comprehensive failure details.
 */
class H2DlqRepositoryTest extends H2RepositoryTestBase {

    private H2DlqRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new H2DlqRepository(dataSource);

        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("DELETE FROM command_dlq");
        }
    }

    @Test
    @DisplayName("insertDlqEntry should insert with all failure details")
    void testInsertDlqEntry() {
        // Given
        UUID dlqId = UUID.randomUUID();
        String commandName = "ProcessPayment";
        String businessKey = "order-12345";
        String payload = "{\"amount\":100,\"currency\":\"USD\"}";
        String failedStatus = "FAILED";
        String errorClass = "java.net.ConnectException";
        String errorMessage = "Connection refused: unable to connect to payment service";
        int attempts = 5;
        String parkedBy = "payment-processor-1";

        // When
        repository.insertDlqEntry(dlqId, commandName, businessKey, payload,
                failedStatus, errorClass, errorMessage, attempts, parkedBy);

        // Then
        try (Connection conn = dataSource.getConnection()) {
            var pstmt = conn.prepareStatement(
                    "SELECT * FROM command_dlq WHERE id = ?");
            pstmt.setObject(1, dlqId.toString());
            ResultSet rs = pstmt.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("command_name")).isEqualTo(commandName);
            assertThat(rs.getString("business_key")).isEqualTo(businessKey);
            assertThat(rs.getString("error_class")).isEqualTo(errorClass);
            assertThat(rs.getString("error_message")).isEqualTo(errorMessage);
            assertThat(rs.getInt("attempts")).isEqualTo(attempts);
            assertThat(rs.getString("parked_by")).isEqualTo(parkedBy);
            assertThat(rs.getTimestamp("parked_at")).isNotNull();
        } catch (Exception e) {
            fail("Failed to verify DLQ entry", e);
        }
    }

    @Test
    @DisplayName("should handle long error messages")
    void testLongErrorMessage() {
        // Given
        UUID dlqId = UUID.randomUUID();
        String longError = "Error: " + "x".repeat(1000);

        // When
        repository.insertDlqEntry(dlqId, "Command", "key", "{}",
                "FAILED", "RuntimeException", longError, 3, "worker");

        // Then
        try (Connection conn = dataSource.getConnection()) {
            var pstmt = conn.prepareStatement(
                    "SELECT error_message FROM command_dlq WHERE id = ?");
            pstmt.setObject(1, dlqId.toString());
            ResultSet rs = pstmt.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("error_message")).isEqualTo(longError);
        } catch (Exception e) {
            fail("Failed to verify long error message", e);
        }
    }

    @Test
    @DisplayName("should handle multiple failed commands")
    void testMultipleDlqEntries() {
        // When
        for (int i = 0; i < 10; i++) {
            repository.insertDlqEntry(UUID.randomUUID(), "Command-" + i,
                    "key-" + i, "{}", "FAILED",
                    "Exception-" + i, "Error message " + i, i, "worker");
        }

        // Then - verify all entries exist
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) as count FROM command_dlq");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("count")).isEqualTo(10);
        } catch (Exception e) {
            fail("Failed to count DLQ entries", e);
        }
    }
}
