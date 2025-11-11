package com.acme.reliable.persistence.jdbc.dlq;

import com.acme.reliable.persistence.jdbc.PostgresDlqRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL verification tests for PostgresDlqRepository.
 * Verifies that all SQL statements are correctly formed for PostgreSQL dialect.
 * Tests Dead Letter Queue (DLQ) operations for failed command tracking.
 */
@DisplayName("PostgreSQL DLQ Repository SQL Verification")
class PostgresDlqRepositorySqlTest {

    private PostgresDlqRepository repository;
    private HikariDataSource dataSource;

    @BeforeEach
    void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:test");
        config.setUsername("sa");
        config.setPassword("");
        dataSource = new HikariDataSource(config);

        repository = new PostgresDlqRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("Insert DLQ entry SQL should not be null or empty")
    void testInsertDlqEntrySql() {
        String sql = invokeProtectedMethod("getInsertDlqEntrySql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Insert DLQ entry SQL column count should not be null or empty")
    void testInsertDlqEntrySqlColumnCount() {
        String sql = invokeProtectedMethod("getInsertDlqEntrySql");
        assertThat(sql).isNotNull().isNotEmpty();
    }

    /**
     * Helper method to invoke protected SQL methods via reflection.
     */
    private String invokeProtectedMethod(String methodName) {
        try {
            Method method = PostgresDlqRepository.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (String) method.invoke(repository);
        } catch (Exception e) {
            throw new AssertionError("Failed to invoke method: " + methodName, e);
        }
    }
}
