package com.acme.reliable.persistence.jdbc.process;

import static org.assertj.core.api.Assertions.*;

import com.acme.reliable.persistence.jdbc.PostgresProcessRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
  @DisplayName("Insert SQL should use JSONB cast for process data")
  void testInsertSql() {
    String sql = invokeProtectedMethod("getInsertSql");

    assertThat(sql)
        .as("Should use PostgreSQL JSONB cast")
        .contains("INSERT INTO process")
        .contains("(process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at)")
        .contains("VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)")
        .contains("?::jsonb");
  }

  @Test
  @DisplayName("Find by ID SQL should select all process fields")
  void testFindByIdSql() {
    String sql = invokeProtectedMethod("getFindByIdSql");

    assertThat(sql)
        .contains("SELECT")
        .contains("process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at")
        .contains("FROM process")
        .contains("WHERE process_id = ?");
  }

  @Test
  @DisplayName("Find by status SQL should order by creation date descending with limit")
  void testFindByStatusSql() {
    String sql = invokeProtectedMethod("getFindByStatusSql");

    assertThat(sql)
        .contains("SELECT")
        .contains("process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at")
        .contains("FROM process")
        .contains("WHERE status = ?")
        .contains("ORDER BY created_at DESC")
        .contains("LIMIT ?");
  }

  @Test
  @DisplayName("Find by type and status SQL should filter by both process type and status")
  void testFindByTypeAndStatusSql() {
    String sql = invokeProtectedMethod("getFindByTypeAndStatusSql");

    assertThat(sql)
        .contains("SELECT")
        .contains("process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at")
        .contains("FROM process")
        .contains("WHERE process_type = ? AND status = ?")
        .contains("ORDER BY created_at DESC")
        .contains("LIMIT ?");
  }

  @Test
  @DisplayName("Update SQL should use JSONB cast for data")
  void testUpdateSql() {
    String sql = invokeProtectedMethod("getUpdateSql");

    assertThat(sql)
        .contains("UPDATE process")
        .contains("SET status = ?, current_step = ?, data = ?::jsonb, retries = ?, updated_at = ?")
        .contains("?::jsonb")
        .contains("WHERE process_id = ?");
  }

  @Test
  @DisplayName("Log query SQL should retrieve logs ordered by sequence descending")
  void testLogQuerySql() {
    String sql = invokeProtectedMethod("getLogQuerySql");

    assertThat(sql)
        .contains("SELECT")
        .contains("process_id, seq, at, event")
        .contains("FROM process_log")
        .contains("WHERE process_id = ?")
        .contains("ORDER BY seq DESC")
        .contains("LIMIT ?");
  }

  @Test
  @DisplayName("Find by business key SQL should query by process type and business key")
  void testFindByBusinessKeySql() {
    String sql = invokeProtectedMethod("getFindByBusinessKeySql");

    assertThat(sql)
        .contains("SELECT")
        .contains("process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at")
        .contains("FROM process")
        .contains("WHERE process_type = ? AND business_key = ?");
  }

  @Test
  @DisplayName("Insert log entry SQL should use JSONB cast for event")
  void testInsertLogEntrySql() {
    String sql = invokeProtectedMethod("getInsertLogEntrySql");

    assertThat(sql)
        .contains("INSERT INTO process_log")
        .contains("(process_id, event)")
        .contains("VALUES (?, ?::jsonb)")
        .contains("?::jsonb");
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
