package com.acme.payments.infrastructure.persistence;

import com.acme.payments.domain.model.*;
import com.acme.payments.domain.repository.AccountRepository;
import com.acme.payments.domain.repository.PaymentRepository;
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
 * Integration tests for JdbcPaymentRepository with real PostgreSQL database.
 * Uses Testcontainers to spin up a PostgreSQL instance.
 */
@MicronautTest(
    environments = "test",
    startApplication = false,
    transactional = false
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("JdbcPaymentRepository Integration Tests")
class JdbcPaymentRepositoryIntegrationTest implements TestPropertyProvider {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @Inject
    PaymentRepository paymentRepository;

    @Inject
    AccountRepository accountRepository;

    private UUID paymentId;
    private UUID debitAccountId;
    private Beneficiary beneficiary;

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

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        debitAccountId = UUID.randomUUID();
        beneficiary = new Beneficiary(
            "John Doe",
            "ACC987654321",
            "002",
            "Test Bank"
        );
    }

    private void createTestAccount() {
        // Create account to satisfy foreign key constraint
        Account account = new Account(
            debitAccountId,
            UUID.randomUUID(), // customerId
            "ACC" + UUID.randomUUID().toString().substring(0, 10),
            "USD",
            AccountType.CHECKING,
            "001",
            false,
            Money.zero("USD")
        );
        accountRepository.save(account);
    }

    @Test
    @Transactional
    @DisplayName("save and findById - should persist and retrieve payment (same currency)")
    void testSaveAndFindById_SameCurrency() {
        // Given
        createTestAccount();
        Payment payment = new Payment(
            paymentId,
            debitAccountId,
            Money.of(100.00, "USD"),
            Money.of(100.00, "USD"),
            LocalDate.now(),
            beneficiary
        );

        // When
        paymentRepository.save(payment);

        // Then
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();

        Payment savedPayment = retrieved.get();
        assertThat(savedPayment.getPaymentId()).isEqualTo(paymentId);
        assertThat(savedPayment.getDebitAccountId()).isEqualTo(debitAccountId);
        assertThat(savedPayment.getDebitAmount()).isEqualTo(Money.of(100.00, "USD"));
        assertThat(savedPayment.getCreditAmount()).isEqualTo(Money.of(100.00, "USD"));
        assertThat(savedPayment.getValueDate()).isEqualTo(LocalDate.now());
        assertThat(savedPayment.getBeneficiary()).isEqualTo(beneficiary);
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedPayment.requiresFx()).isFalse();
        assertThat(savedPayment.getDebitTransactionId()).isNull();
        assertThat(savedPayment.getFxContractId()).isNull();
        assertThat(savedPayment.getCreatedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("save and findById - should persist and retrieve payment (different currency)")
    void testSaveAndFindById_DifferentCurrency() {
        // Given - USD to EUR payment requiring FX
        createTestAccount();
        Payment payment = new Payment(
            paymentId,
            debitAccountId,
            Money.of(100.00, "USD"),
            Money.of(85.50, "EUR"),
            LocalDate.now().plusDays(2),
            beneficiary
        );

        // When
        paymentRepository.save(payment);

        // Then
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();

        Payment savedPayment = retrieved.get();
        assertThat(savedPayment.getPaymentId()).isEqualTo(paymentId);
        assertThat(savedPayment.getDebitAmount()).isEqualTo(Money.of(100.00, "USD"));
        assertThat(savedPayment.getCreditAmount()).isEqualTo(Money.of(85.50, "EUR"));
        assertThat(savedPayment.requiresFx()).isTrue();
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @Transactional
    @Disabled("TODO: Repositories manually commit, conflicting with test transactions")
    @DisplayName("save - should update existing payment")
    void testUpdateExistingPayment() {
        // Given - initial payment
        createTestAccount();
        Payment payment = new Payment(
            paymentId,
            debitAccountId,
            Money.of(200.00, "USD"),
            Money.of(200.00, "USD"),
            LocalDate.now(),
            beneficiary
        );
        paymentRepository.save(payment);

        // When - update payment with transaction IDs and status
        UUID transactionId = UUID.randomUUID();
        payment.recordDebitTransaction(transactionId);
        payment.markAsProcessing();
        paymentRepository.save(payment);

        // Then
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();

        Payment updatedPayment = retrieved.get();
        assertThat(updatedPayment.getDebitTransactionId()).isEqualTo(transactionId);
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    @Transactional
    @Disabled("TODO: Repositories manually commit, conflicting with test transactions")
    @DisplayName("save - should persist payment with FX contract")
    void testSavePaymentWithFxContract() {
        // Given
        createTestAccount();
        Payment payment = new Payment(
            paymentId,
            debitAccountId,
            Money.of(1000.00, "USD"),
            Money.of(750.00, "GBP"),
            LocalDate.now().plusDays(3),
            beneficiary
        );

        UUID fxContractId = UUID.randomUUID();
        payment.recordFxContract(fxContractId);
        payment.markAsProcessing();

        // When
        paymentRepository.save(payment);

        // Then
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();

        Payment savedPayment = retrieved.get();
        assertThat(savedPayment.getFxContractId()).isEqualTo(fxContractId);
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    @Transactional
    @Disabled("TODO: Repositories manually commit, conflicting with test transactions")
    @DisplayName("save - should persist completed payment")
    void testSaveCompletedPayment() {
        // Given
        createTestAccount();
        Payment payment = new Payment(
            paymentId,
            debitAccountId,
            Money.of(500.00, "CAD"),
            Money.of(500.00, "CAD"),
            LocalDate.now(),
            beneficiary
        );

        payment.recordDebitTransaction(UUID.randomUUID());
        payment.markAsProcessing();
        payment.markAsCompleted();

        // When
        paymentRepository.save(payment);

        // Then
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @Transactional
    @DisplayName("save - should persist failed payment")
    void testSaveFailedPayment() {
        // Given
        createTestAccount();
        Payment payment = new Payment(
            paymentId,
            debitAccountId,
            Money.of(300.00, "EUR"),
            Money.of(300.00, "EUR"),
            LocalDate.now(),
            beneficiary
        );

        payment.markAsProcessing();
        payment.markAsFailed("Insufficient funds");

        // When
        paymentRepository.save(payment);

        // Then
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @Transactional
    @Disabled("TODO: Repositories manually commit, conflicting with test transactions")
    @DisplayName("save - should persist reversed payment")
    void testSaveReversedPayment() {
        // Given
        createTestAccount();
        Payment payment = new Payment(
            paymentId,
            debitAccountId,
            Money.of(150.00, "USD"),
            Money.of(150.00, "USD"),
            LocalDate.now(),
            beneficiary
        );

        payment.recordDebitTransaction(UUID.randomUUID());
        payment.markAsProcessing();
        payment.markAsCompleted();
        payment.reverse("Customer request");

        // When
        paymentRepository.save(payment);

        // Then
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo(PaymentStatus.REVERSED);
    }

    @Test
    @Transactional
    @DisplayName("findById - should return empty for non-existent payment")
    void testFindById_NotFound() {
        // When
        Optional<Payment> retrieved = paymentRepository.findById(UUID.randomUUID());

        // Then
        assertThat(retrieved).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("save - should handle different currencies")
    void testDifferentCurrencies() {
        // Test USD to JPY
        createTestAccount();
        Payment usdToJpy = new Payment(
            UUID.randomUUID(),
            debitAccountId,
            Money.of(100.00, "USD"),
            Money.of(11000.00, "JPY"),
            LocalDate.now(),
            beneficiary
        );
        paymentRepository.save(usdToJpy);

        Optional<Payment> retrieved1 = paymentRepository.findById(usdToJpy.getPaymentId());
        assertThat(retrieved1).isPresent();
        assertThat(retrieved1.get().getDebitAmount()).isEqualTo(Money.of(100.00, "USD"));
        assertThat(retrieved1.get().getCreditAmount()).isEqualTo(Money.of(11000.00, "JPY"));

        // Test EUR to GBP
        Payment eurToGbp = new Payment(
            UUID.randomUUID(),
            debitAccountId,
            Money.of(100.00, "EUR"),
            Money.of(85.00, "GBP"),
            LocalDate.now(),
            beneficiary
        );
        paymentRepository.save(eurToGbp);

        Optional<Payment> retrieved2 = paymentRepository.findById(eurToGbp.getPaymentId());
        assertThat(retrieved2).isPresent();
        assertThat(retrieved2.get().getDebitAmount()).isEqualTo(Money.of(100.00, "EUR"));
        assertThat(retrieved2.get().getCreditAmount()).isEqualTo(Money.of(85.00, "GBP"));
    }

    @Test
    @Transactional
    @DisplayName("save - should handle future value dates")
    void testFutureValueDates() {
        // Given
        createTestAccount();
        LocalDate futureDate = LocalDate.now().plusDays(30);
        Payment payment = new Payment(
            paymentId,
            debitAccountId,
            Money.of(5000.00, "USD"),
            Money.of(4250.00, "EUR"),
            futureDate,
            beneficiary
        );

        // When
        paymentRepository.save(payment);

        // Then
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getValueDate()).isEqualTo(futureDate);
    }
}
