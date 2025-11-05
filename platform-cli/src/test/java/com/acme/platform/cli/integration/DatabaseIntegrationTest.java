package com.acme.platform.cli.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class DatabaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Test
    void testPostgresContainer_isRunning() {
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    void testPostgresContainer_canConnect() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword())) {

            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
        }
    }

    @Test
    void testPostgresContainer_canCreateTable() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword())) {

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test_command (id INT PRIMARY KEY, status VARCHAR(20))");
                stmt.execute("INSERT INTO test_command VALUES (1, 'PENDING'), (2, 'COMPLETED')");

                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM test_command")) {
                    rs.next();
                    assertThat(rs.getInt("count")).isEqualTo(2);
                }
            }
        }
    }

    @Test
    void testPostgresContainer_canQueryWithPagination() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword())) {

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE paginated_test (id INT PRIMARY KEY, value VARCHAR(50))");

                // Insert 50 records
                for (int i = 1; i <= 50; i++) {
                    stmt.execute(String.format("INSERT INTO paginated_test VALUES (%d, 'value-%d')", i, i));
                }

                // Query with LIMIT and OFFSET (pagination)
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM paginated_test LIMIT 20 OFFSET 0")) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                    }
                    assertThat(count).isEqualTo(20);
                }

                // Second page
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM paginated_test LIMIT 20 OFFSET 20")) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                    }
                    assertThat(count).isEqualTo(20);
                }
            }
        }
    }

    @Test
    void testPostgresContainer_tableMetadata() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword())) {

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE metadata_test (id INT, name VARCHAR(50), created_at TIMESTAMP)");

                // Query table metadata
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT column_name, data_type FROM information_schema.columns " +
                        "WHERE table_name = 'metadata_test' ORDER BY ordinal_position")) {

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("column_name")).isEqualTo("id");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("column_name")).isEqualTo("name");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("column_name")).isEqualTo("created_at");
                }
            }
        }
    }
}
