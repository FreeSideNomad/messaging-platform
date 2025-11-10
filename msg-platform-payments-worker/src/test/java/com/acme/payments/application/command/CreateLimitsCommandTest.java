package com.acme.payments.application.command;

import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CreateLimitsCommand validation
 */
class CreateLimitsCommandTest {

    @Test
    @DisplayName("Should create command successfully with valid limits")
    void testValidCommand() {
        // Given: Valid command parameters
        UUID accountId = UUID.randomUUID();
        Map<PeriodType, Money> limits =
                Map.of(
                        PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000.00), "USD"),
                        PeriodType.DAY, Money.of(BigDecimal.valueOf(5000.00), "USD"),
                        PeriodType.MONTH, Money.of(BigDecimal.valueOf(50000.00), "USD"));

        // When: Creating command
        CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "USD", limits);

        // Then: Command is created successfully
        assertThat(cmd).isNotNull();
        assertThat(cmd.accountId()).isEqualTo(accountId);
        assertThat(cmd.currencyCode()).isEqualTo("USD");
        assertThat(cmd.limits()).hasSize(3);
        assertThat(cmd.limits()).containsKeys(PeriodType.HOUR, PeriodType.DAY, PeriodType.MONTH);
    }

    @Test
    @DisplayName("Should throw exception when accountId is null")
    void testNullAccountId() {
        // Given: Null accountId
        Map<PeriodType, Money> limits =
                Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(1000.00), "USD"));

        // When/Then: Should throw exception
        assertThatThrownBy(() -> new CreateLimitsCommand(null, "USD", limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account ID cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when currencyCode is null")
    void testNullCurrencyCode() {
        // Given: Null currencyCode
        UUID accountId = UUID.randomUUID();
        Map<PeriodType, Money> limits =
                Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(1000.00), "USD"));

        // When/Then: Should throw exception
        assertThatThrownBy(() -> new CreateLimitsCommand(accountId, null, limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency code cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when currencyCode is blank")
    void testBlankCurrencyCode() {
        // Given: Blank currencyCode
        UUID accountId = UUID.randomUUID();
        Map<PeriodType, Money> limits =
                Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(1000.00), "USD"));

        // When/Then: Should throw exception
        assertThatThrownBy(() -> new CreateLimitsCommand(accountId, "  ", limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency code cannot be null or blank");
    }

    @Test
    @DisplayName("Should throw exception when limits map is null")
    void testNullLimits() {
        // Given: Null limits
        UUID accountId = UUID.randomUUID();

        // When/Then: Should throw exception
        assertThatThrownBy(() -> new CreateLimitsCommand(accountId, "USD", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limits map cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when limits map is empty")
    void testEmptyLimits() {
        // Given: Empty limits map
        UUID accountId = UUID.randomUUID();
        Map<PeriodType, Money> limits = new HashMap<>();

        // When/Then: Should throw exception
        assertThatThrownBy(() -> new CreateLimitsCommand(accountId, "USD", limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limits map cannot be null or empty");
    }

    @Test
    @DisplayName("Should throw exception when limit amount is null")
    void testNullLimitAmount() {
        // Given: Limit with null money value
        UUID accountId = UUID.randomUUID();
        Map<PeriodType, Money> limits = new HashMap<>();
        limits.put(PeriodType.DAY, null);

        // When/Then: Should throw exception
        assertThatThrownBy(() -> new CreateLimitsCommand(accountId, "USD", limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limit amount for DAY cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when limit currency does not match account currency")
    void testMismatchedCurrency() {
        // Given: Limit with different currency than account
        UUID accountId = UUID.randomUUID();
        Map<PeriodType, Money> limits =
                Map.of(
                        PeriodType.DAY, Money.of(BigDecimal.valueOf(1000.00), "EUR") // EUR instead of USD
                );

        // When/Then: Should throw exception
        assertThatThrownBy(() -> new CreateLimitsCommand(accountId, "USD", limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("All limit amounts must match account currency: USD")
                .hasMessageContaining("found EUR for DAY");
    }

    @Test
    @DisplayName("Should throw exception when limit amount is zero")
    void testZeroLimitAmount() {
        // Given: Limit with zero amount
        UUID accountId = UUID.randomUUID();
        Map<PeriodType, Money> limits = Map.of(PeriodType.DAY, Money.of(BigDecimal.ZERO, "USD"));

        // When/Then: Should throw exception
        assertThatThrownBy(() -> new CreateLimitsCommand(accountId, "USD", limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limit amount for DAY must be positive");
    }

    @Test
    @DisplayName("Should throw exception when limit amount is negative")
    void testNegativeLimitAmount() {
        // Given: Limit with negative amount
        UUID accountId = UUID.randomUUID();
        Map<PeriodType, Money> limits =
                Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(-1000.00), "USD"));

        // When/Then: Should throw exception
        assertThatThrownBy(() -> new CreateLimitsCommand(accountId, "USD", limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limit amount for DAY must be positive");
    }

    @Test
    @DisplayName("Should accept single limit")
    void testSingleLimit() {
        // Given: Single limit
        UUID accountId = UUID.randomUUID();
        Map<PeriodType, Money> limits =
                Map.of(PeriodType.DAY, Money.of(BigDecimal.valueOf(5000.00), "USD"));

        // When: Creating command
        CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "USD", limits);

        // Then: Command is created successfully
        assertThat(cmd).isNotNull();
        assertThat(cmd.limits()).hasSize(1);
        assertThat(cmd.limits()).containsKey(PeriodType.DAY);
    }

    @Test
    @DisplayName("Should accept all period types")
    void testAllPeriodTypes() {
        // Given: Limits for all period types
        UUID accountId = UUID.randomUUID();
        Map<PeriodType, Money> limits =
                Map.of(
                        PeriodType.MINUTE, Money.of(BigDecimal.valueOf(100.00), "USD"),
                        PeriodType.HOUR, Money.of(BigDecimal.valueOf(1000.00), "USD"),
                        PeriodType.DAY, Money.of(BigDecimal.valueOf(5000.00), "USD"),
                        PeriodType.WEEK, Money.of(BigDecimal.valueOf(25000.00), "USD"),
                        PeriodType.MONTH, Money.of(BigDecimal.valueOf(100000.00), "USD"));

        // When: Creating command
        CreateLimitsCommand cmd = new CreateLimitsCommand(accountId, "USD", limits);

        // Then: Command is created successfully with all period types
        assertThat(cmd).isNotNull();
        assertThat(cmd.limits()).hasSize(5);
        assertThat(cmd.limits())
                .containsKeys(
                        PeriodType.MINUTE, PeriodType.HOUR, PeriodType.DAY, PeriodType.WEEK, PeriodType.MONTH);
    }
}
