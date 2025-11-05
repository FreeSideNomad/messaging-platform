package com.acme.payments.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive unit tests for AccountLimit aggregate */
class AccountLimitTest {

  @Nested
  @DisplayName("Construction and Validation")
  class ConstructionTests {

    @Test
    @DisplayName("Should create AccountLimit with valid parameters")
    void testValidConstruction() {
      UUID limitId = UUID.randomUUID();
      UUID accountId = UUID.randomUUID();
      Instant startTime = Instant.now().truncatedTo(ChronoUnit.HOURS);
      Money limitAmount = Money.of(1000, "USD");

      AccountLimit limit =
          new AccountLimit(limitId, accountId, PeriodType.HOUR, startTime, limitAmount);

      assertThat(limit.getLimitId()).isEqualTo(limitId);
      assertThat(limit.getAccountId()).isEqualTo(accountId);
      assertThat(limit.getPeriodType()).isEqualTo(PeriodType.HOUR);
      assertThat(limit.getStartTime()).isEqualTo(startTime);
      assertThat(limit.getLimitAmount()).isEqualTo(limitAmount);
      assertThat(limit.getUtilized()).isEqualTo(Money.zero("USD"));
      assertThat(limit.getAvailable()).isEqualTo(limitAmount);
    }

    @Test
    @DisplayName("Should calculate correct end time for HOUR period")
    void testEndTimeHour() {
      Instant startTime = Instant.parse("2025-01-01T10:00:00Z");
      Money limitAmount = Money.of(1000, "USD");

      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(), UUID.randomUUID(), PeriodType.HOUR, startTime, limitAmount);

      assertThat(limit.getEndTime()).isEqualTo(Instant.parse("2025-01-01T11:00:00Z"));
    }

    @Test
    @DisplayName("Should calculate correct end time for DAY period")
    void testEndTimeDay() {
      Instant startTime = Instant.parse("2025-01-01T00:00:00Z");
      Money limitAmount = Money.of(5000, "USD");

      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(), UUID.randomUUID(), PeriodType.DAY, startTime, limitAmount);

