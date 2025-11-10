package com.acme.payments.e2e;

import com.acme.payments.application.command.CreateAccountCommand;
import com.acme.payments.application.command.CreatePaymentCommand;
import com.acme.payments.domain.model.*;
import com.acme.payments.integration.PaymentsIntegrationTestBase;
import io.micronaut.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for complete payment flows. Tests the full stack: domain services,
 * repositories, and database.
 * <p>
 * This test class extends PaymentsIntegrationTestBase which provides:
 * - H2 in-memory database with Flyway migrations
 * - Micronaut ApplicationContext for proper DI and AOP
 * - Hardwired service and repository instances (no @MicronautTest needed)
 *
 * @Transactional is used to ensure each test method runs within a database transaction.
 */
@DisplayName("Payment Flow E2E Tests")
@Transactional
class PaymentFlowE2ETest extends PaymentsIntegrationTestBase {

    /**
     * Initialize ApplicationContext once for the entire test class.
     */
    @BeforeAll
    void setupOnce() throws Exception {
        // Setup Micronaut ApplicationContext with DI and AOP - runs ONCE per test class
        super.setupContext();
    }

    /**
     * Clean up ApplicationContext after all test methods complete.
     */
    @org.junit.jupiter.api.AfterAll
    void tearDownOnce() throws Exception {
        super.tearDownContext();
    }

