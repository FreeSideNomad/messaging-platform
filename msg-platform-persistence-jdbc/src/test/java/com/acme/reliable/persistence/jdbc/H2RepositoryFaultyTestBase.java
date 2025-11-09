package com.acme.reliable.persistence.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/**
 * Base class for H2-based repository exception tests.
 * Sets up H2 in-memory database WITHOUT any tables to trigger SQLException on operations.
 * Used to test exception handling in catch (SQLException e) blocks.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class H2RepositoryFaultyTestBase {

  protected HikariDataSource dataSource;

  /**
   * Sets up H2 in-memory database WITHOUT Flyway migrations.
   * This ensures all table operations will fail with SQLException.
   */
  @BeforeAll
  void setupFaultySchema() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:faultydb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    config.setDriverClassName("org.h2.Driver");
    config.setUsername("sa");
    config.setPassword("");
    config.setMaximumPoolSize(5);

    dataSource = new HikariDataSource(config);
    // Note: No Flyway migration, so tables don't exist
  }

  /**
   * Cleans up the datasource after all tests.
   */
  @AfterAll
  void tearDown() {
    if (dataSource != null) {
      dataSource.close();
    }
  }

  /**
   * Gets the datasource for test classes to use.
   */
  protected DataSource getDataSource() {
    return dataSource;
  }
}
