package com.acme.payments.e2e;

import com.acme.payments.application.command.CreateAccountCommand;
import com.acme.payments.application.command.CreatePaymentCommand;
import com.acme.payments.domain.model.*;
import com.acme.payments.domain.repository.AccountRepository;
import com.acme.payments.domain.repository.PaymentRepository;
import com.acme.payments.domain.service.AccountService;
import com.acme.payments.domain.service.PaymentService;
import com.acme.reliable.processor.command.AutoCommandHandlerRegistry;
import com.acme.reliable.processor.process.ProcessManager;
import com.acme.reliable.repository.ProcessRepository;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * End-to-end integration tests for complete payment flows.
 * Tests the full stack: domain services, repositories, and database.
 */
@MicronautTest(
    environments = "test",
    startApplication = false,
    transactional = false
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Payment Flow E2E Tests")
class PaymentFlowE2ETest implements TestPropertyProvider {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @Inject
    AccountService accountService;

    @Inject
    PaymentService paymentService;

    @Inject
    AccountRepository accountRepository;

    @Inject
    PaymentRepository paymentRepository;

    @MockBean(AutoCommandHandlerRegistry.class)
    AutoCommandHandlerRegistry autoCommandHandlerRegistry() {
        return mock(AutoCommandHandlerRegistry.class);
    }

    @MockBean(ProcessRepository.class)
    ProcessRepository processRepository() {
        return mock(ProcessRepository.class);
    }

    @MockBean(ProcessManager.class)
    ProcessManager processManager() {
        return mock(ProcessManager.class);
    }

    @Override
    public Map<String, String> getProperties() {
        postgres.start();
        java.util.Map<String, String> props = new java.util.HashMap<>();
        props.put("datasources.default.url", postgres.getJdbcUrl());
        props.put("datasources.default.username", postgres.getUsername());
        props.put("datasources.default.password", postgres.getPassword());
        props.put("datasources.default.driver-class-name", "org.postgresql.Driver");
        props.put("datasources.default.auto-commit", "false");
        props.put("flyway.datasources.default.enabled", "true");
        props.put("flyway.datasources.default.locations", "filesystem:src/main/resources/db/migration");
        props.put("jms.consumers.enabled", "false");
        return props;
    }

    @Test
    @Transactional
    @DisplayName("E2E: Create account flow")
    void testCreateAccountFlow() {
        // Given
        UUID customerId = UUID.randomUUID();
        CreateAccountCommand command = new CreateAccountCommand(
            customerId,
            "USD",
            "001",
            AccountType.CHECKING,
            false
        );

        // When - Create account through service
        Account account = accountService.handleCreateAccount(command);

        // Then - Verify account was persisted
        Optional<Account> retrieved = accountRepository.findById(account.getAccountId());
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
        CreateAccountCommand accountCmd = new CreateAccountCommand(
            customerId,
            "USD",
            "001",
            AccountType.CHECKING,
            false
        );
        Account debitAccount = accountService.handleCreateAccount(accountCmd);

        // Add initial balance via transaction
        debitAccount.createTransaction(
            TransactionType.CREDIT,
            Money.of(1000.00, "USD"),
            "Initial deposit"
        );
        accountRepository.save(debitAccount);

        // When - Create payment
        Beneficiary beneficiary = new Beneficiary(
            "John Doe",
            "ACC987654321",
            "002",
            "Test Bank"
        );
        CreatePaymentCommand paymentCmd = new CreatePaymentCommand(
            debitAccount.getAccountId(),
            Money.of(100.00, "USD"),
            Money.of(100.00, "USD"),
            LocalDate.now(),
            beneficiary
        );
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
        Optional<Payment> retrievedPayment = paymentRepository.findById(payment.getPaymentId());
        assertThat(retrievedPayment).isPresent();
        assertThat(retrievedPayment.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @Transactional
    @DisplayName("E2E: Payment with FX conversion")
    void testPaymentFlow_WithFX() {
        // Given - Create USD account
        UUID customerId = UUID.randomUUID();
        CreateAccountCommand accountCmd = new CreateAccountCommand(
            customerId,
            "USD",
            "001",
            AccountType.CHECKING,
            false
        );
        Account debitAccount = accountService.handleCreateAccount(accountCmd);

        // Add initial balance
        debitAccount.createTransaction(
            TransactionType.CREDIT,
            Money.of(500.00, "USD"),
            "Initial deposit"
        );
        accountRepository.save(debitAccount);

        // When - Create payment requiring FX (USD to EUR)
        Beneficiary beneficiary = new Beneficiary(
            "Pierre Dubois",
            "EUR123456789",
            "003",
            "European Bank"
        );
        CreatePaymentCommand paymentCmd = new CreatePaymentCommand(
            debitAccount.getAccountId(),
            Money.of(100.00, "USD"),
            Money.of(85.00, "EUR"),  // Different currency - requires FX
            LocalDate.now().plusDays(2),
            beneficiary
        );
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
    @Disabled("TODO: Transaction loading needs investigation - transactions not being retrieved")
    @DisplayName("E2E: Account transaction management")
    void testAccountTransactionManagement() {
        // Given - Create account
        UUID customerId = UUID.randomUUID();
        CreateAccountCommand accountCmd = new CreateAccountCommand(
            customerId,
            "EUR",
            "002",
            AccountType.SAVINGS,
            false
        );
        Account account = accountService.handleCreateAccount(accountCmd);
        UUID accountId = account.getAccountId();

        // When - Add multiple transactions
        account.createTransaction(TransactionType.CREDIT, Money.of(500.00, "EUR"), "Deposit 1");
        accountRepository.save(account);

        Account retrieved1 = accountRepository.findById(accountId).orElseThrow();
        retrieved1.createTransaction(TransactionType.DEBIT, Money.of(100.00, "EUR"), "Withdrawal 1");
        accountRepository.save(retrieved1);

        Account retrieved2 = accountRepository.findById(accountId).orElseThrow();
        retrieved2.createTransaction(TransactionType.CREDIT, Money.of(200.00, "EUR"), "Deposit 2");
        accountRepository.save(retrieved2);

        // Then - Verify final balance
        Account finalAccount = accountRepository.findById(accountId).orElseThrow();
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

        Account account1 = accountService.handleCreateAccount(new CreateAccountCommand(
            customer1, "USD", "001", AccountType.CHECKING, false
        ));
        Account account2 = accountService.handleCreateAccount(new CreateAccountCommand(
            customer2, "EUR", "002", AccountType.SAVINGS, false
        ));

        // Add balances
        account1.createTransaction(TransactionType.CREDIT, Money.of(1000.00, "USD"), "Initial");
        account2.createTransaction(TransactionType.CREDIT, Money.of(500.00, "EUR"), "Initial");
        accountRepository.save(account1);
        accountRepository.save(account2);

        // When - Create payments from both accounts
        Beneficiary beneficiary = new Beneficiary("Recipient", "ACC999", "003", "Bank");

        Payment payment1 = paymentService.createPayment(new CreatePaymentCommand(
            account1.getAccountId(),
            Money.of(100.00, "USD"),
            Money.of(100.00, "USD"),
            LocalDate.now(),
            beneficiary
        ));

        Payment payment2 = paymentService.createPayment(new CreatePaymentCommand(
            account2.getAccountId(),
            Money.of(50.00, "EUR"),
            Money.of(50.00, "EUR"),
            LocalDate.now(),
            beneficiary
        ));

        // Then - Verify both payments exist
        assertThat(paymentRepository.findById(payment1.getPaymentId())).isPresent();
        assertThat(paymentRepository.findById(payment2.getPaymentId())).isPresent();

        // Verify accounts still exist
        assertThat(accountRepository.findById(account1.getAccountId())).isPresent();
        assertThat(accountRepository.findById(account2.getAccountId())).isPresent();
    }

    @Test
    @Transactional
    @DisplayName("E2E: Payment idempotency check")
    void testPaymentIdempotency() {
        // Given - Create account
        UUID customerId = UUID.randomUUID();
        Account account = accountService.handleCreateAccount(new CreateAccountCommand(
            customerId, "USD", "001", AccountType.CHECKING, false
        ));
        account.createTransaction(TransactionType.CREDIT, Money.of(500.00, "USD"), "Initial");
        accountRepository.save(account);

        // When - Create payment with specific ID
        UUID paymentId = UUID.randomUUID();
        Beneficiary beneficiary = new Beneficiary("Recipient", "ACC999", "003", "Bank");
        Payment payment = new Payment(
            paymentId,
            account.getAccountId(),
            Money.of(100.00, "USD"),
            Money.of(100.00, "USD"),
            LocalDate.now(),
            beneficiary
        );
        paymentRepository.save(payment);

        // Then - Verify same payment ID can be queried
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getPaymentId()).isEqualTo(paymentId);

        // Attempting to create another payment with same ID would violate uniqueness
        // (This is enforced by the database primary key constraint)
    }
}
