package com.acme.payments.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.acme.payments.application.command.CreateLimitsCommand;
import com.acme.payments.application.command.CreatePaymentCommand;
import com.acme.payments.integration.testdata.PaymentTestData;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for complete payment workflows with embedded H2 and ActiveMQ.
 *
 * Tests end-to-end message flow across multiple commands:
 * 1. Create Account workflow (CreateAccountCommand + optional CreateLimitsCommand)
 * 2. Simple Payment workflow (CreatePaymentCommand)
 * 3. Multi-step workflows with conditional logic
 *
 * Verifies:
 * - Commands are received and processed asynchronously
 * - Database state is correctly updated for each command
 * - Workflow completion is deterministic
 * - Concurrent workflows maintain isolation
 *
 * This test class extends PaymentsIntegrationTestBase which provides:
 * - H2 in-memory database with Flyway migrations
 * - Embedded ActiveMQ broker with VM transport
 * - Hardwired service and repository instances (no @MicronautTest DI required)
 *
 * @Transactional is used to ensure each test method runs within a database transaction.
 */
@DisplayName("Payment Workflow Integration Tests")
@Transactional
class PaymentWorkflowIntegrationTest extends PaymentsIntegrationTestBase {

  private static final String CREATE_ACCOUNT_QUEUE = "APP.CMD.CREATEACCOUNT.Q";
  private static final String CREATE_LIMITS_QUEUE = "APP.CMD.CREATELIMITS.Q";
  private static final String CREATE_PAYMENT_QUEUE = "APP.CMD.CREATEPAYMENT.Q";

  @BeforeEach
  void setup() throws Exception {
    // Setup Micronaut ApplicationContext with DI and AOP
    // Database setup (H2 with Flyway) is handled automatically by setupDatabaseForTest() @BeforeEach
    // in PaymentsIntegrationTestBase
    // This also creates embedded ActiveMQ via TestMqFactoryProvider
    super.setupContext();

    // Reset state before each test
    // Queues are cleared via embedded ActiveMQ non-persistent configuration
  }

  @AfterEach
  void tearDown() throws Exception {
    super.tearDownContext();
  }

  // ============================================================================
  // Workflow 1: Account Creation Workflow (Simple Account)
  // ============================================================================

