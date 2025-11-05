package com.acme.payments.infrastructure.persistence;

import com.acme.payments.domain.model.*;
import com.acme.payments.domain.repository.AccountRepository;
import com.acme.payments.domain.repository.FxContractRepository;
import com.acme.reliable.processor.command.AutoCommandHandlerRegistry;
import com.acme.reliable.processor.process.ProcessManager;
import com.acme.reliable.repository.ProcessRepository;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for JdbcFxContractRepository with real PostgreSQL database.
 */
@MicronautTest(
    environments = "test",
    startApplication = false
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("JdbcFxContractRepository Integration Tests")
class JdbcFxContractRepositoryIntegrationTest implements TestPropertyProvider {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @Inject
    FxContractRepository fxContractRepository;

    @Inject
    AccountRepository accountRepository;

    private UUID fxContractId;
    private UUID customerId;
    private UUID debitAccountId;

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
        props.put("datasources.default.maximum-pool-size", "10");
        props.put("datasources.default.minimum-idle", "2");
        props.put("flyway.datasources.default.enabled", "true");
        props.put("flyway.datasources.default.locations", "classpath:db/migration");
        props.put("jms.consumers.enabled", "false");
        return props;
    }

    @BeforeEach
    void setUp() {
        fxContractId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        debitAccountId = UUID.randomUUID();

        // Create the account that will be referenced by FX contracts
        Account account = new Account(
            debitAccountId,
            customerId,
            "ACC" + System.currentTimeMillis(),
            "USD",
            AccountType.CHECKING,
            "001",
            false,
            Money.zero("USD")
        );
        accountRepository.save(account);
    }

    @Test
    @DisplayName("save and findById - should persist and retrieve FX contract")
    void testSaveAndFindById() {
        // Given
        FxContract contract = new FxContract(
            fxContractId,
            customerId,
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(85, "EUR"),
            new BigDecimal("0.85"),
            LocalDate.now()
        );

        // When
        fxContractRepository.save(contract);
        Optional<FxContract> retrieved = fxContractRepository.findById(fxContractId);

        // Then
        assertThat(retrieved).isPresent();
        FxContract saved = retrieved.get();
        assertThat(saved.getFxContractId()).isEqualTo(fxContractId);
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getDebitAccountId()).isEqualTo(debitAccountId);
        assertThat(saved.getDebitAmount()).isEqualTo(Money.of(100, "USD"));
        assertThat(saved.getCreditAmount()).isEqualTo(Money.of(85, "EUR"));
        assertThat(saved.getRate()).isEqualByComparingTo("0.85");
        assertThat(saved.getStatus()).isEqualTo(FxStatus.BOOKED);
    }

    @Test
    @DisplayName("findById - should return empty for non-existent ID")
    void testFindByIdNotFound() {
        // When
        Optional<FxContract> result = fxContractRepository.findById(UUID.randomUUID());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("save - should update existing FX contract")
    void testUpdate() {
        // Given: Save initial contract
        FxContract contract = new FxContract(
            fxContractId,
            customerId,
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(85, "EUR"),
            new BigDecimal("0.85"),
            LocalDate.now()
        );
        fxContractRepository.save(contract);

        // When: Unwind and save again
        contract.unwind("Test unwind");
        fxContractRepository.save(contract);

        // Then: Status should be updated
        Optional<FxContract> retrieved = fxContractRepository.findById(fxContractId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo(FxStatus.UNWOUND);
    }

    @Test
    @DisplayName("save - should handle multiple FX contracts")
    void testMultipleContracts() {
        // Given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        FxContract contract1 = new FxContract(
            id1,
            customerId,
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(85, "EUR"),
            new BigDecimal("0.85"),
            LocalDate.now()
        );

        FxContract contract2 = new FxContract(
            id2,
            customerId,
            debitAccountId,
            Money.of(200, "GBP"),
            Money.of(250, "USD"),
            new BigDecimal("1.25"),
            LocalDate.now()
        );

        // When
        fxContractRepository.save(contract1);
        fxContractRepository.save(contract2);

        // Then
        Optional<FxContract> retrieved1 = fxContractRepository.findById(id1);
        Optional<FxContract> retrieved2 = fxContractRepository.findById(id2);

        assertThat(retrieved1).isPresent();
        assertThat(retrieved2).isPresent();
        assertThat(retrieved1.get().getDebitAmount().currencyCode()).isEqualTo("USD");
        assertThat(retrieved2.get().getDebitAmount().currencyCode()).isEqualTo("GBP");
    }

    @Test
    @DisplayName("save - should handle various currency pairs")
    void testVariousCurrencyPairs() {
        // USD to EUR
        UUID id1 = UUID.randomUUID();
        FxContract usdEur = new FxContract(
            id1,
            customerId,
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(85, "EUR"),
            new BigDecimal("0.85"),
            LocalDate.now()
        );
        fxContractRepository.save(usdEur);

        // GBP to JPY
        UUID id2 = UUID.randomUUID();
        FxContract gbpJpy = new FxContract(
            id2,
            customerId,
            debitAccountId,
            Money.of(100, "GBP"),
            Money.of(18000, "JPY"),
            new BigDecimal("180.00"),
            LocalDate.now()
        );
        fxContractRepository.save(gbpJpy);

        // Then
        Optional<FxContract> retrieved1 = fxContractRepository.findById(id1);
        Optional<FxContract> retrieved2 = fxContractRepository.findById(id2);

        assertThat(retrieved1).isPresent();
        assertThat(retrieved2).isPresent();
        assertThat(retrieved1.get().getCreditAmount().currencyCode()).isEqualTo("EUR");
        assertThat(retrieved2.get().getCreditAmount().currencyCode()).isEqualTo("JPY");
    }

    @Test
    @DisplayName("save - should handle high precision rates")
    void testHighPrecisionRate() {
        // Given
        BigDecimal preciseRate = new BigDecimal("0.853712");
        FxContract contract = new FxContract(
            fxContractId,
            customerId,
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(85.37, "EUR"),
            preciseRate,
            LocalDate.now()
        );

        // When
        fxContractRepository.save(contract);
        Optional<FxContract> retrieved = fxContractRepository.findById(fxContractId);

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getRate()).isEqualByComparingTo(preciseRate);
    }

    @Test
    @DisplayName("save - should handle large amounts")
    void testLargeAmounts() {
        // Given
        FxContract contract = new FxContract(
            fxContractId,
            customerId,
            debitAccountId,
            Money.of(1000000, "USD"),
            Money.of(850000, "EUR"),
            new BigDecimal("0.85"),
            LocalDate.now()
        );

        // When
        fxContractRepository.save(contract);
        Optional<FxContract> retrieved = fxContractRepository.findById(fxContractId);

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getDebitAmount()).isEqualTo(Money.of(1000000, "USD"));
        assertThat(retrieved.get().getCreditAmount()).isEqualTo(Money.of(850000, "EUR"));
    }

    @Test
    @DisplayName("save - should handle future value dates")
    void testFutureValueDate() {
        // Given
        LocalDate futureDate = LocalDate.now().plusMonths(3);
        FxContract contract = new FxContract(
            fxContractId,
            customerId,
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(85, "EUR"),
            new BigDecimal("0.85"),
            futureDate
        );

        // When
        fxContractRepository.save(contract);
        Optional<FxContract> retrieved = fxContractRepository.findById(fxContractId);

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getValueDate()).isEqualTo(futureDate);
    }

    @Test
    @DisplayName("save - should persist status changes")
    void testStatusPersistence() {
        // Given: Save as BOOKED
        FxContract contract = new FxContract(
            fxContractId,
            customerId,
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(85, "EUR"),
            new BigDecimal("0.85"),
            LocalDate.now()
        );
        fxContractRepository.save(contract);

        // Verify BOOKED status
        Optional<FxContract> booked = fxContractRepository.findById(fxContractId);
        assertThat(booked).isPresent();
        assertThat(booked.get().getStatus()).isEqualTo(FxStatus.BOOKED);

        // When: Unwind and save
        contract.unwind("Customer cancelled");
        fxContractRepository.save(contract);

        // Then: Status should be UNWOUND
        Optional<FxContract> unwound = fxContractRepository.findById(fxContractId);
        assertThat(unwound).isPresent();
        assertThat(unwound.get().getStatus()).isEqualTo(FxStatus.UNWOUND);
    }

    @Test
    @DisplayName("save - should handle decimal precision correctly")
    void testDecimalPrecision() {
        // Given: Amounts with exact 2 decimal places
        FxContract contract = new FxContract(
            fxContractId,
            customerId,
            debitAccountId,
            Money.of(100.50, "USD"),
            Money.of(85.43, "EUR"),
            new BigDecimal("0.8493"),
            LocalDate.now()
        );

        // When
        fxContractRepository.save(contract);
        Optional<FxContract> retrieved = fxContractRepository.findById(fxContractId);

        // Then: Amounts should maintain 2 decimal places
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getDebitAmount().amount()).isEqualByComparingTo("100.50");
        assertThat(retrieved.get().getCreditAmount().amount()).isEqualByComparingTo("85.43");
    }
}
