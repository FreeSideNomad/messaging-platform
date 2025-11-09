package com.acme.payments.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.acme.payments.application.command.BookLimitsCommand;
import com.acme.payments.application.command.CreateLimitsCommand;
import com.acme.payments.application.command.CreatePaymentCommand;
import com.acme.payments.application.command.CreateTransactionCommand;
import com.acme.payments.domain.model.Account;
import com.acme.payments.domain.model.AccountLimit;
import com.acme.payments.domain.model.AccountType;
import com.acme.payments.domain.model.Beneficiary;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.Payment;
import com.acme.payments.domain.model.PeriodType;
import com.acme.payments.domain.model.Transaction;
import com.acme.payments.domain.model.TransactionType;
import com.acme.payments.domain.service.AccountService;
import com.acme.payments.domain.service.PaymentService;
import com.acme.payments.integration.testdata.PaymentTestData;
import io.micronaut.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for Payment domain services with embedded H2 database.
 *
 * Tests service layer operations:
 * 1. Account service: creation, transaction management
 * 2. Limit service: limit creation, booking, reversal
 * 3. Payment service: payment creation and retrieval
 *
 * This test class extends PaymentsIntegrationTestBase which provides:
 * - H2 in-memory database with Flyway migrations
 * - Hardwired service and repository instances (no @MicronautTest DI required)
 *
 * @Transactional is used to ensure each test method runs within a database transaction,
 * which is required by Micronaut Data's repository operations.
 */
@DisplayName("Payment Service Integration Tests")
@Transactional
class PaymentServiceIntegrationTest extends PaymentsIntegrationTestBase {

  private UUID customerId;
  private String testAccountNumber;

  @BeforeEach
  void setup() throws Exception {
    // Setup Micronaut ApplicationContext with DI and AOP
    // Database setup (H2 with Flyway) is handled automatically by setupDatabaseForTest() @BeforeEach
    // in PaymentsIntegrationTestBase
    super.setupContext();

    customerId = PaymentTestData.customerId();
    testAccountNumber = "TEST_ACCT_" + System.nanoTime();
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() throws Exception {
    super.tearDownContext();
  }

  // ============================================================================
  // Account Service Tests
  // ============================================================================

  @Test
  @DisplayName("Should create account without limits")
  void testCreateAccount_NoLimits() {
    // Act: Create account
    Map<String, Object> result =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", false);

    // Assert: Account created with correct attributes
    assertNotNull(result);
    String accountId = (String) result.get("accountId");
    String accountNumber = (String) result.get("accountNumber");

    assertNotNull(accountId);
    assertNotNull(accountNumber);
    assertTrue(accountNumber.startsWith("ACC"));

    // Verify in database
    Optional<Account> saved = accountRepository.findById(UUID.fromString(accountId));
    assertTrue(saved.isPresent());

    Account account = saved.get();
    assertEquals(customerId, account.getCustomerId());
    assertEquals("USD", account.getCurrencyCode());
    assertEquals(AccountType.CHECKING, account.getAccountType());
    assertEquals("001", account.getTransitNumber());
    assertFalse(account.isLimitBased());
  }

  @Test
  @DisplayName("Should create account with limit-based flag")
  void testCreateAccount_LimitBased() {
    // Act: Create limit-based account
    Map<String, Object> result =
        accountService.createAccount(customerId, "EUR", AccountType.SAVINGS, "002", true);

    // Assert
    String accountId = (String) result.get("accountId");
    Optional<Account> saved = accountRepository.findById(UUID.fromString(accountId));
    assertTrue(saved.isPresent());

    Account account = saved.get();
    assertTrue(account.isLimitBased());
    assertEquals("EUR", account.getCurrencyCode());
  }

  @Test
  @DisplayName("Should retrieve account by ID")
  void testGetAccountById() {
    // Arrange: Create account
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", false);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    // Act: Retrieve account
    Account account = accountService.getAccountById(accountId);

    // Assert
    assertNotNull(account);
    assertEquals(accountId, account.getAccountId());
    assertEquals(customerId, account.getCustomerId());
  }

  @Test
  @DisplayName("Should throw exception for non-existent account")
  void testGetAccountById_NotFound() {
    // Act & Assert
    UUID nonExistentId = UUID.randomUUID();
    assertThrows(
        AccountService.AccountNotFoundException.class,
        () -> accountService.getAccountById(nonExistentId));
  }

  @Test
  @DisplayName("Should create transaction on account")
  void testCreateTransaction() {
    // Arrange: Create account
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", false);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    // Act: Create transaction
    CreateTransactionCommand cmd =
        new CreateTransactionCommand(
            accountId,
            TransactionType.DEBIT,
            Money.of(BigDecimal.valueOf(500), "USD"),
            "Test withdrawal");

    Transaction transaction = accountService.createTransaction(cmd);

    // Assert
    assertNotNull(transaction);
    assertEquals(TransactionType.DEBIT, transaction.transactionType());
    assertEquals(BigDecimal.valueOf(500), transaction.amount().amount());

    // Verify in database
    Account saved = accountService.getAccountById(accountId);
    assertFalse(saved.getTransactions().isEmpty());
    assertEquals(1, saved.getTransactions().size());
  }

  @Test
  @DisplayName("Should reverse transaction")
  void testReverseTransaction() {
    // Arrange: Create account and transaction
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", false);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    CreateTransactionCommand debitCmd =
        new CreateTransactionCommand(
            accountId,
            TransactionType.DEBIT,
            Money.of(BigDecimal.valueOf(500), "USD"),
            "Original withdrawal");

    Transaction original = accountService.createTransaction(debitCmd);
    UUID transactionId = original.transactionId();

    // Act: Reverse transaction
    Transaction reversal = accountService.reverseTransaction(transactionId, "Customer request");

    // Assert
    assertNotNull(reversal);
    assertEquals(TransactionType.CREDIT, reversal.transactionType());
    assertEquals(BigDecimal.valueOf(500), reversal.amount().amount());
    assertTrue(reversal.description().contains("Reversal"));
    assertTrue(reversal.description().contains("Customer request"));
  }

  // ============================================================================
  // Limit Service Tests
  // ============================================================================

  @Test
  @DisplayName("Should create limits for account")
  void testCreateLimits_SinglePeriod() {
    // Arrange: Create account
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", true);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    // Act: Create limit
    CreateLimitsCommand cmd =
        new CreateLimitsCommand(
            accountId,
            "USD",
            Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(10000), "USD")));

