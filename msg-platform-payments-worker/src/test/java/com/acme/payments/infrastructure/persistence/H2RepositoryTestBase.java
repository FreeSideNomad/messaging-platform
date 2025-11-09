package com.acme.payments.infrastructure.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Paths;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/**
 * Base class for H2-based repository integration tests.
 * Handles shared Flyway setup and datasource management.
 * Uses migrations from the shared migrations/payments/h2 directory.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class H2RepositoryTestBase {

  protected static HikariDataSource dataSource;

  /**
   * Sets up the H2 in-memory database with Flyway migrations.
   * Uses relative path to migrations directory for portability.
   */
  @BeforeAll
  protected void setupSchema() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    config.setDriverClassName("org.h2.Driver");
    config.setUsername("sa");
    config.setPassword("");
    config.setMaximumPoolSize(5);

    dataSource = new HikariDataSource(config);

    // Use relative path to project-level migrations directory
    String migrationsPath = "filesystem:"
        + Paths.get("../migrations/payments/h2")
            .toAbsolutePath()
            .normalize()
            .toString();

    Flyway flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations(migrationsPath)
        .load();
    flyway.migrate();
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
