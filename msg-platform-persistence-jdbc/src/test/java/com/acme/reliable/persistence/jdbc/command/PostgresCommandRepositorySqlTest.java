package com.acme.reliable.persistence.jdbc.command;

import com.acme.reliable.persistence.jdbc.PostgresCommandRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL verification tests for PostgresCommandRepository.
 * Verifies that all SQL statements are correctly formed for PostgreSQL dialect.
 */
@DisplayName("PostgreSQL Command Repository SQL Verification")
class PostgresCommandRepositorySqlTest {

    private PostgresCommandRepository repository;
    private HikariDataSource dataSource;

    @BeforeEach
    void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:test");
        config.setUsername("sa");
        config.setPassword("");
        dataSource = new HikariDataSource(config);

        repository = new PostgresCommandRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("Insert pending SQL should insert command with all fields")
    void testInsertPendingSql() {
        String sql = invokeProtectedMethod("getInsertPendingSql");

        assertThat(sql)
                .contains("INSERT INTO command")
                .contains("(id, name, business_key, payload, idempotency_key, status, retries, requested_at, reply)")
                .contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
    }

    @Test
    @DisplayName("Find by ID SQL should select all command fields")
    void testFindByIdSql() {
        String sql = invokeProtectedMethod("getFindByIdSql");

        assertThat(sql)
                .contains("SELECT")
                .contains("id, name, business_key, payload, idempotency_key, status, requested_at, updated_at")
                .contains("retries, processing_lease_until, last_error, reply")
                .contains("FROM command")
                .contains("WHERE id = ?");
    }

    @Test
    @DisplayName("Update to running SQL should set status and processing lease")
    void testUpdateToRunningSql() {
        String sql = invokeProtectedMethod("getUpdateToRunningSql");

        assertThat(sql)
                .contains("UPDATE command")
                .contains("SET status = ?, processing_lease_until = ?, updated_at = ?")
                .contains("WHERE id = ?");
    }

    @Test
    @DisplayName("Update to succeeded SQL should set succeeded status")
    void testUpdateToSucceededSql() {
        String sql = invokeProtectedMethod("getUpdateToSucceededSql");

        assertThat(sql)
                .contains("UPDATE command")
                .contains("SET status = ?, updated_at = ?")
                .contains("WHERE id = ?");
    }

    @Test
    @DisplayName("Update to failed SQL should set failed status and error")
    void testUpdateToFailedSql() {
        String sql = invokeProtectedMethod("getUpdateToFailedSql");

        assertThat(sql)
                .contains("UPDATE command")
                .contains("SET status = ?, last_error = ?, updated_at = ?")
                .contains("WHERE id = ?");
    }

    @Test
    @DisplayName("Increment retries SQL should increment retry count")
    void testIncrementRetriesSql() {
        String sql = invokeProtectedMethod("getIncrementRetriesSql");

        assertThat(sql)
                .contains("UPDATE command")
                .contains("SET retries = retries + 1, last_error = ?, updated_at = ?")
                .contains("WHERE id = ?");
    }

    @Test
    @DisplayName("Update to timed out SQL should set timeout status and error")
    void testUpdateToTimedOutSql() {
        String sql = invokeProtectedMethod("getUpdateToTimedOutSql");

        assertThat(sql)
                .contains("UPDATE command")
                .contains("SET status = ?, last_error = ?, updated_at = ?")
                .contains("WHERE id = ?");
    }

    @Test
    @DisplayName("Exists by idempotency key SQL should check for duplicate commands")
    void testExistsByIdempotencyKeySql() {
        String sql = invokeProtectedMethod("getExistsByIdempotencyKeySql");

        assertThat(sql)
                .contains("SELECT COUNT(*) FROM command WHERE idempotency_key = ?");
    }

    /**
     * Helper method to invoke protected SQL methods via reflection.
     */
    private String invokeProtectedMethod(String methodName) {
        try {
            Method method = PostgresCommandRepository.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (String) method.invoke(repository);
        } catch (Exception e) {
            throw new AssertionError("Failed to invoke method: " + methodName, e);
        }
    }
}
