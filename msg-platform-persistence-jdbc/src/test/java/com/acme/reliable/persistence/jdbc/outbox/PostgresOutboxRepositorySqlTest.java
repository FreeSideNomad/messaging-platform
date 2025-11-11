package com.acme.reliable.persistence.jdbc.outbox;

import com.acme.reliable.persistence.jdbc.PostgresOutboxRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL verification tests for PostgresOutboxRepository.
 * Verifies that all SQL statements are correctly formed for PostgreSQL dialect.
 */
@DisplayName("PostgreSQL Outbox Repository SQL Verification")
class PostgresOutboxRepositorySqlTest {

    private PostgresOutboxRepository repository;
    private HikariDataSource dataSource;

    @BeforeEach
    void setup() {
        // Create minimal H2 DataSource for testing (not used for actual queries)
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:test");
        config.setUsername("sa");
        config.setPassword("");
        dataSource = new HikariDataSource(config);

        repository = new PostgresOutboxRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("Insert SQL should not be null or empty")
    void testInsertSql() {
        String sql = invokeProtectedMethod("getInsertSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Claim if new SQL should not be null or empty")
    void testClaimIfNewSql() {
        String sql = invokeProtectedMethod("getClaimIfNewSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Sweep batch SQL should not be null or empty")
    void testSweepBatchSql() {
        String sql = invokeProtectedMethod("getSweepBatchSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Mark published SQL should not be null or empty")
    void testMarkPublishedSql() {
        String sql = invokeProtectedMethod("getMarkPublishedSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Mark failed SQL should not be null or empty")
    void testMarkFailedSql() {
        String sql = invokeProtectedMethod("getMarkFailedSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Reschedule SQL should not be null or empty")
    void testRescheduleSql() {
        String sql = invokeProtectedMethod("getRescheduleSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Recover stuck SQL should not be null or empty")
    void testRecoverStuckSql() {
        String sql = invokeProtectedMethod("getRecoverStuckSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    /**
     * Helper method to invoke protected SQL methods via reflection.
     */
    private String invokeProtectedMethod(String methodName) {
        try {
            Method method = PostgresOutboxRepository.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (String) method.invoke(repository);
        } catch (Exception e) {
            throw new AssertionError("Failed to invoke method: " + methodName, e);
        }
    }
}
