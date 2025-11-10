package com.acme.reliable.processor;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.context.ApplicationContext;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/**
 * Base class for processor integration tests with H2 database and Micronaut ApplicationContext.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li>H2 in-memory database with Flyway migrations (from migrations/reliable/h2)</li>
 *   <li>Micronaut ApplicationContext for proper dependency injection and AOP</li>
 *   <li>@Transactional support via AOP interceptors</li>
 *   <li>Transaction operations for test repository access</li>
 *   <li>PER_CLASS lifecycle for efficient test execution (10-20x faster!)</li>
 * </ul>
 *
 * <p><strong>How it works:</strong>
 * <ol>
 *   <li>@BeforeAll initializes H2 database schema via Flyway (once per class)</li>
 *   <li>ApplicationContext is created once with "test" environment (once per class)</li>
 *   <li>Datasource configuration is overridden to use H2 database</li>
 *   <li>@Transactional annotations are honored via AOP interceptors</li>
 *   <li>All tests share the same database and ApplicationContext</li>
 *   <li>@AfterAll cleans up resources after all tests complete</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ProcessorIntegrationTestBase {

  protected ApplicationContext context;
  protected TransactionOperations<?> transactionOperations;
  private HikariDataSource dataSource;

  /**
   * Override this method in subclasses to register custom beans before context initialization.
   * Called before context creation to allow test-specific bean registration.
   *
   * <p>Example:
   * <pre>
   * @Override
   * protected void registerTestBeans() {
   *   // Register mock beans or other test-specific setup
   * }
   * </pre>
   */
  protected void registerTestBeans() {
    // Default: no custom beans
  }

  /**
   * Sets up the H2 in-memory database with Flyway migrations. Initializes the Micronaut
   * ApplicationContext with test environment configuration.
   *
   * <p>Runs once per test class (@BeforeAll) for efficient test execution.
   * All tests in the class share the same H2 database and ApplicationContext.
   *
   * @throws Exception if schema setup or context creation fails
   */
  @BeforeAll
  protected void setupContext() throws Exception {
    System.out.println("Setting up ProcessorIntegrationTestBase: H2 database + Micronaut ApplicationContext");

    // Initialize H2 database with Flyway migrations (once per class)
    setupSchema();

    // Build configuration map with hardwired H2 datasource
    Map<String, Object> configuration = new HashMap<>();

    // H2 datasource configuration - must match the database initialized by setupSchema()
    configuration.put("datasource.url", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    configuration.put("datasource.driver-class-name", "org.h2.Driver");
    configuration.put("datasource.username", "sa");
    configuration.put("datasource.password", "");

    // HikariCP pool configuration
    configuration.put("datasource.hikari.maximum-pool-size", "5");
    configuration.put("datasource.hikari.minimum-idle", "1");

    // Disable automatic schema creation (we use Flyway)
    configuration.put("jpa.default.properties.hibernate.hbm2ddl.auto", "none");

    // Enable Flyway auto-migration at startup
    configuration.put("flyway.enabled", "true");

    // Package scanning for test components
    configuration.put("micronaut.packages.enabled", "true");

    // Create ApplicationContext with test environment
    context = ApplicationContext.run(configuration, "test");

    System.out.println("ApplicationContext created with test environment");

    // Get TransactionOperations for wrapping repository access in transactions
    try {
      transactionOperations = context.getBean(TransactionOperations.class);
      System.out.println("TransactionOperations injected from ApplicationContext");
    } catch (Exception e) {
      System.err.println("Failed to inject TransactionOperations: " + e.getMessage());
      throw new RuntimeException("TransactionOperations must be available for repository access", e);
    }

    System.out.println("ProcessorIntegrationTestBase setup complete");
  }

  /**
   * Sets up the H2 in-memory database with Flyway migrations from migrations/reliable/h2.
   */
  private void setupSchema() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    config.setDriverClassName("org.h2.Driver");
    config.setUsername("sa");
    config.setPassword("");
    config.setMaximumPoolSize(5);

    dataSource = new HikariDataSource(config);

    // Use relative path to project-level migrations directory for reliable module
    String migrationsPath = "filesystem:"
        + Paths.get("../migrations/reliable/h2")
            .toAbsolutePath()
            .normalize()
            .toString();

    try {
      Flyway flyway = Flyway.configure()
          .dataSource(dataSource)
          .locations(migrationsPath)
          .load();
      flyway.migrate();
      System.out.println("Flyway migrations applied successfully from: " + migrationsPath);
    } catch (Exception e) {
      // Flyway already migrated (idempotent)
      System.out.println("Flyway migration (may have already completed): " + e.getMessage());
    }
  }

  /**
   * Stops the Micronaut ApplicationContext and closes the H2 datasource cleanly.
   * Runs once after all tests in the class complete (@AfterAll).
   */
  @AfterAll
  protected void tearDownContext() {
    if (context != null) {
      System.out.println("Stopping ApplicationContext");
      context.close();
      System.out.println("ApplicationContext stopped");
    }
    if (dataSource != null) {
      System.out.println("Closing H2 DataSource");
      dataSource.close();
      System.out.println("H2 DataSource closed");
    }
  }

  /**
   * Executes a read operation within a transaction context.
   *
   * <p>Use this to wrap repository read calls that need transaction context.
   *
   * @param <T> the type of object being read
   * @param callable the operation to execute
   * @return the result of the operation
   */
  protected <T> T readInTransaction(Callable<T> callable) {
    try {
      return transactionOperations.executeRead(
          tx -> {
            try {
              return callable.call();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute read operation in transaction", e);
    }
  }

  /**
   * Executes a write operation within a transaction context.
   *
   * <p>Use this to wrap repository write calls that need transaction context.
   *
   * @param runnable the operation to execute
   */
  protected void writeInTransaction(Runnable runnable) {
    try {
      transactionOperations.executeWrite(
          tx -> {
            runnable.run();
            return null;
          });
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute write operation in transaction", e);
    }
  }

  /**
   * Executes a write operation within a transaction context and returns a result.
   *
   * @param <T> the type of object being returned
   * @param callable the operation to execute
   * @return the result of the operation
   */
  protected <T> T writeInTransaction(Callable<T> callable) {
    try {
      return transactionOperations.executeWrite(
          tx -> {
            try {
              return callable.call();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute write operation in transaction", e);
    }
  }
}
