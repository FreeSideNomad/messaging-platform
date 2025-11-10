package com.acme.reliable.persistence.jdbc.inbox;

import static org.assertj.core.api.Assertions.*;

import com.acme.reliable.persistence.jdbc.PostgresInboxRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SQL verification tests for PostgresInboxRepository.
 * Verifies that all SQL statements are correctly formed for PostgreSQL dialect.
 * Tests PostgreSQL-specific features like ON CONFLICT DO NOTHING for idempotent inserts.
 */
@DisplayName("PostgreSQL Inbox Repository SQL Verification")
class PostgresInboxRepositorySqlTest {

  private PostgresInboxRepository repository;
  private HikariDataSource dataSource;

  @BeforeEach
  void setup() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:test");
    config.setUsername("sa");
    config.setPassword("");
    dataSource = new HikariDataSource(config);

    repository = new PostgresInboxRepository(dataSource);
  }

  @AfterEach
  void tearDown() {
    if (dataSource != null) {
      dataSource.close();
    }
  }

  @Test
  @DisplayName("Insert if absent SQL should use ON CONFLICT DO NOTHING for idempotency")
  void testInsertIfAbsentSql() {
    String sql = invokeProtectedMethod("getInsertIfAbsentSql");

    assertThat(sql)
        .contains("INSERT INTO inbox")
        .contains("(message_id, handler, processed_at)")
        .contains("VALUES (?, ?, ?)")
        .contains("ON CONFLICT DO NOTHING")
        .as("Should use PostgreSQL ON CONFLICT DO NOTHING clause for idempotent inserts");
  }

  /**
   * Verify that the SQL uses PostgreSQL's conflict resolution rather than other dialects.
   */
  @Test
  @DisplayName("Insert if absent SQL should not use H2-specific syntax")
  void testInsertIfAbsentSqlNotH2() {
    String sql = invokeProtectedMethod("getInsertIfAbsentSql");

    assertThat(sql)
        .doesNotContain("IGNORE")
        .doesNotContain("MERGE")
        .doesNotContain("PRAGMA")
        .as("Should not contain H2 or other dialect-specific syntax");
  }

  /**
   * Helper method to invoke protected SQL methods via reflection.
   */
  private String invokeProtectedMethod(String methodName) {
    try {
      Method method = PostgresInboxRepository.class.getDeclaredMethod(methodName);
      method.setAccessible(true);
      return (String) method.invoke(repository);
    } catch (Exception e) {
      throw new AssertionError("Failed to invoke method: " + methodName, e);
    }
  }
}