      assertThat(limit.getEndTime()).isEqualTo(Instant.parse("2025-01-02T00:00:00Z"));
    }

    @Test
    @DisplayName("Should reject null limit ID")
    void testNullLimitId() {
      assertThatThrownBy(
              () ->
                  new AccountLimit(
                      null,
                      UUID.randomUUID(),
                      PeriodType.DAY,
                      Instant.now(),
                      Money.of(1000, "USD")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Limit ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null account ID")
    void testNullAccountId() {
      assertThatThrownBy(
              () ->
                  new AccountLimit(
                      UUID.randomUUID(),
                      null,
                      PeriodType.DAY,
                      Instant.now(),
                      Money.of(1000, "USD")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Account ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null period type")
    void testNullPeriodType() {
      assertThatThrownBy(
              () ->
                  new AccountLimit(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      null,
                      Instant.now(),
                      Money.of(1000, "USD")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Period type cannot be null");
    }

    @Test
    @DisplayName("Should reject null start time")
    void testNullStartTime() {
      assertThatThrownBy(
              () ->
                  new AccountLimit(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      PeriodType.DAY,
                      null,
                      Money.of(1000, "USD")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Start time cannot be null");
    }

    @Test
    @DisplayName("Should reject null limit amount")
    void testNullLimitAmount() {
      assertThatThrownBy(
              () ->
                  new AccountLimit(
                      UUID.randomUUID(), UUID.randomUUID(), PeriodType.DAY, Instant.now(), null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Limit amount must be positive");
    }

    @Test
    @DisplayName("Should reject zero limit amount")
    void testZeroLimitAmount() {
      assertThatThrownBy(
              () ->
                  new AccountLimit(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      PeriodType.DAY,
                      Instant.now(),
                      Money.zero("USD")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Limit amount must be positive");
    }

    @Test
    @DisplayName("Should reject negative limit amount")
    void testNegativeLimitAmount() {
      assertThatThrownBy(
              () ->
                  new AccountLimit(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      PeriodType.DAY,
                      Instant.now(),
                      Money.of(-100, "USD")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Limit amount must be positive");
    }
  }

  @Nested
  @DisplayName("Booking Operations")
  class BookingTests {

    @Test
    @DisplayName("Should book amount successfully when within limit")
    void testBookWithinLimit() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      limit.book(Money.of(500, "USD"));

      assertThat(limit.getUtilized()).isEqualTo(Money.of(500, "USD"));
      assertThat(limit.getAvailable()).isEqualTo(Money.of(500, "USD"));
    }

    @Test
    @DisplayName("Should book multiple amounts cumulatively")
    void testBookMultipleTimes() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      limit.book(Money.of(300, "USD"));
      limit.book(Money.of(200, "USD"));
      limit.book(Money.of(100, "USD"));

      assertThat(limit.getUtilized()).isEqualTo(Money.of(600, "USD"));
      assertThat(limit.getAvailable()).isEqualTo(Money.of(400, "USD"));
    }

    @Test
    @DisplayName("Should book exactly up to the limit")
    void testBookExactLimit() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      limit.book(Money.of(1000, "USD"));

      assertThat(limit.getUtilized()).isEqualTo(Money.of(1000, "USD"));
      assertThat(limit.getAvailable()).isEqualTo(Money.zero("USD"));
    }

    @Test
    @DisplayName("Should throw exception when booking exceeds limit")
    void testBookExceedsLimit() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      assertThatThrownBy(() -> limit.book(Money.of(1001, "USD")))
          .isInstanceOf(AccountLimit.LimitExceededException.class)
          .hasMessageContaining("Limit exceeded");
    }

    @Test
    @DisplayName("Should throw exception when cumulative booking exceeds limit")
    void testCumulativeExceedsLimit() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      limit.book(Money.of(600, "USD"));
      assertThatThrownBy(() -> limit.book(Money.of(500, "USD")))
          .isInstanceOf(AccountLimit.LimitExceededException.class)
          .hasMessageContaining("Limit exceeded");

      // Verify utilized amount didn't change after failed booking
      assertThat(limit.getUtilized()).isEqualTo(Money.of(600, "USD"));
    }

    @Test
    @DisplayName("Should reject booking with different currency")
    void testBookDifferentCurrency() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      assertThatThrownBy(() -> limit.book(Money.of(100, "EUR")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Amount currency must match limit currency");
    }

    @Test
    @DisplayName("LimitExceededException should contain all relevant information")
    void testLimitExceededException() {
      UUID limitId = UUID.randomUUID();
      AccountLimit limit =
          new AccountLimit(
              limitId, UUID.randomUUID(), PeriodType.DAY, Instant.now(), Money.of(1000, "USD"));

      limit.book(Money.of(800, "USD"));

      try {
        limit.book(Money.of(300, "USD"));
        fail("Should have thrown LimitExceededException");
      } catch (AccountLimit.LimitExceededException e) {
        assertThat(e.getLimitId()).isEqualTo(limitId);
        assertThat(e.getPeriodType()).isEqualTo(PeriodType.DAY);
        assertThat(e.getLimitAmount()).isEqualTo(Money.of(1000, "USD"));
        assertThat(e.getUtilized()).isEqualTo(Money.of(800, "USD"));
        assertThat(e.getRequestedAmount()).isEqualTo(Money.of(300, "USD"));
        assertThat(e.getMessage()).contains("Limit exceeded");
      }
    }
  }

  @Nested
  @DisplayName("Reversal Operations")
  class ReversalTests {

    @Test
    @DisplayName("Should reverse previously booked amount")
    void testReverse() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      limit.book(Money.of(600, "USD"));
      limit.reverse(Money.of(200, "USD"));

      assertThat(limit.getUtilized()).isEqualTo(Money.of(400, "USD"));
      assertThat(limit.getAvailable()).isEqualTo(Money.of(600, "USD"));
    }

    @Test
    @DisplayName("Should reverse entire utilized amount")
    void testReverseAll() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      limit.book(Money.of(500, "USD"));
      limit.reverse(Money.of(500, "USD"));

      assertThat(limit.getUtilized()).isEqualTo(Money.zero("USD"));
      assertThat(limit.getAvailable()).isEqualTo(Money.of(1000, "USD"));
    }

    @Test
    @DisplayName("Should handle reversal exceeding utilized amount gracefully")
    void testReverseExceedsUtilized() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      limit.book(Money.of(300, "USD"));
      limit.reverse(Money.of(500, "USD")); // More than booked

      // Should reset to zero, not go negative
      assertThat(limit.getUtilized()).isEqualTo(Money.zero("USD"));
      assertThat(limit.getAvailable()).isEqualTo(Money.of(1000, "USD"));
    }

    @Test
    @DisplayName("Should reject reversal with different currency")
    void testReverseDifferentCurrency() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      limit.book(Money.of(500, "USD"));

      assertThatThrownBy(() -> limit.reverse(Money.of(100, "EUR")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Amount currency must match limit currency");
    }

    @Test
    @DisplayName("Should handle book and reverse cycle")
    void testBookReverseCycle() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      // Book and reverse multiple times
      limit.book(Money.of(300, "USD"));
      assertThat(limit.getUtilized()).isEqualTo(Money.of(300, "USD"));

      limit.reverse(Money.of(100, "USD"));
      assertThat(limit.getUtilized()).isEqualTo(Money.of(200, "USD"));

      limit.book(Money.of(400, "USD"));
      assertThat(limit.getUtilized()).isEqualTo(Money.of(600, "USD"));

      limit.reverse(Money.of(600, "USD"));
      assertThat(limit.getUtilized()).isEqualTo(Money.zero("USD"));
    }
  }

  @Nested
  @DisplayName("Expiry Check")
  class ExpiryTests {

    @Test
    @DisplayName("Should identify expired limit (past end time)")
    void testExpired() {
      // Create a limit that ended 2 hours ago
      Instant startTime = Instant.now().minus(3, ChronoUnit.HOURS);
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.HOUR,
              startTime,
              Money.of(1000, "USD"));

      assertThat(limit.isExpired()).isTrue();
    }

    @Test
    @DisplayName("Should identify non-expired limit (before end time)")
    void testNotExpired() {
      // Create a limit starting now (will expire in 1 hour)
      Instant startTime = Instant.now().truncatedTo(ChronoUnit.HOURS);
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.HOUR,
              startTime,
              Money.of(1000, "USD"));

      assertThat(limit.isExpired()).isFalse();
    }

    @Test
    @DisplayName("Should handle DAY period expiry")
    void testDayPeriodExpiry() {
      // Create a limit that started yesterday
      Instant startTime = Instant.now().minus(25, ChronoUnit.HOURS).truncatedTo(ChronoUnit.DAYS);
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              startTime,
              Money.of(5000, "USD"));

      assertThat(limit.isExpired()).isTrue();
    }
  }

  @Nested
  @DisplayName("Available Amount Calculation")
  class AvailableAmountTests {

    @Test
    @DisplayName("Should calculate available with no utilization")
    void testAvailableNoUtilization() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      assertThat(limit.getAvailable()).isEqualTo(Money.of(1000, "USD"));
    }

    @Test
    @DisplayName("Should calculate available with partial utilization")
    void testAvailablePartialUtilization() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      limit.book(Money.of(350, "USD"));

      assertThat(limit.getAvailable()).isEqualTo(Money.of(650, "USD"));
    }

    @Test
    @DisplayName("Should calculate available with full utilization")
    void testAvailableFullUtilization() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      limit.book(Money.of(1000, "USD"));

      assertThat(limit.getAvailable()).isEqualTo(Money.zero("USD"));
    }

    @Test
    @DisplayName("Should update available after reversal")
    void testAvailableAfterReversal() {
      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(),
              UUID.randomUUID(),
              PeriodType.DAY,
              Instant.now(),
              Money.of(1000, "USD"));

      limit.book(Money.of(700, "USD"));
      assertThat(limit.getAvailable()).isEqualTo(Money.of(300, "USD"));

      limit.reverse(Money.of(200, "USD"));
      assertThat(limit.getAvailable()).isEqualTo(Money.of(500, "USD"));
    }
  }
}
