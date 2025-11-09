package com.acme.payments.integration;

import com.acme.payments.domain.repository.AccountLimitRepository;
import com.acme.payments.domain.repository.AccountRepository;
import com.acme.payments.domain.repository.FxContractRepository;
import com.acme.payments.domain.repository.PaymentRepository;
import com.acme.payments.domain.service.AccountService;
import com.acme.payments.domain.service.LimitService;
import com.acme.payments.domain.service.PaymentService;
import com.acme.payments.infrastructure.persistence.H2RepositoryTestBase;
import io.micronaut.context.ApplicationContext;
import jakarta.jms.ConnectionFactory;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

/**
 * Base class for payment integration tests that uses Micronaut ApplicationContext for proper DI
 * and AOP (including @Transactional), while hardwiring H2 database and embedded ActiveMQ.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li>Micronaut ApplicationContext for proper dependency injection</li>
 *   <li>AOP interceptors (including @Transactional) work correctly</li>
 *   <li>H2 in-memory database via HikariCP connection pool</li>
 *   <li>Flyway migrations from shared filesystem directory</li>
 *   <li>Embedded ActiveMQ broker with VM transport (no Docker required)</li>
 *   <li>No @MicronautTest annotation needed on test classes</li>
 * </ul>
 *
 * <p><strong>How it works:</strong>
 * <ol>
 *   <li>@BeforeEach initializes H2 database schema via Flyway</li>
 *   <li>ApplicationContext is created with "test" environment (enables TestMqFactoryProvider)</li>
 *   <li>Datasource configuration is overridden to use the H2 database initialized above</li>
 *   <li>Embedded ActiveMQ is set up via TestMqFactoryProvider (VM transport)</li>
 *   <li>Beans are obtained from ApplicationContext (with proper AOP and DI)</li>
 *   <li>@Transactional annotations are honored via AOP interceptors</li>
 * </ol>
 *
 * <p><strong>Usage:</strong>
 * <pre>
 * class MyIntegrationTest extends PaymentsIntegrationTestBase {
 *   @BeforeEach
 *   void setUp() throws Exception {
 *     super.setupContext();      // Initialize H2 + Create ApplicationContext
 *
 *     // Now use inherited fields obtained from ApplicationContext:
 *     // - connectionFactory (JMS)
 *     // - accountRepository, paymentRepository, etc. (from DI)
 *     // - accountService, limitService, paymentService (from DI with @Transactional working)
 *   }
 *
 *   @AfterEach
 *   void tearDown() throws Exception {
 *     super.tearDownContext();
 *   }
 * }
 * </pre>
 *
 * <p>Extends H2RepositoryTestBase which provides the setupSchema() method that initializes
 * the H2 database with Flyway migrations from the shared migrations/payments/h2 directory.
 *
 * @see H2RepositoryTestBase for H2 database setup details
 */

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PaymentsIntegrationTestBase extends H2RepositoryTestBase {

  /**
   * Override setupSchema to run @BeforeEach instead of @BeforeAll.
   * Since we're using ApplicationContext with Flyway for test setup, we skip the
   * parent class's database initialization which creates a separate datasource.
   *
   * @throws Exception if schema setup fails
   */
  @BeforeEach
  public void setupDatabaseForTest() throws Exception {
    // The ApplicationContext.run() in setupContext() will handle Flyway migrations.
    // We don't call super.setupSchema() here because it creates a separate HikariCP pool
    // and H2 in-memory database instance that doesn't share with ApplicationContext's database.
  }

  // ============================================================================
  // Micronaut ApplicationContext (provides DI and AOP)
  // ============================================================================

  protected ApplicationContext context;

  // ============================================================================
  // JMS Resources (from ApplicationContext)
  // ============================================================================

  protected ConnectionFactory connectionFactory;

  // ============================================================================
  // Service Instances (from ApplicationContext - with AOP and DI)
  // ============================================================================

  protected AccountService accountService;
  protected LimitService limitService;
  protected PaymentService paymentService;

  // ============================================================================
  // Repository Instances (from ApplicationContext - with DI)
  // ============================================================================

  protected AccountRepository accountRepository;
  protected AccountLimitRepository accountLimitRepository;
  protected PaymentRepository paymentRepository;
  protected FxContractRepository fxContractRepository;

  // ============================================================================
  // Setup Methods
  // ============================================================================

  /**
   * Sets up the Micronaut ApplicationContext with hardwired H2 datasource configuration.
   *
   * <p>This must be called after setupSchema() to ensure the H2 database is initialized.
   * The ApplicationContext will:
   * <ul>
   *   <li>Run with "test" environment (enables TestMqFactoryProvider for embedded ActiveMQ)</li>
   *   <li>Override datasource URL to use the H2 database initialized via Flyway</li>
   *   <li>Provide beans with proper DI and AOP interceptors</li>
   *   <li>Create embedded ActiveMQ broker via TestMqFactoryProvider</li>
   * </ul>
   *
   * @throws Exception if context creation fails
   */
  protected void setupContext() throws Exception {
    log.info("Setting up Micronaut ApplicationContext with hardwired H2 datasource");

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
    // This ensures the ApplicationContext's database has migrations applied
    configuration.put("flyway.enabled", "true");

    // Create ApplicationContext with test environment
    // "test" environment activates TestMqFactoryProvider for embedded ActiveMQ
    context =
        ApplicationContext.run(
            configuration, "test"); // "test" env activates TestMqFactoryProvider

    log.info("ApplicationContext created with test environment");

    // Get beans from context (with proper DI and AOP)
    accountService = context.getBean(AccountService.class);
    limitService = context.getBean(LimitService.class);
    paymentService = context.getBean(PaymentService.class);
    accountRepository = context.getBean(AccountRepository.class);
    accountLimitRepository = context.getBean(AccountLimitRepository.class);
    paymentRepository = context.getBean(PaymentRepository.class);
    fxContractRepository = context.getBean(FxContractRepository.class);

    // Get JMS ConnectionFactory (created by TestMqFactoryProvider in test environment)
    // Note: ConnectionFactory may not be available if the test doesn't use JMS
    try {
      connectionFactory = context.getBean(ConnectionFactory.class);
      log.info("ConnectionFactory injected from ApplicationContext");
    } catch (Exception e) {
      log.info("ConnectionFactory not available in this test context (may not be needed)");
      connectionFactory = null;
    }

    log.info("All services and repositories injected from ApplicationContext");
  }

  /**
   * Stops the Micronaut ApplicationContext cleanly.
   *
   * <p>Should be called in @AfterEach or @AfterAll to ensure proper resource cleanup.
   */
  protected void tearDownContext() {
    if (context != null) {
      log.info("Stopping ApplicationContext");
      context.close();
      log.info("ApplicationContext stopped");
    }
  }

}
