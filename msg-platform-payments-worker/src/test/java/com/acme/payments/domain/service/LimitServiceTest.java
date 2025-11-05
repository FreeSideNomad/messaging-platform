package com.acme.payments.domain.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.acme.payments.application.command.BookLimitsCommand;
import com.acme.payments.application.command.CreateLimitsCommand;
import com.acme.payments.application.command.ReverseLimitsCommand;
import com.acme.payments.domain.model.AccountLimit;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;
import com.acme.payments.domain.repository.AccountLimitRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for LimitService.handleCreateLimits */
@ExtendWith(MockitoExtension.class)
class LimitServiceTest {

  @Mock private AccountLimitRepository limitRepository;

  @InjectMocks private LimitService limitService;

  @Captor private ArgumentCaptor<AccountLimit> limitCaptor;

  private UUID accountId;

  @BeforeEach
  void setUp() {
    accountId = UUID.randomUUID();
  }

  @Test
  @DisplayName("Should create limits for all specified period types")
  void testCreateLimits_Success() {
    // Given: Command with multiple limits
    Map<PeriodType, Money> limits =
        Map.of(
            PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000.00), "USD"),
            PeriodType.DAY, Money.of(BigDecimal.valueOf(5000.00), "USD"),
            PeriodType.MONTH, Money.of(BigDecimal.valueOf(50000.00), "USD"));
    CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "USD", limits);

    // When: Handling the command
    Map<String, Object> result = limitService.handleCreateLimits(cmd);

    // Then: All limits should be created
    verify(limitRepository, times(3)).save(limitCaptor.capture());

    List<AccountLimit> savedLimits = limitCaptor.getAllValues();
    assertThat(savedLimits).hasSize(3);

    // Verify each limit
    assertThat(savedLimits)
        .extracting(AccountLimit::getPeriodType)
        .containsExactlyInAnyOrder(PeriodType.HOUR, PeriodType.DAY, PeriodType.MONTH);

    assertThat(savedLimits)
        .allMatch(limit -> limit.getAccountId().equals(accountId))
        .allMatch(limit -> limit.getLimitId() != null)
        .allMatch(limit -> limit.getStartTime() != null)
        .allMatch(limit -> limit.getEndTime() != null);

    // Verify result contains limit IDs and count
    assertThat(result).isNotNull();
    assertThat(result.get("limitCount")).isEqualTo(3);
    assertThat(result.get("limitIds")).asList().hasSize(3);
  }

  @Test
  @DisplayName("Should create single limit")
  void testCreateSingleLimit() {
    // Given: Command with single limit
    Map<PeriodType, Money> limits =
        Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(5000.00), "USD"));
    CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "USD", limits);

    // When: Handling the command
    Map<String, Object> result = limitService.handleCreateLimits(cmd);

    // Then: Single limit should be created
    verify(limitRepository, times(1)).save(any(AccountLimit.class));
    assertThat(result.get("limitCount")).isEqualTo(1);
  }

  @Test
  @DisplayName("Should align start time to period boundaries")
  void testTimeBucketAlignment() {
    // Given: Command with hour limit
    Map<PeriodType, Money> limits =
        Map.of(PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000.00), "USD"));
    CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "USD", limits);

    // When: Handling the command
    Instant before = Instant.now().truncatedTo(ChronoUnit.HOURS);
    limitService.handleCreateLimits(cmd);
    Instant after = before.plus(1, ChronoUnit.HOURS);

    // Then: Start time should be aligned to hour boundary
    verify(limitRepository).save(limitCaptor.capture());
    AccountLimit savedLimit = limitCaptor.getValue();

    assertThat(savedLimit.getStartTime()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);

    // Start time should be at exact hour boundary (no minutes/seconds)
    assertThat(savedLimit.getStartTime().truncatedTo(ChronoUnit.HOURS))
        .isEqualTo(savedLimit.getStartTime());
  }

  @Test
  @DisplayName("Should set correct limit amounts")
  void testLimitAmounts() {
    // Given: Command with specific limit amounts
    Money hourLimit = Money.of(BigDecimal.valueOf(1000.00), "USD");
    Money dayLimit = Money.of(BigDecimal.valueOf(5000.00), "USD");

    Map<PeriodType, Money> limits =
        Map.of(
            PeriodType.HOUR, hourLimit,
            PeriodType.DAY, dayLimit);
    CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "USD", limits);

    // When: Handling the command
    limitService.handleCreateLimits(cmd);

    // Then: Limits should have correct amounts
    verify(limitRepository, times(2)).save(limitCaptor.capture());

    List<AccountLimit> savedLimits = limitCaptor.getAllValues();

    AccountLimit hourLimitSaved =
        savedLimits.stream()
            .filter(l -> l.getPeriodType() == PeriodType.HOUR)
            .findFirst()
            .orElseThrow();

    AccountLimit dayLimitSaved =
        savedLimits.stream()
            .filter(l -> l.getPeriodType() == PeriodType.DAY)
            .findFirst()
            .orElseThrow();

    assertThat(hourLimitSaved.getLimitAmount()).isEqualTo(hourLimit);
    assertThat(dayLimitSaved.getLimitAmount()).isEqualTo(dayLimit);
  }

  @Test
  @DisplayName("Should initialize utilized amount to zero")
  void testUtilizedInitialization() {
    // Given: Command with limit
    Map<PeriodType, Money> limits =
        Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(5000.00), "USD"));
    CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "USD", limits);

    // When: Handling the command
    limitService.handleCreateLimits(cmd);

    // Then: Utilized should be zero
    verify(limitRepository).save(limitCaptor.capture());
    AccountLimit savedLimit = limitCaptor.getValue();

    assertThat(savedLimit.getUtilized()).isEqualTo(Money.zero("USD"));
  }

  @Test
  @DisplayName("Should calculate correct end time based on period type")
  void testEndTimeCalculation() {
    // Given: Command with different period types
    Map<PeriodType, Money> limits =
        Map.of(
            PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000.00), "USD"),
            PeriodType.DAY, Money.of(BigDecimal.valueOf(5000.00), "USD"));
    CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "USD", limits);

    // When: Handling the command
    limitService.handleCreateLimits(cmd);

    // Then: End times should be calculated correctly
    verify(limitRepository, times(2)).save(limitCaptor.capture());

    List<AccountLimit> savedLimits = limitCaptor.getAllValues();

    AccountLimit hourLimit =
        savedLimits.stream()
            .filter(l -> l.getPeriodType() == PeriodType.HOUR)
            .findFirst()
            .orElseThrow();

    AccountLimit dayLimit =
        savedLimits.stream()
            .filter(l -> l.getPeriodType() == PeriodType.DAY)
            .findFirst()
            .orElseThrow();

    // Hour limit should end 1 hour after start
    long hoursDiff = ChronoUnit.HOURS.between(hourLimit.getStartTime(), hourLimit.getEndTime());
    assertThat(hoursDiff).isEqualTo(1);

    // Day limit should end 1 day after start
    long daysDiff = ChronoUnit.DAYS.between(dayLimit.getStartTime(), dayLimit.getEndTime());
    assertThat(daysDiff).isEqualTo(1);
  }

  @Test
  @DisplayName("Should handle all period types")
  void testAllPeriodTypes() {
    // Given: Command with all period types
    Map<PeriodType, Money> limits =
        Map.of(
            PeriodType.MINUTE, Money.of(BigDecimal.valueOf(100.00), "USD"),
            PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000.00), "USD"),
            PeriodType.DAY, Money.of(BigDecimal.valueOf(5000.00), "USD"),
            PeriodType.WEEK, Money.of(BigDecimal.valueOf(25000.00), "USD"),
            PeriodType.MONTH, Money.of(BigDecimal.valueOf(100000.00), "USD"));
    CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "USD", limits);

    // When: Handling the command
    Map<String, Object> result = limitService.handleCreateLimits(cmd);

    // Then: All limits should be created
    verify(limitRepository, times(5)).save(any(AccountLimit.class));
    assertThat(result.get("limitCount")).isEqualTo(5);
  }

  @Test
  @DisplayName("Should handle different currencies")
  void testDifferentCurrencies() {
    // Given: Command with EUR currency
    Map<PeriodType, Money> limits =
        Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(5000.00), "EUR"));
    CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "EUR", limits);

    // When: Handling the command
    limitService.handleCreateLimits(cmd);

    // Then: Limit should have EUR currency
    verify(limitRepository).save(limitCaptor.capture());
    AccountLimit savedLimit = limitCaptor.getValue();

    assertThat(savedLimit.getLimitAmount().currencyCode()).isEqualTo("EUR");
    assertThat(savedLimit.getUtilized().currencyCode()).isEqualTo("EUR");
  }

  @Test
  @DisplayName("Should return list of created limit IDs")
  void testReturnedLimitIds() {
    // Given: Command with limits
    Map<PeriodType, Money> limits =
        Map.of(
            PeriodType.DAY, Money.of(BigDecimal.valueOf(5000.00), "USD"),
            PeriodType.MONTH, Money.of(BigDecimal.valueOf(50000.00), "USD"));
    CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "USD", limits);

    // When: Handling the command
    Map<String, Object> result = limitService.handleCreateLimits(cmd);

    // Then: Result should contain limit IDs
    assertThat(result).containsKeys("limitIds", "limitCount");

    @SuppressWarnings("unchecked")
    List<String> limitIds = (List<String>) result.get("limitIds");

    assertThat(limitIds).hasSize(2);
    assertThat(limitIds).allMatch(id -> UUID.fromString(id) != null);
  }

  @Test
  @DisplayName("bookLimits - should book amount against all active limits")
  void testBookLimits_Success() {
    // Given: Active limits for an account
    AccountLimit hourLimit = createLimit(PeriodType.HOUR, Money.of(1000, "USD"));
    AccountLimit dayLimit = createLimit(PeriodType.DAY, Money.of(5000, "USD"));
    when(limitRepository.findActiveByAccountId(accountId)).thenReturn(List.of(hourLimit, dayLimit));

    BookLimitsCommand cmd = new BookLimitsCommand(accountId, Money.of(100, "USD"));

    // When
    limitService.bookLimits(cmd);

    // Then: Both limits should be updated
    assertThat(hourLimit.getUtilized()).isEqualTo(Money.of(100, "USD"));
    assertThat(dayLimit.getUtilized()).isEqualTo(Money.of(100, "USD"));
    verify(limitRepository, times(2)).save(any(AccountLimit.class));
  }

  @Test
  @DisplayName("bookLimits - should handle no active limits gracefully")
  void testBookLimits_NoLimits() {
    // Given: No active limits
    when(limitRepository.findActiveByAccountId(accountId)).thenReturn(List.of());

    BookLimitsCommand cmd = new BookLimitsCommand(accountId, Money.of(100, "USD"));

    // When
    limitService.bookLimits(cmd);

    // Then: No errors and no saves
    verify(limitRepository, never()).save(any(AccountLimit.class));
  }

  @Test
  @DisplayName("bookLimits - should skip expired limits")
  void testBookLimits_SkipExpired() {
    // Given: One active and one expired limit
    Instant pastTime = Instant.now().minus(2, ChronoUnit.HOURS);
    AccountLimit expiredLimit =
        new AccountLimit(
            UUID.randomUUID(), accountId, PeriodType.HOUR, pastTime, Money.of(1000, "USD"));
    AccountLimit activeLimit = createLimit(PeriodType.DAY, Money.of(5000, "USD"));

    when(limitRepository.findActiveByAccountId(accountId))
        .thenReturn(List.of(expiredLimit, activeLimit));

    BookLimitsCommand cmd = new BookLimitsCommand(accountId, Money.of(100, "USD"));

    // When
    limitService.bookLimits(cmd);

    // Then: Only active limit should be updated
    assertThat(expiredLimit.getUtilized()).isEqualTo(Money.zero("USD")); // Not booked
    assertThat(activeLimit.getUtilized()).isEqualTo(Money.of(100, "USD")); // Booked
    verify(limitRepository, times(1)).save(activeLimit);
  }

  @Test
  @DisplayName("reverseLimits - should reverse amount from all active limits")
  void testReverseLimits_Success() {
    // Given: Active limits with utilized amounts
    AccountLimit hourLimit = createLimit(PeriodType.HOUR, Money.of(1000, "USD"));
    hourLimit.book(Money.of(300, "USD"));

    AccountLimit dayLimit = createLimit(PeriodType.DAY, Money.of(5000, "USD"));
    dayLimit.book(Money.of(300, "USD"));

    when(limitRepository.findActiveByAccountId(accountId)).thenReturn(List.of(hourLimit, dayLimit));

    // When
    limitService.reverseLimits(accountId, Money.of(100, "USD"));

    // Then: Utilized should be reduced
    assertThat(hourLimit.getUtilized()).isEqualTo(Money.of(200, "USD"));
    assertThat(dayLimit.getUtilized()).isEqualTo(Money.of(200, "USD"));
    verify(limitRepository, times(2)).save(any(AccountLimit.class));
  }

  @Test
  @DisplayName("reverseLimits - should handle full reversal")
  void testReverseLimits_FullReversal() {
    // Given: Active limit with booked amount
    AccountLimit limit = createLimit(PeriodType.DAY, Money.of(5000, "USD"));
    limit.book(Money.of(500, "USD"));

    when(limitRepository.findActiveByAccountId(accountId)).thenReturn(List.of(limit));

    // When: Reverse the full amount
    limitService.reverseLimits(accountId, Money.of(500, "USD"));

    // Then: Utilized should be zero
    assertThat(limit.getUtilized()).isEqualTo(Money.zero("USD"));
    assertThat(limit.getAvailable()).isEqualTo(Money.of(5000, "USD"));
    verify(limitRepository).save(limit);
  }

  @Test
  @DisplayName("handleReverseLimits - should delegate to reverseLimits")
  void testHandleReverseLimits() {
    // Given: Active limit
    AccountLimit limit = createLimit(PeriodType.DAY, Money.of(5000, "USD"));
    limit.book(Money.of(300, "USD"));

    when(limitRepository.findActiveByAccountId(accountId)).thenReturn(List.of(limit));

    ReverseLimitsCommand cmd = new ReverseLimitsCommand(accountId, Money.of(100, "USD"));

    // When
    limitService.handleReverseLimits(cmd);

    // Then
    assertThat(limit.getUtilized()).isEqualTo(Money.of(200, "USD"));
    verify(limitRepository).save(limit);
  }

  // Helper method to create test limits
  private AccountLimit createLimit(PeriodType periodType, Money limitAmount) {
    Instant startTime = periodType.alignToBucketStart(Instant.now());
    return new AccountLimit(UUID.randomUUID(), accountId, periodType, startTime, limitAmount);
  }
}
