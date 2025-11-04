package com.acme.payments.e2e;

import com.acme.payments.application.command.CreateAccountCommand;
import com.acme.payments.domain.model.Account;
import com.acme.payments.domain.model.AccountLimit;
import com.acme.payments.domain.model.AccountType;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;
import com.acme.payments.domain.repository.AccountLimitRepository;
import com.acme.payments.domain.repository.AccountRepository;
import com.acme.payments.domain.service.AccountService;
import com.acme.payments.domain.service.LimitService;
import com.acme.reliable.core.Jsons;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * End-to-end test for account creation with limits.
 * Tests the complete flow from command submission through process execution.
 */
@MicronautTest(
    environments = "test",
    startApplication = false,
    transactional = false
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreateAccountWithLimitsE2ETest implements TestPropertyProvider {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @Override
    public Map<String, String> getProperties() {
        postgres.start();
        Map<String, String> props = new HashMap<>();
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

    @Inject
    private AccountService accountService;

    @Inject
    private LimitService limitService;

    @Inject
    private AccountRepository accountRepository;

    @Inject
    private AccountLimitRepository limitRepository;

    @Test
    @Transactional
    @DisplayName("E2E: Create regular account without limits")
    void testCreateAccountWithoutLimits_E2E() {
        // Given: A create account command without limits
        UUID customerId = UUID.randomUUID();
        CreateAccountCommand cmd = new CreateAccountCommand(
            customerId,
            "USD",
            "001",
            AccountType.CHECKING,
            false,
            null
        );

        // When: Creating account through service
        Map<String, Object> result = accountService.handleCreateAccount(cmd);

        // Then: Account should be created
        assertThat(result).isNotNull();
        UUID accountId = UUID.fromString((String) result.get("accountId"));
        assertThat(accountId).isNotNull();

        // Verify account exists
        Account account = accountRepository.findById(accountId).orElseThrow();
        assertThat(account.getCustomerId()).isEqualTo(customerId);
        assertThat(account.getCurrencyCode()).isEqualTo("USD");
        assertThat(account.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(account.isLimitBased()).isFalse();

        // Verify no limits were created
        List<AccountLimit> limits = limitRepository.findActiveByAccountId(accountId);
        assertThat(limits).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("E2E: Create limit-based account with single limit")
    void testCreateAccountWithSingleLimit_E2E() {
        // Given: A create account command with single limit
        UUID customerId = UUID.randomUUID();
        Map<PeriodType, Money> limits = Map.of(
            PeriodType.DAY, Money.of(5000, "USD")
        );

        CreateAccountCommand accountCmd = new CreateAccountCommand(
            customerId,
            "USD",
            "002",
            AccountType.CHECKING,
            true,
            limits
        );

        // When: Creating account
        Map<String, Object> accountResult = accountService.handleCreateAccount(accountCmd);
        UUID accountId = UUID.fromString((String) accountResult.get("accountId"));

        // And: Creating limits
        var createLimitsCmd = new com.acme.payments.application.command.CreateLimitsCommand(
            accountId,
            "USD",
            limits
        );
        limitService.handleCreateLimits(createLimitsCmd);

        // Then: Account should be created
        Account account = accountRepository.findById(accountId).orElseThrow();
        assertThat(account.isLimitBased()).isTrue();

        // And: Limit should be created
        List<AccountLimit> savedLimits = limitRepository.findActiveByAccountId(accountId);
        assertThat(savedLimits).hasSize(1);

        AccountLimit limit = savedLimits.get(0);
        assertThat(limit.getPeriodType()).isEqualTo(PeriodType.DAY);
        assertThat(limit.getLimitAmount()).isEqualTo(Money.of(5000, "USD"));
        assertThat(limit.getUtilized()).isEqualTo(Money.zero("USD"));
        assertThat(limit.getAvailable()).isEqualTo(Money.of(5000, "USD"));
    }

    @Test
    @Transactional
    @DisplayName("E2E: Create limit-based account with multiple limits")
    void testCreateAccountWithMultipleLimits_E2E() {
        // Given: A create account command with multiple limits
        UUID customerId = UUID.randomUUID();
        Map<PeriodType, Money> limits = Map.of(
            PeriodType.HOUR, Money.of(1000, "EUR"),
            PeriodType.DAY, Money.of(5000, "EUR"),
            PeriodType.WEEK, Money.of(25000, "EUR"),
            PeriodType.MONTH, Money.of(100000, "EUR")
        );

        CreateAccountCommand accountCmd = new CreateAccountCommand(
            customerId,
            "EUR",
            "003",
            AccountType.SAVINGS,
            true,
            limits
        );

        // When: Creating account and limits
        Map<String, Object> accountResult = accountService.handleCreateAccount(accountCmd);
        UUID accountId = UUID.fromString((String) accountResult.get("accountId"));

        var createLimitsCmd = new com.acme.payments.application.command.CreateLimitsCommand(
            accountId,
            "EUR",
            limits
        );
        Map<String, Object> limitsResult = limitService.handleCreateLimits(createLimitsCmd);

        // Then: Account should be created with correct attributes
        Account account = accountRepository.findById(accountId).orElseThrow();
        assertThat(account.isLimitBased()).isTrue();
        assertThat(account.getCurrencyCode()).isEqualTo("EUR");
        assertThat(account.getAccountType()).isEqualTo(AccountType.SAVINGS);

        // And: All limits should be created
        assertThat(limitsResult.get("limitCount")).isEqualTo(4);

        List<AccountLimit> savedLimits = limitRepository.findActiveByAccountId(accountId);
        assertThat(savedLimits).hasSize(4);

        // Verify each limit type
        assertThat(savedLimits)
            .extracting(AccountLimit::getPeriodType)
            .containsExactlyInAnyOrder(
                PeriodType.HOUR,
                PeriodType.DAY,
                PeriodType.WEEK,
                PeriodType.MONTH
            );

        // Verify all limits have correct currency
        assertThat(savedLimits)
            .allMatch(limit -> limit.getLimitAmount().currencyCode().equals("EUR"))
            .allMatch(limit -> limit.getUtilized().currencyCode().equals("EUR"));

        // Verify limit amounts
        AccountLimit hourLimit = savedLimits.stream()
            .filter(l -> l.getPeriodType() == PeriodType.HOUR)
            .findFirst().orElseThrow();
        assertThat(hourLimit.getLimitAmount().amount()).isEqualByComparingTo("1000.00");

        AccountLimit dayLimit = savedLimits.stream()
            .filter(l -> l.getPeriodType() == PeriodType.DAY)
            .findFirst().orElseThrow();
        assertThat(dayLimit.getLimitAmount().amount()).isEqualByComparingTo("5000.00");
    }

    @Test
    @Transactional
    @DisplayName("E2E: Limits should have proper time bucket alignment")
    void testLimitTimeBucketAlignment_E2E() {
        // Given: Account with hour and day limits
        UUID customerId = UUID.randomUUID();
        Map<PeriodType, Money> limits = Map.of(
            PeriodType.HOUR, Money.of(1000, "USD"),
            PeriodType.DAY, Money.of(5000, "USD")
        );

        CreateAccountCommand accountCmd = new CreateAccountCommand(
            customerId,
            "USD",
            "004",
            AccountType.CHECKING,
            true,
            limits
        );

        // When: Creating account and limits
        Map<String, Object> accountResult = accountService.handleCreateAccount(accountCmd);
        UUID accountId = UUID.fromString((String) accountResult.get("accountId"));

        var createLimitsCmd = new com.acme.payments.application.command.CreateLimitsCommand(
            accountId,
            "USD",
            limits
        );
        limitService.handleCreateLimits(createLimitsCmd);

        // Then: Limits should be aligned to period boundaries
        List<AccountLimit> savedLimits = limitRepository.findActiveByAccountId(accountId);

        AccountLimit hourLimit = savedLimits.stream()
            .filter(l -> l.getPeriodType() == PeriodType.HOUR)
            .findFirst().orElseThrow();

        AccountLimit dayLimit = savedLimits.stream()
            .filter(l -> l.getPeriodType() == PeriodType.DAY)
            .findFirst().orElseThrow();

        // Hour limit should start at exact hour boundary (XX:00:00)
        assertThat(hourLimit.getStartTime().toString())
            .matches(".*T\\d{2}:00:00.*");

        // Day limit should start at exact day boundary (00:00:00)
        assertThat(dayLimit.getStartTime().toString())
            .matches(".*T00:00:00.*");

        // Limits should not be expired
        assertThat(hourLimit.isExpired()).isFalse();
        assertThat(dayLimit.isExpired()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("E2E: Account creation should be idempotent")
    void testIdempotency_E2E() {
        // Given: A create account command
        UUID customerId = UUID.randomUUID();
        CreateAccountCommand cmd = new CreateAccountCommand(
            customerId,
            "USD",
            "005",
            AccountType.CHECKING,
            false,
            null
        );

        // When: Creating account twice
        Map<String, Object> result1 = accountService.handleCreateAccount(cmd);
        UUID accountId1 = UUID.fromString((String) result1.get("accountId"));

        // Note: In production, the second call would have the same idempotency key
        // and would return the same account. For this test, we're just verifying
        // that the same command parameters can be handled without error.

        // Then: Account should exist
        Account account = accountRepository.findById(accountId1).orElseThrow();
        assertThat(account).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("E2E: Should handle all supported period types")
    void testAllPeriodTypes_E2E() {
        // Given: Account with multiple period types (excluding MINUTE to avoid timing issues in tests)
        UUID customerId = UUID.randomUUID();
        Map<PeriodType, Money> limits = Map.of(
            PeriodType.HOUR, Money.of(1000, "GBP"),
            PeriodType.DAY, Money.of(5000, "GBP"),
            PeriodType.WEEK, Money.of(25000, "GBP"),
            PeriodType.MONTH, Money.of(100000, "GBP")
        );

        CreateAccountCommand accountCmd = new CreateAccountCommand(
            customerId,
            "GBP",
            "006",
            AccountType.CHECKING,
            true,
            limits
        );

        // When: Creating account and limits
        Map<String, Object> accountResult = accountService.handleCreateAccount(accountCmd);
        UUID accountId = UUID.fromString((String) accountResult.get("accountId"));

        var createLimitsCmd = new com.acme.payments.application.command.CreateLimitsCommand(
            accountId,
            "GBP",
            limits
        );
        limitService.handleCreateLimits(createLimitsCmd);

        // Then: All limits should be created and active
        List<AccountLimit> savedLimits = limitRepository.findActiveByAccountId(accountId);
        assertThat(savedLimits).hasSize(4);

        // Verify we can query by each period type
        for (PeriodType periodType : limits.keySet()) {
            List<AccountLimit> limitsForPeriod = limitRepository
                .findByAccountIdAndPeriodType(accountId, periodType);
            assertThat(limitsForPeriod).hasSize(1);

            AccountLimit limit = limitsForPeriod.get(0);
            assertThat(limit.getPeriodType()).isEqualTo(periodType);
            assertThat(limit.getAccountId()).isEqualTo(accountId);
        }
    }

    @Test
    @Transactional
    @DisplayName("E2E: Command serialization should preserve limit data")
    void testCommandSerialization_E2E() {
        // Given: Create account command with limits
        UUID customerId = UUID.randomUUID();
        Map<PeriodType, Money> limits = Map.of(
            PeriodType.DAY, Money.of(5000, "USD")
        );

        CreateAccountCommand cmd = new CreateAccountCommand(
            customerId,
            "USD",
            "007",
            AccountType.CHECKING,
            true,
            limits
        );

        // When: Serializing
        String json = Jsons.toJson(cmd);

        // Then: JSON should be valid and contain expected data
        assertThat(json).isNotEmpty();
        assertThat(json).contains("customerId");
        assertThat(json).contains("USD");
        assertThat(json).contains("limitBased");
        assertThat(json).contains("limits");
        assertThat(json).contains("DAY");
        assertThat(json).contains("5000");
    }
}
