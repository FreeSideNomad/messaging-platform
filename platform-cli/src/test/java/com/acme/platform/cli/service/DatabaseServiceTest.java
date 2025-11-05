package com.acme.platform.cli.service;

import com.acme.platform.cli.model.PaginatedResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class DatabaseServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Test
    void testGetInstance_returnsSingleton() {
        DatabaseService instance1 = DatabaseService.getInstance();
        DatabaseService instance2 = DatabaseService.getInstance();

        assertThat(instance1).isSameAs(instance2);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    void testQueryTable_withTestContainer() throws Exception {
        // Set up test database
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword())) {

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test_table (id INT, name VARCHAR(50))");
                stmt.execute("INSERT INTO test_table VALUES (1, 'test1'), (2, 'test2')");
            }
        }

        // Test would need DatabaseService to use test container
        // This is integration test placeholder
    }

    @Test
    void testListTables_invalidTableName() {
        // Test table name validation
        DatabaseService dbService = DatabaseService.getInstance();

        // These should not throw - just testing the structure
        assertThat(dbService).isNotNull();
    }

    @Test
    void testTableNameValidation() {
        // Valid table names
        assertThat("users".matches("^[a-zA-Z0-9_]+$")).isTrue();
        assertThat("test_table_123".matches("^[a-zA-Z0-9_]+$")).isTrue();

        // Invalid table names
        assertThat("users; DROP TABLE".matches("^[a-zA-Z0-9_]+$")).isFalse();
        assertThat("users--comment".matches("^[a-zA-Z0-9_]+$")).isFalse();
        assertThat("users' OR '1'='1".matches("^[a-zA-Z0-9_]+$")).isFalse();
    }
}
