package com.acme.payments.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.acme.payments.domain.model.*;
import com.acme.payments.domain.repository.AccountLimitRepository;
import com.acme.payments.domain.repository.AccountRepository;
import com.acme.reliable.processor.process.ProcessManager;
import com.acme.reliable.repository.ProcessRepository;
import io.micronaut.test.annotation.MockBean;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;

/** Integration tests for JdbcAccountLimitRepository with real PostgreSQL database. */
@DisplayName("JdbcAccountLimitRepository Integration Tests")
class JdbcAccountLimitRepositoryIntegrationTest extends H2RepositoryTestBase {

  private AccountLimitRepository limitRepository;

  private AccountRepository accountRepository;

  private UUID accountId;
  private UUID limitId;

  @MockBean(ProcessRepository.class)
  ProcessRepository processRepository() {
    return mock(ProcessRepository.class);
  }

  @MockBean(ProcessManager.class)
  ProcessManager processManager() {
    return mock(ProcessManager.class);
  }

  @BeforeEach
  void setUp() throws Exception {
    super.setupSchema();
    DataSource dataSource = getDataSource();
    accountRepository = new JdbcAccountRepository(dataSource);
    limitRepository = new JdbcAccountLimitRepository(dataSource);

    accountId = UUID.randomUUID();
    limitId = UUID.randomUUID();
  }

  private void createTestAccount() {
    // Create account to satisfy foreign key constraint
    Account account =
        new Account(
            accountId,
            UUID.randomUUID(), // customerId
            "ACC" + UUID.randomUUID().toString().substring(0, 10),
            "USD",
            AccountType.CHECKING,
            "001",
            false,
            Money.zero("USD"));
    accountRepository.save(account);
  }

  @Test
  @DisplayName("save and findByAccountIdAndPeriodType - should persist and retrieve account limit")
  void testSaveAndFind() {
    // Given
    createTestAccount();
    Instant startTime = Instant.now().truncatedTo(ChronoUnit.HOURS);
    AccountLimit limit =
        new AccountLimit(limitId, accountId, PeriodType.DAY, startTime, Money.of(5000, "USD"));

    // When
    limitRepository.save(limit);
    List<AccountLimit> retrieved =
        limitRepository.findByAccountIdAndPeriodType(accountId, PeriodType.DAY);

    // Then
    assertThat(retrieved).hasSize(1);
    AccountLimit saved = retrieved.get(0);
    assertThat(saved.getLimitId()).isEqualTo(limitId);
    assertThat(saved.getAccountId()).isEqualTo(accountId);
    assertThat(saved.getPeriodType()).isEqualTo(PeriodType.DAY);
    assertThat(saved.getLimitAmount()).isEqualTo(Money.of(5000, "USD"));
    assertThat(saved.getUtilized()).isEqualTo(Money.zero("USD"));
    assertThat(saved.getAvailable()).isEqualTo(Money.of(5000, "USD"));
  }

