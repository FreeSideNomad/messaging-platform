package com.acme.payments.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Currency;

/** Value object representing money with currency */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Money(BigDecimal amount, String currencyCode) implements Serializable {
  public Money {
    if (amount == null) {
      throw new IllegalArgumentException("Amount cannot be null");
    }
    if (currencyCode == null || currencyCode.isBlank()) {
      throw new IllegalArgumentException("Currency code cannot be null or blank");
    }
    if (amount.scale() > 2) {
      throw new IllegalArgumentException("Amount cannot have more than 2 decimal places");
    }

    // Validate currency code
    try {
      Currency.getInstance(currencyCode);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid currency code: " + currencyCode, e);
    }
  }

  public static Money zero(String currencyCode) {
    return new Money(BigDecimal.ZERO.setScale(2), currencyCode);
  }

  public static Money of(BigDecimal amount, String currencyCode) {
    return new Money(amount, currencyCode);
  }

  public static Money of(double amount, String currencyCode) {
    return new Money(
        BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP), currencyCode);
  }

  public Money add(Money other) {
    if (!this.currencyCode.equals(other.currencyCode)) {
      throw new IllegalArgumentException(
          "Cannot add money with different currencies: "
              + this.currencyCode
              + " vs "
              + other.currencyCode);
    }
    return new Money(
        this.amount.add(other.amount).setScale(2, java.math.RoundingMode.HALF_UP),
        this.currencyCode);
  }

  public Money subtract(Money other) {
    if (!this.currencyCode.equals(other.currencyCode)) {
      throw new IllegalArgumentException(
          "Cannot subtract money with different currencies: "
              + this.currencyCode
              + " vs "
              + other.currencyCode);
    }
    return new Money(
        this.amount.subtract(other.amount).setScale(2, java.math.RoundingMode.HALF_UP),
        this.currencyCode);
  }

  public Money multiply(BigDecimal multiplier) {
    return new Money(
        this.amount.multiply(multiplier).setScale(2, java.math.RoundingMode.HALF_UP),
        this.currencyCode);
  }

  @JsonIgnore
  public boolean isPositive() {
    return amount.compareTo(BigDecimal.ZERO) > 0;
  }

  @JsonIgnore
  public boolean isNegative() {
    return amount.compareTo(BigDecimal.ZERO) < 0;
  }

  @JsonIgnore
  public boolean isZero() {
    return amount.compareTo(BigDecimal.ZERO) == 0;
  }

  @JsonIgnore
  public boolean greaterThan(Money other) {
    if (!this.currencyCode.equals(other.currencyCode)) {
      throw new IllegalArgumentException("Cannot compare money with different currencies");
    }
    return this.amount.compareTo(other.amount) > 0;
  }

  @JsonIgnore
  public boolean lessThan(Money other) {
    if (!this.currencyCode.equals(other.currencyCode)) {
      throw new IllegalArgumentException("Cannot compare money with different currencies");
    }
    return this.amount.compareTo(other.amount) < 0;
  }
}