  @Test
  @DisplayName("Should complete account creation workflow without limits")
  void testAccountCreationWorkflow_NoLimits() throws JMSException {
    // Arrange: Test data for account creation
    UUID customerId = PaymentTestData.customerId();
    String accountNumber = "ACCT_NO_LIMITS_" + System.nanoTime();
    String commandJson = PaymentTestData.createAccountCommandJson(customerId, accountNumber);

    // Act: Send CreateAccount command
    sendJmsMessage(CREATE_ACCOUNT_QUEUE, commandJson);

    // Assert: Verify account is created in database
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              var account = accountRepository.findByAccountNumber(accountNumber);
              assertTrue(account.isPresent(), "Account should be created in database");
              assertEquals(customerId, account.get().getCustomerId());
              assertFalse(account.get().isLimitBased(), "Account should not be limit-based");
            });
  }

  // ============================================================================
  // Workflow 2: Account Creation with Limits Workflow
  // ============================================================================

  @Test
  @DisplayName("Should complete account creation workflow with limits")
  void testAccountCreationWorkflow_WithLimits() throws JMSException {
    // Arrange: Test data for account creation with limits
    UUID customerId = PaymentTestData.customerId();
    String accountNumber = "ACCT_WITH_LIMITS_" + System.nanoTime();

    // Step 1: Create account
    String createAccountJson = PaymentTestData.createAccountCommandJson(customerId, accountNumber);

    // Act Step 1: Send CreateAccount command
    sendJmsMessage(CREATE_ACCOUNT_QUEUE, createAccountJson);

    // Assert Step 1: Account should be created
    UUID accountId =
        await()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .until(
                () -> accountRepository.findByAccountNumber(accountNumber).map(a -> a.getAccountId()),
                java.util.Optional::isPresent)
            .get();

    assertTrue(accountRepository.findById(accountId).isPresent());

    // Step 2: Send CreateLimits command
    String createLimitsJson = PaymentTestData.createLimitsCommandJson(accountId);

    // Act Step 2: Send CreateLimits command
    sendJmsMessage(CREATE_LIMITS_QUEUE, createLimitsJson);

    // Assert Step 2: Limits should be created
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              var limits = accountLimitRepository.findActiveByAccountId(accountId);
              assertFalse(limits.isEmpty(), "Limits should be created for account");
              // Default is daily limit, so we expect at least one
              assertTrue(limits.size() >= 1);
            });
  }

  // ============================================================================
  // Workflow 3: Simple Payment Workflow
  // ============================================================================

  @Test
  @DisplayName("Should process payment on existing account")
  void testPaymentWorkflow_OnExistingAccount() throws JMSException {
    // Arrange: Create account first
    UUID customerId = PaymentTestData.customerId();
    String accountNumber = "PAY_ACCT_" + System.nanoTime();
    String createAccountJson = PaymentTestData.createAccountCommandJson(customerId, accountNumber);

    // Create account
    sendJmsMessage(CREATE_ACCOUNT_QUEUE, createAccountJson);

    UUID accountId =
        await()
            .atMost(Duration.ofSeconds(5))
            .until(
                () -> accountRepository.findByAccountNumber(accountNumber).map(a -> a.getAccountId()),
                java.util.Optional::isPresent)
            .get();

    // Step 2: Send payment command
    String paymentJson =
        PaymentTestData.createPaymentCommandJson(accountId, BigDecimal.valueOf(1000), "John Doe");

    // Act: Send CreatePayment command
    sendJmsMessage(CREATE_PAYMENT_QUEUE, paymentJson);

    // Assert: Payment should be created and persisted
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              var payments = paymentRepository.findByDebitAccountId(accountId);
              assertFalse(payments.isEmpty(), "Payment should be created");
              var payment = payments.get(0);
              assertEquals(accountId, payment.getDebitAccountId());
              assertEquals(BigDecimal.valueOf(1000), payment.getDebitAmount().amount());
            });
  }

  // ============================================================================
  // Workflow 4: Multi-Step Account + Payment Workflow
  // ============================================================================

  @Test
  @DisplayName("Should complete multi-step workflow: Create account -> Add limits -> Process payment")
  void testCompleteMultiStepWorkflow() throws JMSException {
    // Arrange: Complete workflow test data
    UUID customerId = PaymentTestData.customerId();
    String accountNumber = "WORKFLOW_" + System.nanoTime();

    // Step 1: Create account
    String createAccountJson = PaymentTestData.createAccountCommandJson(customerId, accountNumber);
    sendJmsMessage(CREATE_ACCOUNT_QUEUE, createAccountJson);

    UUID accountId =
        await()
            .atMost(Duration.ofSeconds(5))
            .until(
                () -> accountRepository.findByAccountNumber(accountNumber).map(a -> a.getAccountId()),
                java.util.Optional::isPresent)
            .get();

    // Step 2: Add limits
    String createLimitsJson = PaymentTestData.createLimitsCommandJson(accountId);
    sendJmsMessage(CREATE_LIMITS_QUEUE, createLimitsJson);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              var limits = accountLimitRepository.findActiveByAccountId(accountId);
              assertTrue(limits.size() > 0, "Limits should exist");
            });

    // Step 3: Process payment
    String paymentJson =
        PaymentTestData.createPaymentCommandJson(
            accountId, BigDecimal.valueOf(500), "Payment Beneficiary");
    sendJmsMessage(CREATE_PAYMENT_QUEUE, paymentJson);

    // Final Assert: All workflow steps completed successfully
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              // Verify account exists
              var account = accountRepository.findById(accountId);
              assertTrue(account.isPresent(), "Account should exist");

              // Verify limits exist
              var limits = accountLimitRepository.findActiveByAccountId(accountId);
              assertTrue(limits.size() > 0, "Limits should exist");

              // Verify payment was processed
              var payments = paymentRepository.findByDebitAccountId(accountId);
              assertTrue(payments.size() > 0, "Payment should exist");

              var payment = payments.get(0);
              assertEquals(BigDecimal.valueOf(500), payment.getDebitAmount().amount());
            });
  }

  // ============================================================================
  // Workflow 5: Concurrent Workflows - Isolation Testing
  // ============================================================================

  @Test
  @DisplayName("Should maintain isolation between concurrent account creation workflows")
  void testConcurrentAccountWorkflows_WithIsolation() throws JMSException {
    // Arrange: Two concurrent workflows
    UUID customer1 = PaymentTestData.customerId();
    UUID customer2 = PaymentTestData.customerId();
    String account1 = "CONCURRENT_1_" + System.nanoTime();
    String account2 = "CONCURRENT_2_" + System.nanoTime();

    // Act: Send both account creation commands
    String createAccount1Json = PaymentTestData.createAccountCommandJson(customer1, account1);
    String createAccount2Json = PaymentTestData.createAccountCommandJson(customer2, account2);

    sendJmsMessage(CREATE_ACCOUNT_QUEUE, createAccount1Json);
    sendJmsMessage(CREATE_ACCOUNT_QUEUE, createAccount2Json);

    // Assert: Both accounts should be created independently
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              var acc1 = accountRepository.findByAccountNumber(account1);
              var acc2 = accountRepository.findByAccountNumber(account2);

              assertTrue(acc1.isPresent(), "First account should be created");
              assertTrue(acc2.isPresent(), "Second account should be created");

              // Verify they belong to different customers
              assertNotEquals(
                  acc1.get().getCustomerId(),
                  acc2.get().getCustomerId(),
                  "Accounts should belong to different customers");
              assertEquals(customer1, acc1.get().getCustomerId());
              assertEquals(customer2, acc2.get().getCustomerId());
            });
  }

  // ============================================================================
  // Workflow 6: Multiple Payments on Same Account
  // ============================================================================

  @Test
  @DisplayName("Should handle multiple concurrent payments on same account")
  void testConcurrentPaymentsOnSameAccount() throws JMSException {
    // Arrange: Create single account
    UUID customerId = PaymentTestData.customerId();
    String accountNumber = "MULTI_PAY_" + System.nanoTime();
    String createAccountJson = PaymentTestData.createAccountCommandJson(customerId, accountNumber);

    sendJmsMessage(CREATE_ACCOUNT_QUEUE, createAccountJson);

    UUID accountId =
        await()
            .atMost(Duration.ofSeconds(5))
            .until(
                () -> accountRepository.findByAccountNumber(accountNumber).map(a -> a.getAccountId()),
                java.util.Optional::isPresent)
            .get();

    // Act: Send multiple payment commands
    String payment1Json =
        PaymentTestData.createPaymentCommandJson(accountId, BigDecimal.valueOf(100), "Payee 1");
    String payment2Json =
        PaymentTestData.createPaymentCommandJson(accountId, BigDecimal.valueOf(200), "Payee 2");
    String payment3Json =
        PaymentTestData.createPaymentCommandJson(accountId, BigDecimal.valueOf(300), "Payee 3");

    sendJmsMessage(CREATE_PAYMENT_QUEUE, payment1Json);
    sendJmsMessage(CREATE_PAYMENT_QUEUE, payment2Json);
    sendJmsMessage(CREATE_PAYMENT_QUEUE, payment3Json);

    // Assert: All payments should be created
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              var payments = paymentRepository.findByDebitAccountId(accountId);
              assertEquals(
                  3, payments.size(), "All three payments should be created on the same account");

              // Verify payment amounts
              var amounts =
                  payments.stream().map(p -> p.getDebitAmount().amount()).sorted().toList();
              assertEquals(BigDecimal.valueOf(100), amounts.get(0));
              assertEquals(BigDecimal.valueOf(200), amounts.get(1));
              assertEquals(BigDecimal.valueOf(300), amounts.get(2));
            });
  }

  // ============================================================================
  // Workflow 7: Account Limits Enforcement
  // ============================================================================

  @Test
  @DisplayName("Should verify limits are applied after creation")
  void testLimitsAppliedAfterCreation() throws JMSException {
    // Arrange: Create account with limits
    UUID customerId = PaymentTestData.customerId();
    String accountNumber = "LIMITED_ACCT_" + System.nanoTime();
    String createAccountJson = PaymentTestData.createAccountCommandJson(customerId, accountNumber);

    sendJmsMessage(CREATE_ACCOUNT_QUEUE, createAccountJson);

    UUID accountId =
        await()
            .atMost(Duration.ofSeconds(5))
            .until(
                () -> accountRepository.findByAccountNumber(accountNumber).map(a -> a.getAccountId()),
                java.util.Optional::isPresent)
            .get();

    // Send limits
    String createLimitsJson = PaymentTestData.createLimitsCommandJson(accountId);
    sendJmsMessage(CREATE_LIMITS_QUEUE, createLimitsJson);

    // Assert: Limits should be created and active
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              var limits = accountLimitRepository.findActiveByAccountId(accountId);
              assertTrue(limits.size() > 0, "Limits should be created");

              // Verify limit properties
              var limit = limits.get(0);
              assertNotNull(limit.getLimitAmount());
              assertEquals("USD", limit.getLimitAmount().currencyCode());
              assertTrue(limit.getLimitAmount().isPositive());
            });
  }

  // ============================================================================
  // Workflow 8: Sequential Command Dependencies
  // ============================================================================

  @Test
  @DisplayName("Should handle sequential commands with dependencies")
  void testSequentialCommandDependencies() throws JMSException {
    // This test verifies that commands can be sent in sequence and each waits for
    // the previous to complete (via polling with Awaitility)

    UUID customerId = PaymentTestData.customerId();
    String accountNumber = "SEQUENTIAL_" + System.nanoTime();

    // Command 1: Create account
    String createAccountJson = PaymentTestData.createAccountCommandJson(customerId, accountNumber);
    sendJmsMessage(CREATE_ACCOUNT_QUEUE, createAccountJson);

    // Wait for account to be created before proceeding
    UUID accountId =
        await()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .until(
                () -> accountRepository.findByAccountNumber(accountNumber).map(a -> a.getAccountId()),
                java.util.Optional::isPresent)
            .get();

    // Command 2: Create limits (depends on account existing)
    String createLimitsJson = PaymentTestData.createLimitsCommandJson(accountId);
    sendJmsMessage(CREATE_LIMITS_QUEUE, createLimitsJson);

    // Wait for limits to be created
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              var limits = accountLimitRepository.findActiveByAccountId(accountId);
              assertTrue(limits.size() > 0);
            });

    // Command 3: Create payment (depends on account existing and limits set)
    String paymentJson =
        PaymentTestData.createPaymentCommandJson(accountId, BigDecimal.valueOf(1000), "Final Payee");
    sendJmsMessage(CREATE_PAYMENT_QUEUE, paymentJson);

    // Final assertion
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              var payments = paymentRepository.findByDebitAccountId(accountId);
              assertEquals(1, payments.size());
            });
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  /**
   * Sends a message to the specified JMS queue using embedded ActiveMQ.
   */
  private void sendJmsMessage(String queueName, String messageBody) throws JMSException {
    try (var connection = connectionFactory.createConnection();
        var session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

      Queue queue = session.createQueue(queueName);
      var producer = session.createProducer(queue);
      var message = session.createTextMessage(messageBody);

      connection.start();
      producer.send(message);
    }
  }
}
