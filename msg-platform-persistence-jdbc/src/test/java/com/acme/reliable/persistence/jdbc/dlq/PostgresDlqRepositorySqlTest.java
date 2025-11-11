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
    @DisplayName("Insert DLQ entry SQL should store all failed command details")
    void testInsertDlqEntrySql() {
        String sql = invokeProtectedMethod("getInsertDlqEntrySql");

        assertThat(sql)
                .contains("INSERT INTO platform.command_dlq")
                .contains("(id, command_id, command_name, business_key, payload, failed_status, error_class, error_message, attempts, parked_by, parked_at)")
                .contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    }

    @Test
    @DisplayName("Insert DLQ entry SQL should have correct column count")
    void testInsertDlqEntrySqlColumnCount() {
        String sql = invokeProtectedMethod("getInsertDlqEntrySql");

        // Count the number of columns
        int openParens = countOccurrences(sql, "(");
        int closeParens = countOccurrences(sql, ")");

        assertThat(openParens).as("Open parentheses count").isGreaterThan(0);
        assertThat(closeParens).as("Close parentheses count").isGreaterThan(0);

        // Count placeholders (?)
        int placeholders = countOccurrences(sql, "?");
        assertThat(placeholders).as("Parameter placeholders").isEqualTo(11);
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

    /**
     * Helper method to count occurrences of a substring in a string.
     */
    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
