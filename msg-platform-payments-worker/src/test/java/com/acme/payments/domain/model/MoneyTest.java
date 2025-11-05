package com.acme.payments.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive unit tests for Money value object */
class MoneyTest {

  @Nested
  @DisplayName("Construction and Validation")
  class ConstructionTests {

    @Test
    @DisplayName("Should create Money with valid amount and currency")
    void testValidConstruction() {
      Money money = new Money(new BigDecimal("100.50"), "USD");

      assertThat(money.amount()).isEqualByComparingTo("100.50");
      assertThat(money.currencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Should create Money using of(BigDecimal) factory method")
    void testOfBigDecimal() {
      Money money = Money.of(new BigDecimal("50.75"), "EUR");

      assertThat(money.amount()).isEqualByComparingTo("50.75");
      assertThat(money.currencyCode()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Should create Money using of(double) factory method")
    void testOfDouble() {
      Money money = Money.of(100.0, "GBP");

      assertThat(money.amount()).isEqualByComparingTo("100.00");
      assertThat(money.currencyCode()).isEqualTo("GBP");
    }

    @Test
    @DisplayName("Should create zero Money")
    void testZero() {
      Money zero = Money.zero("USD");

      assertThat(zero.amount()).isEqualByComparingTo("0.00");
      assertThat(zero.currencyCode()).isEqualTo("USD");
      assertThat(zero.isZero()).isTrue();
    }

    @Test
    @DisplayName("Should reject null amount")
    void testNullAmount() {
      assertThatThrownBy(() -> new Money(null, "USD"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Amount cannot be null");
    }

    @Test
    @DisplayName("Should reject null currency code")
    void testNullCurrency() {
      assertThatThrownBy(() -> new Money(new BigDecimal("100"), null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Currency code cannot be null or blank");
    }

    @Test
    @DisplayName("Should reject blank currency code")
    void testBlankCurrency() {
      assertThatThrownBy(() -> new Money(new BigDecimal("100"), ""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Currency code cannot be null or blank");
    }

    @Test
    @DisplayName("Should reject invalid currency code")
    void testInvalidCurrency() {
      assertThatThrownBy(() -> new Money(new BigDecimal("100"), "INVALID"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid currency code");
    }

    @Test
    @DisplayName("Should reject amount with more than 2 decimal places")
    void testTooManyDecimals() {
      assertThatThrownBy(() -> new Money(new BigDecimal("100.123"), "USD"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Amount cannot have more than 2 decimal places");
    }

    @Test
    @DisplayName("Should accept negative amounts")
    void testNegativeAmount() {
      Money money = new Money(new BigDecimal("-50.00"), "USD");

      assertThat(money.amount()).isEqualByComparingTo("-50.00");
      assertThat(money.isNegative()).isTrue();
    }
  }

  @Nested
  @DisplayName("Arithmetic Operations")
  class ArithmeticTests {

    @Test
    @DisplayName("Should add two Money amounts with same currency")
    void testAdd() {
      Money money1 = Money.of(100.50, "USD");
      Money money2 = Money.of(50.25, "USD");

      Money result = money1.add(money2);

      assertThat(result.amount()).isEqualByComparingTo("150.75");
      assertThat(result.currencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Should add zero")
    void testAddZero() {
      Money money = Money.of(100.0, "USD");
      Money zero = Money.zero("USD");

      Money result = money.add(zero);

      assertThat(result.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Should throw exception when adding different currencies")
    void testAddDifferentCurrencies() {
      Money usd = Money.of(100.0, "USD");
      Money eur = Money.of(100.0, "EUR");

      assertThatThrownBy(() -> usd.add(eur))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot add money with different currencies");
    }

    @Test
    @DisplayName("Should subtract two Money amounts with same currency")
    void testSubtract() {
      Money money1 = Money.of(100.50, "USD");
      Money money2 = Money.of(50.25, "USD");

      Money result = money1.subtract(money2);

      assertThat(result.amount()).isEqualByComparingTo("50.25");
      assertThat(result.currencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Should subtract resulting in negative amount")
    void testSubtractNegativeResult() {
      Money money1 = Money.of(50.0, "USD");
      Money money2 = Money.of(100.0, "USD");

      Money result = money1.subtract(money2);

      assertThat(result.amount()).isEqualByComparingTo("-50.00");
      assertThat(result.isNegative()).isTrue();
    }

    @Test
    @DisplayName("Should throw exception when subtracting different currencies")
    void testSubtractDifferentCurrencies() {
      Money usd = Money.of(100.0, "USD");
      Money eur = Money.of(50.0, "EUR");

      assertThatThrownBy(() -> usd.subtract(eur))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot subtract money with different currencies");
    }

    @Test
    @DisplayName("Should multiply by scalar")
    void testMultiply() {
      Money money = Money.of(100.0, "USD");
      BigDecimal multiplier = new BigDecimal("1.5");

      Money result = money.multiply(multiplier);

      assertThat(result.amount()).isEqualByComparingTo("150.00");
      assertThat(result.currencyCode()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Should multiply by zero")
    void testMultiplyByZero() {
      Money money = Money.of(100.0, "USD");

      Money result = money.multiply(BigDecimal.ZERO);

      assertThat(result.amount()).isEqualByComparingTo("0.00");
      assertThat(result.isZero()).isTrue();
    }

    @Test
    @DisplayName("Should multiply by negative scalar")
    void testMultiplyByNegative() {
      Money money = Money.of(100.0, "USD");
      BigDecimal multiplier = new BigDecimal("-0.5");

      Money result = money.multiply(multiplier);

      assertThat(result.amount()).isEqualByComparingTo("-50.00");
      assertThat(result.isNegative()).isTrue();
    }

    @Test
    @DisplayName("Should round result to 2 decimal places when multiplying")
    void testMultiplyRounding() {
      Money money = Money.of(10.0, "USD");
      BigDecimal multiplier = new BigDecimal("0.333");

      Money result = money.multiply(multiplier);

      // 10.0 * 0.333 = 3.33 (rounded)
      assertThat(result.amount()).isEqualByComparingTo("3.33");
    }
  }

  @Nested
  @DisplayName("Comparison Operations")
  class ComparisonTests {

    @Test
    @DisplayName("Should identify positive amounts")
    void testIsPositive() {
      assertThat(Money.of(100.0, "USD").isPositive()).isTrue();
      assertThat(Money.of(0.01, "USD").isPositive()).isTrue();
      assertThat(Money.of(0.0, "USD").isPositive()).isFalse();
      assertThat(Money.of(-0.01, "USD").isPositive()).isFalse();
    }

    @Test
    @DisplayName("Should identify negative amounts")
    void testIsNegative() {
      assertThat(Money.of(-100.0, "USD").isNegative()).isTrue();
      assertThat(Money.of(-0.01, "USD").isNegative()).isTrue();
      assertThat(Money.of(0.0, "USD").isNegative()).isFalse();
      assertThat(Money.of(0.01, "USD").isNegative()).isFalse();
    }

    @Test
    @DisplayName("Should identify zero amounts")
    void testIsZero() {
      assertThat(Money.zero("USD").isZero()).isTrue();
      assertThat(Money.of(0.0, "USD").isZero()).isTrue();
      assertThat(Money.of(0.01, "USD").isZero()).isFalse();
      assertThat(Money.of(-0.01, "USD").isZero()).isFalse();
    }

    @Test
    @DisplayName("Should compare greater than with same currency")
    void testGreaterThan() {
      Money money1 = Money.of(100.0, "USD");
      Money money2 = Money.of(50.0, "USD");

      assertThat(money1.greaterThan(money2)).isTrue();
      assertThat(money2.greaterThan(money1)).isFalse();
      assertThat(money1.greaterThan(money1)).isFalse();
    }

    @Test
    @DisplayName("Should throw exception when comparing greater than with different currencies")
    void testGreaterThanDifferentCurrencies() {
      Money usd = Money.of(100.0, "USD");
      Money eur = Money.of(50.0, "EUR");

      assertThatThrownBy(() -> usd.greaterThan(eur))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot compare money with different currencies");
    }

    @Test
    @DisplayName("Should compare less than with same currency")
    void testLessThan() {
      Money money1 = Money.of(50.0, "USD");
      Money money2 = Money.of(100.0, "USD");

      assertThat(money1.lessThan(money2)).isTrue();
      assertThat(money2.lessThan(money1)).isFalse();
      assertThat(money1.lessThan(money1)).isFalse();
    }

    @Test
    @DisplayName("Should throw exception when comparing less than with different currencies")
    void testLessThanDifferentCurrencies() {
      Money usd = Money.of(100.0, "USD");
      Money eur = Money.of(50.0, "EUR");

      assertThatThrownBy(() -> usd.lessThan(eur))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cannot compare money with different currencies");
    }
  }

  @Nested
  @DisplayName("Equality and Immutability")
  class EqualityTests {

    @Test
    @DisplayName("Should be equal when amount and currency match")
    void testEquality() {
      Money money1 = Money.of(100.0, "USD");
      Money money2 = Money.of(100.0, "USD");

      assertThat(money1).isEqualTo(money2);
      assertThat(money1.hashCode()).isEqualTo(money2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when amounts differ")
    void testInequalityAmount() {
      Money money1 = Money.of(100.0, "USD");
      Money money2 = Money.of(50.0, "USD");

      assertThat(money1).isNotEqualTo(money2);
    }

    @Test
    @DisplayName("Should not be equal when currencies differ")
    void testInequalityCurrency() {
      Money money1 = Money.of(100.0, "USD");
      Money money2 = Money.of(100.0, "EUR");

      assertThat(money1).isNotEqualTo(money2);
    }

    @Test
    @DisplayName("Should be immutable - operations return new instances")
    void testImmutability() {
      Money original = Money.of(100.0, "USD");
      Money other = Money.of(50.0, "USD");

      Money sum = original.add(other);
      Money difference = original.subtract(other);
      Money product = original.multiply(new BigDecimal("2"));

      // Original should remain unchanged
      assertThat(original.amount()).isEqualByComparingTo("100.00");

      // Operations should return new instances
      assertThat(sum).isNotSameAs(original);
      assertThat(difference).isNotSameAs(original);
      assertThat(product).isNotSameAs(original);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle very large amounts")
    void testLargeAmounts() {
      Money money = Money.of(new BigDecimal("999999999.99"), "USD");

      assertThat(money.amount()).isEqualByComparingTo("999999999.99");
    }

    @Test
    @DisplayName("Should handle very small positive amounts")
    void testSmallAmounts() {
      Money money = Money.of(0.01, "USD");

      assertThat(money.amount()).isEqualByComparingTo("0.01");
      assertThat(money.isPositive()).isTrue();
    }

    @Test
    @DisplayName("Should handle various valid currency codes")
    void testVariousCurrencies() {
      assertThatNoException().isThrownBy(() -> Money.of(100.0, "USD"));
      assertThatNoException().isThrownBy(() -> Money.of(100.0, "EUR"));
      assertThatNoException().isThrownBy(() -> Money.of(100.0, "GBP"));
      assertThatNoException().isThrownBy(() -> Money.of(100.0, "JPY"));
      assertThatNoException().isThrownBy(() -> Money.of(100.0, "CAD"));
    }

    @Test
    @DisplayName("Should handle precision in arithmetic operations")
    void testPrecision() {
      Money money1 = Money.of(0.1, "USD");
      Money money2 = Money.of(0.2, "USD");

      Money sum = money1.add(money2);

      // 0.1 + 0.2 should equal 0.30, not 0.30000000000000004
      assertThat(sum.amount()).isEqualByComparingTo("0.30");
    }
  }
}
