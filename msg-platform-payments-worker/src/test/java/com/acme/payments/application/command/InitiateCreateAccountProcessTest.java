package com.acme.payments.application.command;

import static org.assertj.core.api.Assertions.*;

import com.acme.payments.domain.model.AccountType;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for InitiateCreateAccountProcess command validation */
@DisplayName("InitiateCreateAccountProcess Tests")
class InitiateCreateAccountProcessTest {

  @Test
  @DisplayName("Should create command with all valid parameters")
  void testCreateCommand_AllParametersValid() {
    // Given
    UUID customerId = UUID.randomUUID();
    Map<PeriodType, Money> limits =
        Map.of(
            PeriodType.DAY, Money.of(BigDecimal.valueOf(5000), "USD"),
            PeriodType.MONTH, Money.of(BigDecimal.valueOf(50000), "USD"));

    // When
    InitiateCreateAccountProcess cmd =
        new InitiateCreateAccountProcess(
            customerId, "USD", "001", AccountType.CHECKING, true, limits);

    // Then
    assertThat(cmd.customerId()).isEqualTo(customerId);
    assertThat(cmd.currencyCode()).isEqualTo("USD");
    assertThat(cmd.transitNumber()).isEqualTo("001");
    assertThat(cmd.accountType()).isEqualTo(AccountType.CHECKING);
    assertThat(cmd.limitBased()).isTrue();
    assertThat(cmd.limits()).hasSize(2);
    assertThat(cmd.limits().get(PeriodType.DAY))
        .isEqualTo(Money.of(BigDecimal.valueOf(5000), "USD"));
  }

  @Test
  @DisplayName("Should create non-limit-based account without limits")
  void testCreateCommand_NonLimitBasedAccount() {
    // Given
    UUID customerId = UUID.randomUUID();

    // When
    InitiateCreateAccountProcess cmd =
        new InitiateCreateAccountProcess(
            customerId, "EUR", "002", AccountType.SAVINGS, false, null);

    // Then
    assertThat(cmd.limitBased()).isFalse();
    assertThat(cmd.limits()).isNull();
    assertThat(cmd.currencyCode()).isEqualTo("EUR");
    assertThat(cmd.accountType()).isEqualTo(AccountType.SAVINGS);
  }