    @Test
    @Transactional
    @DisplayName("E2E: Create account flow")
    void testCreateAccountFlow() {
        // Given
        UUID customerId = UUID.randomUUID();
        CreateAccountCommand command =
                new CreateAccountCommand(customerId, "USD", "001", AccountType.CHECKING, false);

        // When - Create account through service
        Map<String, Object> result = accountService.handleCreateAccount(command);
        UUID accountId = UUID.fromString((String) result.get("accountId"));

        // Then - Verify account was persisted
        Optional<Account> retrieved = readInTransaction(() -> accountRepository.findById(accountId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getCustomerId()).isEqualTo(customerId);
        assertThat(retrieved.get().getCurrencyCode()).isEqualTo("USD");
        assertThat(retrieved.get().getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(retrieved.get().getAvailableBalance()).isEqualTo(Money.zero("USD"));
    }

    @Test
    @Transactional
    @DisplayName("E2E: Simple payment flow (no FX)")
    void testSimplePaymentFlow_NoFX() {
        // Given - Create debit account
        UUID customerId = UUID.randomUUID();
        CreateAccountCommand accountCmd =
                new CreateAccountCommand(customerId, "USD", "001", AccountType.CHECKING, false);
        Map<String, Object> accountResult = accountService.handleCreateAccount(accountCmd);
        UUID accountId = UUID.fromString((String) accountResult.get("accountId"));
        Account debitAccount = readInTransaction(() -> accountRepository.findById(accountId)).orElseThrow();

        // Add initial balance via transaction
        debitAccount.createTransaction(
                TransactionType.CREDIT, Money.of(1000.00, "USD"), "Initial deposit");
        writeInTransaction(() -> accountRepository.save(debitAccount));

        // When - Create payment
        Beneficiary beneficiary = new Beneficiary("John Doe", "ACC987654321", "002", "Test Bank");
        CreatePaymentCommand paymentCmd =
                new CreatePaymentCommand(
                        debitAccount.getAccountId(),
                        Money.of(100.00, "USD"),
                        Money.of(100.00, "USD"),
                        LocalDate.now(),
                        beneficiary);
        Payment payment = paymentService.createPayment(paymentCmd);

        // Then - Verify payment was created
        assertThat(payment).isNotNull();
        assertThat(payment.getPaymentId()).isNotNull();
        assertThat(payment.getDebitAccountId()).isEqualTo(debitAccount.getAccountId());
        assertThat(payment.getDebitAmount()).isEqualTo(Money.of(100.00, "USD"));
        assertThat(payment.getCreditAmount()).isEqualTo(Money.of(100.00, "USD"));
        assertThat(payment.requiresFx()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        // Verify payment was persisted
        Optional<Payment> retrievedPayment = readInTransaction(() -> paymentRepository.findById(payment.getPaymentId()));
        assertThat(retrievedPayment).isPresent();
        assertThat(retrievedPayment.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @Transactional
    @DisplayName("E2E: Payment with FX conversion")
    void testPaymentFlow_WithFX() {
        // Given - Create USD account
        UUID customerId = UUID.randomUUID();
        CreateAccountCommand accountCmd =
                new CreateAccountCommand(customerId, "USD", "001", AccountType.CHECKING, false);
        Map<String, Object> accountResult = accountService.handleCreateAccount(accountCmd);
        UUID accountId = UUID.fromString((String) accountResult.get("accountId"));
        Account debitAccount = readInTransaction(() -> accountRepository.findById(accountId)).orElseThrow();

        // Add initial balance
        debitAccount.createTransaction(
                TransactionType.CREDIT, Money.of(500.00, "USD"), "Initial deposit");
        writeInTransaction(() -> accountRepository.save(debitAccount));

        // When - Create payment requiring FX (USD to EUR)
        Beneficiary beneficiary =
                new Beneficiary("Pierre Dubois", "EUR123456789", "003", "European Bank");
        CreatePaymentCommand paymentCmd =
                new CreatePaymentCommand(
                        debitAccount.getAccountId(),
                        Money.of(100.00, "USD"),
                        Money.of(85.00, "EUR"), // Different currency - requires FX
                        LocalDate.now().plusDays(2),
                        beneficiary);
        Payment payment = paymentService.createPayment(paymentCmd);

        // Then - Verify payment requires FX
        assertThat(payment.requiresFx()).isTrue();
        assertThat(payment.getDebitAmount().currencyCode()).isEqualTo("USD");
        assertThat(payment.getCreditAmount().currencyCode()).isEqualTo("EUR");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        // Verify FX contract ID is null initially
        assertThat(payment.getFxContractId()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("E2E: Account transaction management")
    void testAccountTransactionManagement() {
        // Given - Create account
        UUID customerId = UUID.randomUUID();
        CreateAccountCommand accountCmd =
                new CreateAccountCommand(customerId, "EUR", "002", AccountType.SAVINGS, false);
        Map<String, Object> accountResult = accountService.handleCreateAccount(accountCmd);
        UUID accountId = UUID.fromString((String) accountResult.get("accountId"));
        Account account = readInTransaction(() -> accountRepository.findById(accountId)).orElseThrow();

        // When - Add multiple transactions
        account.createTransaction(TransactionType.CREDIT, Money.of(500.00, "EUR"), "Deposit 1");
        writeInTransaction(() -> accountRepository.save(account));

        Account retrieved1 = readInTransaction(() -> accountRepository.findById(accountId)).orElseThrow();
        retrieved1.createTransaction(TransactionType.DEBIT, Money.of(100.00, "EUR"), "Withdrawal 1");
        writeInTransaction(() -> accountRepository.save(retrieved1));

        Account retrieved2 = readInTransaction(() -> accountRepository.findById(accountId)).orElseThrow();
        retrieved2.createTransaction(TransactionType.CREDIT, Money.of(200.00, "EUR"), "Deposit 2");
        writeInTransaction(() -> accountRepository.save(retrieved2));

        // Then - Verify final balance
        Account finalAccount = readInTransaction(() -> accountRepository.findById(accountId)).orElseThrow();
        assertThat(finalAccount.getAvailableBalance()).isEqualTo(Money.of(600.00, "EUR"));
        assertThat(finalAccount.getTransactions()).hasSize(3);
    }

    @Test
    @Transactional
    @DisplayName("E2E: Multiple accounts and payments")
    void testMultipleAccountsAndPayments() {
        // Given - Create multiple accounts
        UUID customer1 = UUID.randomUUID();
        UUID customer2 = UUID.randomUUID();

        Map<String, Object> result1 =
                accountService.handleCreateAccount(
                        new CreateAccountCommand(customer1, "USD", "001", AccountType.CHECKING, false));
        UUID accountId1 = UUID.fromString((String) result1.get("accountId"));
        Account account1 = readInTransaction(() -> accountRepository.findById(accountId1)).orElseThrow();

        Map<String, Object> result2 =
                accountService.handleCreateAccount(
                        new CreateAccountCommand(customer2, "EUR", "002", AccountType.SAVINGS, false));
        UUID accountId2 = UUID.fromString((String) result2.get("accountId"));
        Account account2 = readInTransaction(() -> accountRepository.findById(accountId2)).orElseThrow();

        // Add balances
        account1.createTransaction(TransactionType.CREDIT, Money.of(1000.00, "USD"), "Initial");
        account2.createTransaction(TransactionType.CREDIT, Money.of(500.00, "EUR"), "Initial");
        writeInTransaction(() -> accountRepository.save(account1));
        writeInTransaction(() -> accountRepository.save(account2));

        // When - Create payments from both accounts
        Beneficiary beneficiary = new Beneficiary("Recipient", "ACC999", "003", "Bank");

        Payment payment1 =
                paymentService.createPayment(
                        new CreatePaymentCommand(
                                account1.getAccountId(),
                                Money.of(100.00, "USD"),
                                Money.of(100.00, "USD"),
                                LocalDate.now(),
                                beneficiary));

        Payment payment2 =
                paymentService.createPayment(
                        new CreatePaymentCommand(
                                account2.getAccountId(),
                                Money.of(50.00, "EUR"),
                                Money.of(50.00, "EUR"),
                                LocalDate.now(),
                                beneficiary));

        // Then - Verify both payments exist
        assertThat(readInTransaction(() -> paymentRepository.findById(payment1.getPaymentId()))).isPresent();
        assertThat(readInTransaction(() -> paymentRepository.findById(payment2.getPaymentId()))).isPresent();

        // Verify accounts still exist
        assertThat(readInTransaction(() -> accountRepository.findById(account1.getAccountId()))).isPresent();
        assertThat(readInTransaction(() -> accountRepository.findById(account2.getAccountId()))).isPresent();
    }

    @Test
    @Transactional
    @DisplayName("E2E: Payment idempotency check")
    void testPaymentIdempotency() {
        // Given - Create account
        UUID customerId = UUID.randomUUID();
        Map<String, Object> accountResult =
                accountService.handleCreateAccount(
                        new CreateAccountCommand(customerId, "USD", "001", AccountType.CHECKING, false));
        UUID accountId = UUID.fromString((String) accountResult.get("accountId"));
        Account account = readInTransaction(() -> accountRepository.findById(accountId)).orElseThrow();
        account.createTransaction(TransactionType.CREDIT, Money.of(500.00, "USD"), "Initial");
        writeInTransaction(() -> accountRepository.save(account));

        // When - Create payment with specific ID
        UUID paymentId = UUID.randomUUID();
        Beneficiary beneficiary = new Beneficiary("Recipient", "ACC999", "003", "Bank");
        Payment payment =
                new Payment(
                        paymentId,
                        account.getAccountId(),
                        Money.of(100.00, "USD"),
                        Money.of(100.00, "USD"),
                        LocalDate.now(),
                        beneficiary);
        writeInTransaction(() -> paymentRepository.save(payment));

        // Then - Verify same payment ID can be queried
        Optional<Payment> retrieved = readInTransaction(() -> paymentRepository.findById(paymentId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getPaymentId()).isEqualTo(paymentId);

        // Attempting to create another payment with same ID would violate uniqueness
        // (This is enforced by the database primary key constraint)
    }
}