    Map<String, Object> result = limitService.handleCreateLimits(cmd);

    // Assert
    assertNotNull(result);
    assertEquals(1, result.get("limitCount"));

    List<String> limitIds = (List<String>) result.get("limitIds");
    assertEquals(1, limitIds.size());

    // Verify in database
    List<AccountLimit> saved = accountLimitRepository.findActiveByAccountId(accountId);
    assertEquals(1, saved.size());

    AccountLimit limit = saved.get(0);
    assertEquals(PeriodType.DAY, limit.getPeriodType());
    assertEquals(BigDecimal.valueOf(10000), limit.getLimitAmount().amount());
  }

  @Test
  @DisplayName("Should create multiple limits for account")
  void testCreateLimits_MultiplePeriods() {
    // Arrange: Create account
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", true);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    // Act: Create multiple limits
    CreateLimitsCommand cmd =
        new CreateLimitsCommand(
            accountId,
            "USD",
            Map.of(
                PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000), "USD"),
                PeriodType.DAY, Money.of(BigDecimal.valueOf(5000), "USD"),
                PeriodType.MONTH, Money.of(BigDecimal.valueOf(50000), "USD")));

    Map<String, Object> result = limitService.handleCreateLimits(cmd);

    // Assert
    assertEquals(3, result.get("limitCount"));

    List<AccountLimit> saved = accountLimitRepository.findActiveByAccountId(accountId);
    assertEquals(3, saved.size());

    // Verify amounts
    Map<PeriodType, BigDecimal> limitsByPeriod =
        saved.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    AccountLimit::getPeriodType, l -> l.getLimitAmount().amount()));

    assertEquals(BigDecimal.valueOf(1000), limitsByPeriod.get(PeriodType.HOUR));
    assertEquals(BigDecimal.valueOf(5000), limitsByPeriod.get(PeriodType.DAY));
    assertEquals(BigDecimal.valueOf(50000), limitsByPeriod.get(PeriodType.MONTH));
  }

  @Test
  @DisplayName("Should book limits against active limits")
  void testBookLimits() {
    // Arrange: Create account with limits
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", true);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    CreateLimitsCommand limitCmd =
        new CreateLimitsCommand(
            accountId,
            "USD",
            Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(5000), "USD")));

    limitService.handleCreateLimits(limitCmd);

    // Act: Book amount against limit
    BookLimitsCommand bookCmd =
        new BookLimitsCommand(accountId, Money.of(BigDecimal.valueOf(1000), "USD"));

    limitService.bookLimits(bookCmd);

    // Assert: Limit should be updated
    List<AccountLimit> limits = accountLimitRepository.findActiveByAccountId(accountId);
    assertEquals(1, limits.size());

    AccountLimit limit = limits.get(0);
    assertEquals(BigDecimal.valueOf(1000), limit.getUtilized().amount());
  }

  @Test
  @DisplayName("Should reverse limits booking")
  void testReverseLimits() {
    // Arrange: Create account with limits and booking
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", true);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    CreateLimitsCommand limitCmd =
        new CreateLimitsCommand(
            accountId,
            "USD",
            Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(5000), "USD")));

    limitService.handleCreateLimits(limitCmd);

    BookLimitsCommand bookCmd =
        new BookLimitsCommand(accountId, Money.of(BigDecimal.valueOf(1500), "USD"));

    limitService.bookLimits(bookCmd);

    // Act: Reverse booking
    limitService.reverseLimits(accountId, Money.of(BigDecimal.valueOf(1000), "USD"));

    // Assert
    List<AccountLimit> limits = accountLimitRepository.findActiveByAccountId(accountId);
    assertEquals(1, limits.size());

    AccountLimit limit = limits.get(0);
    assertEquals(BigDecimal.valueOf(500), limit.getUtilized().amount());
  }

  // ============================================================================
  // Payment Service Tests
  // ============================================================================

  @Test
  @DisplayName("Should create payment")
  void testCreatePayment() {
    // Arrange: Create account
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", false);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    Beneficiary beneficiary =
        new Beneficiary("John Doe", "ACCT123456", "001", "BENEFICIARY BANK");

    CreatePaymentCommand cmd =
        new CreatePaymentCommand(
            accountId,
            Money.of(BigDecimal.valueOf(1000), "USD"),
            Money.of(BigDecimal.valueOf(1000), "USD"),
            LocalDate.now(),
            beneficiary);

    // Act: Create payment
    Payment payment = paymentService.createPayment(cmd);

    // Assert
    assertNotNull(payment);
    assertEquals(accountId, payment.getDebitAccountId());
    assertEquals(BigDecimal.valueOf(1000), payment.getDebitAmount().amount());
    assertEquals("John Doe", payment.getBeneficiary().name());

    // Verify in database
    Optional<Payment> saved = paymentRepository.findById(payment.getPaymentId());
    assertTrue(saved.isPresent());
  }

  @Test
  @DisplayName("Should create payment with currency conversion")
  void testCreatePayment_WithCurrencyConversion() {
    // Arrange: Create account
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", false);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    Beneficiary beneficiary = new Beneficiary("Jane Smith", "CAD_ACCT", "002", "CANADA BANK");

    CreatePaymentCommand cmd =
        new CreatePaymentCommand(
            accountId,
            Money.of(BigDecimal.valueOf(800), "USD"),
            Money.of(BigDecimal.valueOf(1000), "CAD"),
            LocalDate.now(),
            beneficiary);

    // Act: Create payment with FX
    Payment payment = paymentService.createPayment(cmd);

    // Assert
    assertNotNull(payment);
    assertEquals("USD", payment.getDebitAmount().currencyCode());
    assertEquals("CAD", payment.getCreditAmount().currencyCode());
    assertEquals(BigDecimal.valueOf(800), payment.getDebitAmount().amount());
    assertEquals(BigDecimal.valueOf(1000), payment.getCreditAmount().amount());
  }

  @Test
  @DisplayName("Should retrieve payment by ID")
  void testGetPaymentById() {
    // Arrange: Create payment
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", false);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    Beneficiary beneficiary = new Beneficiary("Test Payee", "TEST_ACCT", "001", "TEST BANK");

    CreatePaymentCommand cmd =
        new CreatePaymentCommand(
            accountId,
            Money.of(BigDecimal.valueOf(500), "USD"),
            Money.of(BigDecimal.valueOf(500), "USD"),
            LocalDate.now(),
            beneficiary);

    Payment created = paymentService.createPayment(cmd);

    // Act: Retrieve payment
    Payment retrieved = paymentService.getPaymentById(created.getPaymentId());

    // Assert
    assertNotNull(retrieved);
    assertEquals(created.getPaymentId(), retrieved.getPaymentId());
    assertEquals(accountId, retrieved.getDebitAccountId());
  }

  @Test
  @DisplayName("Should throw exception for non-existent payment")
  void testGetPaymentById_NotFound() {
    // Act & Assert
    UUID nonExistentId = UUID.randomUUID();
    assertThrows(
        PaymentService.PaymentNotFoundException.class,
        () -> paymentService.getPaymentById(nonExistentId));
  }

  @Test
  @DisplayName("Should update payment")
  void testUpdatePayment() {
    // Arrange: Create payment
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", false);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    Beneficiary beneficiary = new Beneficiary("Original Payee", "ACCT", "001", "BANK");

    CreatePaymentCommand cmd =
        new CreatePaymentCommand(
            accountId,
            Money.of(BigDecimal.valueOf(300), "USD"),
            Money.of(BigDecimal.valueOf(300), "USD"),
            LocalDate.now(),
            beneficiary);

    Payment payment = paymentService.createPayment(cmd);

    // Act: Update payment
    Payment updated = paymentService.updatePayment(payment);

    // Assert
    assertNotNull(updated);
    Optional<Payment> saved = paymentRepository.findById(payment.getPaymentId());
    assertTrue(saved.isPresent());
  }

  // ============================================================================
  // Cross-Service Integration Tests
  // ============================================================================

  @Test
  @DisplayName("Should complete account creation with limits then process payment")
  void testCompleteAccountWithLimitsThenPayment() {
    // Step 1: Create account
    Map<String, Object> createAccountResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", true);
    UUID accountId = UUID.fromString((String) createAccountResult.get("accountId"));

    // Step 2: Create limits
    CreateLimitsCommand limitCmd =
        new CreateLimitsCommand(
            accountId,
            "USD",
            Map.of(
                PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000), "USD"),
                PeriodType.DAY, Money.of(BigDecimal.valueOf(5000), "USD")));

    Map<String, Object> limitResult = limitService.handleCreateLimits(limitCmd);
    assertEquals(2, limitResult.get("limitCount"));

    // Step 3: Book limits
    BookLimitsCommand bookCmd =
        new BookLimitsCommand(accountId, Money.of(BigDecimal.valueOf(500), "USD"));
    limitService.bookLimits(bookCmd);

    // Step 4: Create payment
    Beneficiary beneficiary = new Beneficiary("Final Payee", "FINAL_ACCT", "001", "FINAL_BANK");

    CreatePaymentCommand paymentCmd =
        new CreatePaymentCommand(
            accountId,
            Money.of(BigDecimal.valueOf(200), "USD"),
            Money.of(BigDecimal.valueOf(200), "USD"),
            LocalDate.now(),
            beneficiary);

    Payment payment = paymentService.createPayment(paymentCmd);

    // Assert: Complete workflow
    assertNotNull(payment);

    Account account = accountService.getAccountById(accountId);
    assertNotNull(account);
    assertTrue(account.isLimitBased());

    List<AccountLimit> limits = accountLimitRepository.findActiveByAccountId(accountId);
    assertEquals(2, limits.size());

    List<Payment> payments = paymentRepository.findByDebitAccountId(accountId);
    assertEquals(1, payments.size());
  }

  // ============================================================================
  // Error Handling Tests
  // ============================================================================

  @Test
  @DisplayName("Should handle invalid currency mismatch in limits")
  void testCreateLimits_CurrencyMismatch() {
    // Arrange: Create USD account
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", true);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    // Act & Assert: Should throw exception for currency mismatch
    CreateLimitsCommand cmd =
        new CreateLimitsCommand(
            accountId,
            "USD",
            Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(5000), "EUR")));

    assertThrows(IllegalArgumentException.class, () -> limitService.handleCreateLimits(cmd));
  }

  @Test
  @DisplayName("Should handle null account limit gracefully")
  void testBookLimits_NoActiveLimits() {
    // Arrange: Create account without limits
    Map<String, Object> createResult =
        accountService.createAccount(customerId, "USD", AccountType.CHECKING, "001", false);
    UUID accountId = UUID.fromString((String) createResult.get("accountId"));

    // Act: Try to book limits (should handle gracefully)
    BookLimitsCommand bookCmd =
        new BookLimitsCommand(accountId, Money.of(BigDecimal.valueOf(100), "USD"));

    limitService.bookLimits(bookCmd); // Should not throw

    // Assert: Limits should still be empty
    List<AccountLimit> limits = accountLimitRepository.findActiveByAccountId(accountId);
    assertTrue(limits.isEmpty());
  }
}
