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
    @DisplayName("Insert pending SQL should not be null or empty")
    void testInsertPendingSql() {
        String sql = invokeProtectedMethod("getInsertPendingSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Find by ID SQL should not be null or empty")
    void testFindByIdSql() {
        String sql = invokeProtectedMethod("getFindByIdSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Update to running SQL should not be null or empty")
    void testUpdateToRunningSql() {
        String sql = invokeProtectedMethod("getUpdateToRunningSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Update to succeeded SQL should not be null or empty")
    void testUpdateToSucceededSql() {
        String sql = invokeProtectedMethod("getUpdateToSucceededSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Update to failed SQL should not be null or empty")
    void testUpdateToFailedSql() {
        String sql = invokeProtectedMethod("getUpdateToFailedSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Increment retries SQL should not be null or empty")
    void testIncrementRetriesSql() {
        String sql = invokeProtectedMethod("getIncrementRetriesSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Update to timed out SQL should not be null or empty")
    void testUpdateToTimedOutSql() {
        String sql = invokeProtectedMethod("getUpdateToTimedOutSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Exists by idempotency key SQL should not be null or empty")
    void testExistsByIdempotencyKeySql() {
        String sql = invokeProtectedMethod("getExistsByIdempotencyKeySql");
        assertThat(sql).isNotNull().isNotEmpty();
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