  @Test
  @DisplayName("Should throw exception when limit-based account has no limits")
  void testCreateCommand_LimitBasedWithoutLimits_ThrowsException() {
    // Given
    UUID customerId = UUID.randomUUID();

    // When/Then
    assertThatThrownBy(
            () ->
                new InitiateCreateAccountProcess(
                    customerId,
                    "USD",
                    "001",
                    AccountType.CHECKING,
                    true, // limitBased = true
                    null // but no limits provided
                    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Limit-based accounts must have at least one limit configuration");
  }

  @Test
  @DisplayName("Should throw exception when limit-based account has empty limits")
  void testCreateCommand_LimitBasedWithEmptyLimits_ThrowsException() {
    // Given
    UUID customerId = UUID.randomUUID();
    Map<PeriodType, Money> emptyLimits = new HashMap<>();

    // When/Then
    assertThatThrownBy(
            () ->
                new InitiateCreateAccountProcess(
                    customerId,
                    "USD",
                    "001",
                    AccountType.CHECKING,
                    true, // limitBased = true
                    emptyLimits // empty limits map
                    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Limit-based accounts must have at least one limit configuration");
  }

  @Test
  @DisplayName("Should throw exception when non-limit-based account has limits")
  void testCreateCommand_NonLimitBasedWithLimits_ThrowsException() {
    // Given
    UUID customerId = UUID.randomUUID();
    Map<PeriodType, Money> limits =
        Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(1000), "USD"));

    // When/Then
    assertThatThrownBy(
            () ->
                new InitiateCreateAccountProcess(
                    customerId,
                    "USD",
                    "001",
                    AccountType.CHECKING,
                    false, // limitBased = false
                    limits // but limits provided
                    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Non-limit-based accounts cannot have limit configurations");
  }

  @Test
  @DisplayName("Should throw exception when limit currency does not match account currency")
  void testCreateCommand_MismatchedCurrency_ThrowsException() {
    // Given
    UUID customerId = UUID.randomUUID();
    Map<PeriodType, Money> limits =
        Map.of(
            PeriodType.DAY, Money.of(BigDecimal.valueOf(1000), "EUR") // EUR limit
            );

    // When/Then
    assertThatThrownBy(
            () ->
                new InitiateCreateAccountProcess(
                    customerId,
                    "USD", // USD account
                    "001",
                    AccountType.CHECKING,
                    true,
                    limits))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("All limit amounts must match account currency: USD");
  }

  @Test
  @DisplayName("Should throw exception when one of multiple limits has mismatched currency")
  void testCreateCommand_MultipleLimitsOneMismatched_ThrowsException() {
    // Given
    UUID customerId = UUID.randomUUID();
    Map<PeriodType, Money> limits =
        Map.of(
            PeriodType.DAY, Money.of(BigDecimal.valueOf(1000), "USD"), // USD - correct
            PeriodType.MONTH, Money.of(BigDecimal.valueOf(10000), "EUR") // EUR - wrong!
            );

    // When/Then
    assertThatThrownBy(
            () ->
                new InitiateCreateAccountProcess(
                    customerId, "USD", "001", AccountType.CHECKING, true, limits))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("All limit amounts must match account currency: USD");
  }

  @Test
  @DisplayName("Should accept all period types with matching currency")
  void testCreateCommand_AllPeriodTypes_Success() {
    // Given
    UUID customerId = UUID.randomUUID();
    Map<PeriodType, Money> limits =
        Map.of(
            PeriodType.MINUTE, Money.of(BigDecimal.valueOf(100), "CAD"),
            PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000), "CAD"),
            PeriodType.DAY, Money.of(BigDecimal.valueOf(5000), "CAD"),
            PeriodType.WEEK, Money.of(BigDecimal.valueOf(25000), "CAD"),
            PeriodType.MONTH, Money.of(BigDecimal.valueOf(100000), "CAD"));

    // When
    InitiateCreateAccountProcess cmd =
        new InitiateCreateAccountProcess(
            customerId, "CAD", "003", AccountType.LINE_OF_CREDIT, true, limits);

    // Then
    assertThat(cmd.limits()).hasSize(5);
    assertThat(cmd.limits())
        .containsKeys(
            PeriodType.MINUTE, PeriodType.HOUR, PeriodType.DAY, PeriodType.WEEK, PeriodType.MONTH);
    assertThat(cmd.currencyCode()).isEqualTo("CAD");
  }

  @Test
  @DisplayName("Should work with different account types")
  void testCreateCommand_DifferentAccountTypes() {
    UUID customerId = UUID.randomUUID();

    // Test all account types
    for (AccountType accountType : AccountType.values()) {
      InitiateCreateAccountProcess cmd =
          new InitiateCreateAccountProcess(customerId, "GBP", "004", accountType, false, null);

      assertThat(cmd.accountType()).isEqualTo(accountType);
    }
  }

  @Test
  @DisplayName("Should allow zero amount limits")
  void testCreateCommand_ZeroAmountLimits() {
    // Given
    UUID customerId = UUID.randomUUID();
    Map<PeriodType, Money> limits = Map.of(PeriodType.DAY, Money.of(BigDecimal.ZERO, "USD"));

    // When
    InitiateCreateAccountProcess cmd =
        new InitiateCreateAccountProcess(
            customerId, "USD", "001", AccountType.CHECKING, true, limits);

    // Then
    assertThat(cmd.limits().get(PeriodType.DAY).amount()).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("Should allow large limit amounts")
  void testCreateCommand_LargeLimitAmounts() {
    // Given
    UUID customerId = UUID.randomUUID();
    BigDecimal largeAmount = new BigDecimal("999999999999.99");
    Map<PeriodType, Money> limits = Map.of(PeriodType.MONTH, Money.of(largeAmount, "USD"));

    // When
    InitiateCreateAccountProcess cmd =
        new InitiateCreateAccountProcess(
            customerId, "USD", "001", AccountType.CHECKING, true, limits);

    // Then
    assertThat(cmd.limits().get(PeriodType.MONTH).amount()).isEqualTo(largeAmount);
  }
}
