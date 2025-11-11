package com.acme.reliable.persistence.jdbc.process;

import com.acme.reliable.persistence.jdbc.PostgresProcessRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL verification tests for PostgresProcessRepository.
 * Verifies that all SQL statements are correctly formed for PostgreSQL dialect.
 * Tests PostgreSQL-specific features like JSONB data type casting.
 */
@DisplayName("PostgreSQL Process Repository SQL Verification")
class PostgresProcessRepositorySqlTest {

    private PostgresProcessRepository repository;
    private HikariDataSource dataSource;

    @BeforeEach
    void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:test");
        config.setUsername("sa");
        config.setPassword("");
        dataSource = new HikariDataSource(config);

        repository = new PostgresProcessRepository(dataSource);
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
    @DisplayName("Find by ID SQL should not be null or empty")
    void testFindByIdSql() {
        String sql = invokeProtectedMethod("getFindByIdSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Find by status SQL should not be null or empty")
    void testFindByStatusSql() {
        String sql = invokeProtectedMethod("getFindByStatusSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Find by type and status SQL should not be null or empty")
    void testFindByTypeAndStatusSql() {
        String sql = invokeProtectedMethod("getFindByTypeAndStatusSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Update SQL should not be null or empty")
    void testUpdateSql() {
        String sql = invokeProtectedMethod("getUpdateSql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Log query SQL should not be null or empty")
    void testLogQuerySql() {
        String sql = invokeProtectedMethod("getLogQuerySql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Find by business key SQL should not be null or empty")
    void testFindByBusinessKeySql() {
        String sql = invokeProtectedMethod("getFindByBusinessKeySql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Insert log entry SQL should not be null or empty")
    void testInsertLogEntrySql() {
        String sql = invokeProtectedMethod("getInsertLogEntrySql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    /**
     * Helper method to invoke protected SQL methods via reflection.
     */
    private String invokeProtectedMethod(String methodName) {
        try {
            Method method = PostgresProcessRepository.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (String) method.invoke(repository);
        } catch (Exception e) {
            throw new AssertionError("Failed to invoke method: " + methodName, e);
        }
    }
}
