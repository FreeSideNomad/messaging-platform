package com.acme.payments.infrastructure.persistence;

import com.acme.payments.domain.model.*;
import com.acme.payments.domain.repository.AccountRepository;
import com.acme.reliable.processor.command.AutoCommandHandlerRegistry;
import com.acme.reliable.processor.process.ProcessManager;
import com.acme.reliable.repository.ProcessRepository;
import com.acme.reliable.spi.CommandQueue;
import com.acme.reliable.spi.EventPublisher;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for JdbcAccountRepository with real PostgreSQL database.
 * Uses Testcontainers to spin up a PostgreSQL instance.
 */
@MicronautTest(
    environments = "test",
    startApplication = false
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("JdbcAccountRepository Integration Tests")
class JdbcAccountRepositoryIntegrationTest implements TestPropertyProvider {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @Inject
    AccountRepository accountRepository;

    private UUID customerId;
    private UUID accountId;

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

    @MockBean(CommandQueue.class)
    CommandQueue commandQueue() {
        return mock(CommandQueue.class);
    }

    @MockBean(EventPublisher.class)
    EventPublisher eventPublisher() {
        return mock(EventPublisher.class);
    }

    @Override
    public Map<String, String> getProperties() {
        postgres.start();
        // Use HashMap to allow more than 10 entries
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
        customerId = UUID.randomUUID();
        accountId = UUID.randomUUID();
    }

    @Test
    @DisplayName("save and findById - should persist and retrieve account")
    void testSaveAndFindById() {
        // Given
        Account account = new Account(
            accountId,
            customerId,
            "ACC123456789",
            "USD",
            AccountType.CHECKING,
            "001",
            false,
            Money.zero("USD")
        );

        // When
        accountRepository.save(account);

        // Then
        Optional<Account> retrieved = accountRepository.findById(accountId);
        assertThat(retrieved).isPresent();

        Account savedAccount = retrieved.get();
        assertThat(savedAccount.getAccountId()).isEqualTo(accountId);
        assertThat(savedAccount.getCustomerId()).isEqualTo(customerId);
        assertThat(savedAccount.getAccountNumber()).isEqualTo("ACC123456789");
        assertThat(savedAccount.getCurrencyCode()).isEqualTo("USD");
        assertThat(savedAccount.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(savedAccount.getTransitNumber()).isEqualTo("001");
        assertThat(savedAccount.isLimitBased()).isFalse();
        assertThat(savedAccount.getAvailableBalance()).isEqualTo(Money.zero("USD"));
        assertThat(savedAccount.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("save - should persist account with transactions")
    void testSaveWithTransactions() {
        // Given
        Account account = new Account(
            accountId,
            customerId,
            "ACC987654321",
            "EUR",
            AccountType.SAVINGS,
            "002",
            false,
            Money.zero("EUR")
        );

        // Add transactions
        account.createTransaction(TransactionType.CREDIT, Money.of(1000, "EUR"), "Initial deposit");
        account.createTransaction(TransactionType.DEBIT, Money.of(200, "EUR"), "Withdrawal");

        // When
        accountRepository.save(account);

        // Then
        Optional<Account> retrieved = accountRepository.findById(accountId);
        assertThat(retrieved).isPresent();

        Account savedAccount = retrieved.get();
        assertThat(savedAccount.getAvailableBalance()).isEqualTo(Money.of(800, "EUR"));
        // Note: Transactions are managed by the aggregate but loaded separately in production
    }

    @Test
    @DisplayName("save - should update existing account")
    void testUpdateExistingAccount() {
        // Given - Create initial account
        Account account = new Account(
            accountId,
            customerId,
            "ACC111222333",
            "USD",
            AccountType.CHECKING,
            "003",
            false,
            Money.zero("USD")
        );
        accountRepository.save(account);

        // When - Add transaction and save again
        account.createTransaction(TransactionType.CREDIT, Money.of(500, "USD"), "Deposit");
        accountRepository.save(account);

        // Then
        Optional<Account> retrieved = accountRepository.findById(accountId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getAvailableBalance()).isEqualTo(Money.of(500, "USD"));
    }

    @Test
    @DisplayName("save - should handle multiple transactions correctly")
    void testMultipleTransactions() {
        // Given
        Account account = new Account(
            accountId,
            customerId,
            "ACC444555666",
            "GBP",
            AccountType.CHECKING,
            "004",
            false,
            Money.zero("GBP")
        );

        // When - Add multiple transactions
        account.createTransaction(TransactionType.CREDIT, Money.of(1000, "GBP"), "Deposit 1");
        account.createTransaction(TransactionType.CREDIT, Money.of(500, "GBP"), "Deposit 2");
        account.createTransaction(TransactionType.DEBIT, Money.of(300, "GBP"), "Withdrawal 1");
        account.createTransaction(TransactionType.DEBIT, Money.of(100, "GBP"), "Withdrawal 2");

        accountRepository.save(account);

        // Then
        Optional<Account> retrieved = accountRepository.findById(accountId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getAvailableBalance()).isEqualTo(Money.of(1100, "GBP"));
    }

    @Test
    @DisplayName("findById - should return empty when account does not exist")
    void testFindById_NotFound() {
        // When
        Optional<Account> result = accountRepository.findById(UUID.randomUUID());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByAccountNumber - should retrieve account by account number")
    void testFindByAccountNumber() {
        // Given
        Account account = new Account(
            accountId,
            customerId,
            "ACC777888999",
            "CAD",
            AccountType.SAVINGS,
            "005",
            true,  // limit-based
            Money.zero("CAD")
        );
        accountRepository.save(account);

        // When
        Optional<Account> retrieved = accountRepository.findByAccountNumber("ACC777888999");

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getAccountId()).isEqualTo(accountId);
        assertThat(retrieved.get().getAccountNumber()).isEqualTo("ACC777888999");
        assertThat(retrieved.get().isLimitBased()).isTrue();
    }

    @Test
    @DisplayName("findByAccountNumber - should return empty when account number does not exist")
    void testFindByAccountNumber_NotFound() {
        // When
        Optional<Account> result = accountRepository.findByAccountNumber("NONEXISTENT");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("save - should enforce account number uniqueness")
    void testAccountNumberUniqueness() {
        // Given
        Account account1 = new Account(
            UUID.randomUUID(),
            customerId,
            "ACC_UNIQUE",
            "USD",
            AccountType.CHECKING,
            "006",
            false,
            Money.zero("USD")
        );
        accountRepository.save(account1);

        Account account2 = new Account(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "ACC_UNIQUE",  // Same account number
            "USD",
            AccountType.CHECKING,
            "006",
            false,
            Money.zero("USD")
        );

        // When / Then - Should throw exception due to unique constraint
        assertThatThrownBy(() -> accountRepository.save(account2))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to save account");
    }

    @Test
    @DisplayName("save - should persist limit-based account correctly")
    void testLimitBasedAccount() {
        // Given
        Account account = new Account(
            accountId,
            customerId,
            "ACC_LIMIT_001",
            "JPY",
            AccountType.CHECKING,
            "007",
            true,  // limit-based
            Money.zero("JPY")
        );

        // When
        accountRepository.save(account);

        // Then
        Optional<Account> retrieved = accountRepository.findById(accountId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().isLimitBased()).isTrue();
    }

    @Test
    @DisplayName("save - should handle different account types")
    void testDifferentAccountTypes() {
        // Test CHECKING
        UUID checkingId = UUID.randomUUID();
        Account checking = new Account(
            checkingId,
            customerId,
            "ACC_CHECK_001",
            "USD",
            AccountType.CHECKING,
            "008",
            false,
            Money.zero("USD")
        );
        accountRepository.save(checking);

        // Test SAVINGS
        UUID savingsId = UUID.randomUUID();
        Account savings = new Account(
            savingsId,
            customerId,
            "ACC_SAVE_001",
            "USD",
            AccountType.SAVINGS,
            "008",
            false,
            Money.zero("USD")
        );
        accountRepository.save(savings);

        // Verify
        assertThat(accountRepository.findById(checkingId).get().getAccountType())
            .isEqualTo(AccountType.CHECKING);
        assertThat(accountRepository.findById(savingsId).get().getAccountType())
            .isEqualTo(AccountType.SAVINGS);
    }

    @Test
    @DisplayName("save - should handle different currencies")
    void testDifferentCurrencies() {
        // Test USD
        UUID usdAccountId = UUID.randomUUID();
        Account usdAccount = new Account(
            usdAccountId,
            customerId,
            "ACC_USD_001",
            "USD",
            AccountType.CHECKING,
            "009",
            false,
            Money.zero("USD")
        );
        usdAccount.createTransaction(TransactionType.CREDIT, Money.of(100, "USD"), "USD deposit");
        accountRepository.save(usdAccount);

        // Test EUR
        UUID eurAccountId = UUID.randomUUID();
        Account eurAccount = new Account(
            eurAccountId,
            customerId,
            "ACC_EUR_001",
            "EUR",
            AccountType.CHECKING,
            "009",
            false,
            Money.zero("EUR")
        );
        eurAccount.createTransaction(TransactionType.CREDIT, Money.of(85, "EUR"), "EUR deposit");
        accountRepository.save(eurAccount);

        // Verify
        assertThat(accountRepository.findById(usdAccountId).get().getAvailableBalance())
            .isEqualTo(Money.of(100, "USD"));
        assertThat(accountRepository.findById(eurAccountId).get().getAvailableBalance())
            .isEqualTo(Money.of(85, "EUR"));
    }
}