  @Test
  @DisplayName("findByAccountIdAndPeriodType - should return empty for non-existent account")
  void testFindByAccountIdAndPeriodTypeNotFound() {
    // When
    List<AccountLimit> result =
        limitRepository.findByAccountIdAndPeriodType(UUID.randomUUID(), PeriodType.DAY);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("save - should update utilized amount")
  void testUpdateUtilized() {
    // Given: Create and save limit
    createTestAccount();
    Instant startTime = Instant.now().truncatedTo(ChronoUnit.HOURS);
    AccountLimit limit =
        new AccountLimit(limitId, accountId, PeriodType.DAY, startTime, Money.of(5000, "USD"));
    limitRepository.save(limit);

    // When: Book amount and save
    limit.book(Money.of(1000, "USD"));
    limitRepository.save(limit);

    // Then: Utilized should be updated
    List<AccountLimit> retrieved =
        limitRepository.findByAccountIdAndPeriodType(accountId, PeriodType.DAY);
    assertThat(retrieved).hasSize(1);
    assertThat(retrieved.get(0).getUtilized()).isEqualTo(Money.of(1000, "USD"));
    assertThat(retrieved.get(0).getAvailable()).isEqualTo(Money.of(4000, "USD"));
  }

  @Test
  @DisplayName("findActiveByAccountId - should return all active limits for account")
  void testFindActiveByAccountId() {
    createTestAccount();
    // Given: Create multiple limits for same account
    Instant now = Instant.now().truncatedTo(ChronoUnit.HOURS);

    AccountLimit hourLimit =
        new AccountLimit(UUID.randomUUID(), accountId, PeriodType.HOUR, now, Money.of(1000, "USD"));

    AccountLimit dayLimit =
        new AccountLimit(
            UUID.randomUUID(),
            accountId,
            PeriodType.DAY,
            now.truncatedTo(ChronoUnit.DAYS),
            Money.of(5000, "USD"));

    limitRepository.save(hourLimit);
    limitRepository.save(dayLimit);

    // When
    List<AccountLimit> limits = limitRepository.findActiveByAccountId(accountId);

    // Then
    assertThat(limits).hasSize(2);
    assertThat(limits)
        .extracting(AccountLimit::getPeriodType)
        .containsExactlyInAnyOrder(PeriodType.HOUR, PeriodType.DAY);
  }

  @Test
  @DisplayName("findActiveByAccountId - should return empty for non-existent account")
  void testFindActiveByAccountIdEmpty() {
    // When
    List<AccountLimit> limits = limitRepository.findActiveByAccountId(UUID.randomUUID());

    // Then
    assertThat(limits).isEmpty();
  }

  @Test
  @DisplayName("findActiveByAccountId - should not return expired limits")
  void testFindActiveByAccountIdExcludesExpired() {
    createTestAccount();
    // Given: Create one active and one expired limit
    Instant now = Instant.now();
    Instant pastTime = now.minus(2, ChronoUnit.HOURS);

    // Active limit
    AccountLimit activeLimit =
        new AccountLimit(
            UUID.randomUUID(),
            accountId,
            PeriodType.DAY,
            now.truncatedTo(ChronoUnit.DAYS),
            Money.of(5000, "USD"));

    // Expired limit (started 2 hours ago, period is HOUR)
    AccountLimit expiredLimit =
        new AccountLimit(
            UUID.randomUUID(),
            accountId,
            PeriodType.HOUR,
            pastTime.truncatedTo(ChronoUnit.HOURS),
            Money.of(1000, "USD"));

    limitRepository.save(activeLimit);
    limitRepository.save(expiredLimit);

    // When
    List<AccountLimit> limits = limitRepository.findActiveByAccountId(accountId);

    // Then: Should only return active limit
    assertThat(limits).hasSize(1);
    assertThat(limits.get(0).getPeriodType()).isEqualTo(PeriodType.DAY);
  }

  @Test
  @DisplayName("save - should handle all period types")
  void testAllPeriodTypes() {
    createTestAccount();
    // Given
    Instant now = Instant.now();

    AccountLimit hourLimit =
        new AccountLimit(
            UUID.randomUUID(),
            accountId,
            PeriodType.HOUR,
            now.truncatedTo(ChronoUnit.HOURS),
            Money.of(1000, "USD"));

    AccountLimit dayLimit =
        new AccountLimit(
            UUID.randomUUID(),
            accountId,
            PeriodType.DAY,
            now.truncatedTo(ChronoUnit.DAYS),
            Money.of(5000, "USD"));

    AccountLimit weekLimit =
        new AccountLimit(
            UUID.randomUUID(),
            accountId,
            PeriodType.WEEK,
            PeriodType.WEEK.alignToBucketStart(now),
            Money.of(25000, "USD"));

    AccountLimit monthLimit =
        new AccountLimit(
            UUID.randomUUID(),
            accountId,
            PeriodType.MONTH,
            PeriodType.MONTH.alignToBucketStart(now),
            Money.of(100000, "USD"));

    // When
    limitRepository.save(hourLimit);
    limitRepository.save(dayLimit);
    limitRepository.save(weekLimit);
    limitRepository.save(monthLimit);

    // Then
    List<AccountLimit> limits = limitRepository.findActiveByAccountId(accountId);
    assertThat(limits).hasSize(4);
    assertThat(limits)
        .extracting(AccountLimit::getPeriodType)
        .containsExactlyInAnyOrder(
            PeriodType.HOUR, PeriodType.DAY, PeriodType.WEEK, PeriodType.MONTH);
  }

  @Test
  @DisplayName("save - should handle various currencies")
  void testVariousCurrencies() {
    createTestAccount();
    // Given
    UUID usdLimitId = UUID.randomUUID();
    UUID eurLimitId = UUID.randomUUID();
    Instant startTime = Instant.now().truncatedTo(ChronoUnit.DAYS);

    AccountLimit usdLimit =
        new AccountLimit(usdLimitId, accountId, PeriodType.DAY, startTime, Money.of(5000, "USD"));

    // Create second account for EUR limit
    UUID otherAccountId = UUID.randomUUID();
    Account otherAccount =
        new Account(
            otherAccountId,
            UUID.randomUUID(),
            "ACC" + UUID.randomUUID().toString().substring(0, 10),
            "EUR",
            AccountType.CHECKING,
            "002",
            false,
            Money.zero("EUR"));
    accountRepository.save(otherAccount);

    AccountLimit eurLimit =
        new AccountLimit(
            eurLimitId, otherAccountId, PeriodType.DAY, startTime, Money.of(4000, "EUR"));

    // When
    limitRepository.save(usdLimit);
    limitRepository.save(eurLimit);

    // Then
    List<AccountLimit> retrievedUsd =
        limitRepository.findByAccountIdAndPeriodType(accountId, PeriodType.DAY);
    List<AccountLimit> retrievedEur =
        limitRepository.findByAccountIdAndPeriodType(otherAccountId, PeriodType.DAY);

    assertThat(retrievedUsd).hasSize(1);
    assertThat(retrievedEur).hasSize(1);
    assertThat(retrievedUsd.get(0).getLimitAmount().currencyCode()).isEqualTo("USD");
    assertThat(retrievedEur.get(0).getLimitAmount().currencyCode()).isEqualTo("EUR");
  }

  @Test
  @DisplayName("save - should persist booking and reversal operations")
  void testBookingAndReversal() {
    createTestAccount();
    // Given
    Instant startTime = Instant.now().truncatedTo(ChronoUnit.DAYS);
    AccountLimit limit =
        new AccountLimit(limitId, accountId, PeriodType.DAY, startTime, Money.of(5000, "USD"));
    limitRepository.save(limit);

    // When: Book amount
    limit.book(Money.of(1000, "USD"));
    limitRepository.save(limit);

    // Then: Verify booking
    List<AccountLimit> afterBooking =
        limitRepository.findByAccountIdAndPeriodType(accountId, PeriodType.DAY);
    assertThat(afterBooking).hasSize(1);
    assertThat(afterBooking.get(0).getUtilized()).isEqualTo(Money.of(1000, "USD"));

    // When: Reverse partial amount
    limit.reverse(Money.of(300, "USD"));
    limitRepository.save(limit);

    // Then: Verify reversal
    List<AccountLimit> afterReversal =
        limitRepository.findByAccountIdAndPeriodType(accountId, PeriodType.DAY);
    assertThat(afterReversal).hasSize(1);
    assertThat(afterReversal.get(0).getUtilized()).isEqualTo(Money.of(700, "USD"));
    assertThat(afterReversal.get(0).getAvailable()).isEqualTo(Money.of(4300, "USD"));
  }

  @Test
  @DisplayName("save - should handle large amounts")
  void testLargeAmounts() {
    createTestAccount();
    // Given
    Instant startTime = Instant.now().truncatedTo(ChronoUnit.DAYS);
    AccountLimit limit =
        new AccountLimit(limitId, accountId, PeriodType.MONTH, startTime, Money.of(1000000, "USD"));

    // When
    limitRepository.save(limit);
    List<AccountLimit> retrieved =
        limitRepository.findByAccountIdAndPeriodType(accountId, PeriodType.MONTH);

    // Then
    assertThat(retrieved).hasSize(1);
    assertThat(retrieved.get(0).getLimitAmount()).isEqualTo(Money.of(1000000, "USD"));
  }

  @Test
  @DisplayName("save - should maintain decimal precision")
  void testDecimalPrecision() {
    createTestAccount();
    // Given
    Instant startTime = Instant.now().truncatedTo(ChronoUnit.DAYS);
    AccountLimit limit =
        new AccountLimit(limitId, accountId, PeriodType.DAY, startTime, Money.of(5000.50, "USD"));

    limit.book(Money.of(1234.67, "USD"));

    // When
    limitRepository.save(limit);
    List<AccountLimit> retrieved =
        limitRepository.findByAccountIdAndPeriodType(accountId, PeriodType.DAY);

    // Then
    assertThat(retrieved).hasSize(1);
    assertThat(retrieved.get(0).getLimitAmount().amount()).isEqualByComparingTo("5000.50");
    assertThat(retrieved.get(0).getUtilized().amount()).isEqualByComparingTo("1234.67");
  }

  @Test
  @DisplayName("findActiveByAccountId - should handle multiple accounts")
  void testMultipleAccounts() {
    createTestAccount();
    // Given: Create limits for two different accounts
    UUID account1 = UUID.randomUUID();
    Account acc1 =
        new Account(
            account1,
            UUID.randomUUID(),
            "ACC" + UUID.randomUUID().toString().substring(0, 10),
            "USD",
            AccountType.CHECKING,
            "003",
            false,
            Money.zero("USD"));
    accountRepository.save(acc1);

    UUID account2 = UUID.randomUUID();
    Account acc2 =
        new Account(
            account2,
            UUID.randomUUID(),
            "ACC" + UUID.randomUUID().toString().substring(0, 10),
            "EUR",
            AccountType.CHECKING,
            "004",
            false,
            Money.zero("EUR"));
    accountRepository.save(acc2);

    Instant now = Instant.now().truncatedTo(ChronoUnit.DAYS);

    AccountLimit limit1 =
        new AccountLimit(UUID.randomUUID(), account1, PeriodType.DAY, now, Money.of(5000, "USD"));

    AccountLimit limit2 =
        new AccountLimit(UUID.randomUUID(), account2, PeriodType.DAY, now, Money.of(3000, "EUR"));

    limitRepository.save(limit1);
    limitRepository.save(limit2);

    // When
    List<AccountLimit> limits1 = limitRepository.findActiveByAccountId(account1);
    List<AccountLimit> limits2 = limitRepository.findActiveByAccountId(account2);

    // Then
    assertThat(limits1).hasSize(1);
    assertThat(limits2).hasSize(1);
    assertThat(limits1.get(0).getLimitAmount()).isEqualTo(Money.of(5000, "USD"));
    assertThat(limits2.get(0).getLimitAmount()).isEqualTo(Money.of(3000, "EUR"));
  }
}
