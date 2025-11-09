package com.acme.payments.integration.testdata;

import com.acme.payments.domain.model.Account;
import com.acme.payments.domain.model.AccountLimit;
import com.acme.payments.domain.model.AccountType;
import com.acme.payments.domain.model.Beneficiary;
import com.acme.payments.domain.model.FxContract;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.Payment;
import com.acme.payments.domain.model.PeriodType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Test data builder for Payment-related domain models.
 * Provides factory methods for creating test entities with sensible defaults.
 */
public class PaymentTestData {

  // ============================================================================
  // Account Test Data
  // ============================================================================

  public static Account account() {
    return new Account(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "ACC" + System.nanoTime() % 1000000,
        "USD",
        AccountType.CHECKING,
        "001",
        false,
        Money.zero("USD"));
  }

  public static Account account(String accountNumber) {
    return new Account(
        UUID.randomUUID(),
        UUID.randomUUID(),
        accountNumber,
        "USD",
        AccountType.CHECKING,
        "001",
        false,
        Money.zero("USD"));
  }

  public static Account account(UUID customerId, String accountNumber) {
    return new Account(
        UUID.randomUUID(),
        customerId,
        accountNumber,
        "USD",
        AccountType.CHECKING,
        "001",
        false,
        Money.zero("USD"));
  }

  public static Account accountWithLimit(UUID customerId, String accountNumber) {
    return new Account(
        UUID.randomUUID(),
        customerId,
        accountNumber,
        "USD",
        AccountType.CHECKING,
        "001",
        true,
        Money.zero("USD"));
  }

  // ============================================================================
  // Account Limit Test Data
  // ============================================================================

  public static AccountLimit dailyLimit(UUID accountId, BigDecimal amount) {
    return new AccountLimit(
        UUID.randomUUID(),
        accountId,
        PeriodType.DAY,
        Instant.now(),
        Money.of(amount, "USD"));
  }

  public static AccountLimit monthlyLimit(UUID accountId, BigDecimal amount) {
    return new AccountLimit(
        UUID.randomUUID(),
        accountId,
        PeriodType.MONTH,
        Instant.now(),
        Money.of(amount, "USD"));
  }

  public static AccountLimit limit(UUID accountId, PeriodType period, BigDecimal amount) {
    return new AccountLimit(
        UUID.randomUUID(),
        accountId,
        period,
        Instant.now(),
        Money.of(amount, "USD"));
  }

  // ============================================================================
  // Payment Test Data
  // ============================================================================

  public static Payment payment(UUID debitAccountId) {
    return new Payment(
        UUID.randomUUID(),
        debitAccountId,
        Money.of(BigDecimal.valueOf(1000), "USD"),
        Money.of(BigDecimal.valueOf(1000), "USD"),
        LocalDate.now(),
        new Beneficiary("John Doe", "ACCT123456", "001", "BENEFICIARY BANK"));
  }

  public static Payment payment(
      UUID debitAccountId, BigDecimal amount, String currency) {
    return new Payment(
        UUID.randomUUID(),
        debitAccountId,
        Money.of(amount, currency),
        Money.of(amount, currency),
        LocalDate.now(),
        new Beneficiary("John Doe", "ACCT123456", "001", "BENEFICIARY BANK"));
  }

  public static Payment payment(
      UUID debitAccountId,
      BigDecimal debitAmount,
      String debitCurrency,
      BigDecimal creditAmount,
      String creditCurrency) {
    return new Payment(
        UUID.randomUUID(),
        debitAccountId,
        Money.of(debitAmount, debitCurrency),
        Money.of(creditAmount, creditCurrency),
        LocalDate.now(),
        new Beneficiary("John Doe", "ACCT123456", "001", "BENEFICIARY BANK"));
  }

  // ============================================================================
  // FX Contract Test Data
  // ============================================================================

  public static FxContract fxContract(UUID debitAccountId, UUID customerId) {
    return new FxContract(
        UUID.randomUUID(),
        customerId,
        debitAccountId,
        Money.of(BigDecimal.valueOf(1000), "USD"),
        Money.of(BigDecimal.valueOf(1250), "CAD"),
        BigDecimal.valueOf(1.25),
        LocalDate.now());
  }

  public static FxContract fxContract(
      UUID debitAccountId,
      UUID customerId,
      BigDecimal debitAmount,
      BigDecimal creditAmount,
      BigDecimal rate) {
    return new FxContract(
        UUID.randomUUID(),
        customerId,
        debitAccountId,
        Money.of(debitAmount, "USD"),
        Money.of(creditAmount, "CAD"),
        rate,
        LocalDate.now());
  }

  // ============================================================================
  // Command JSON Test Data (for JMS messages)
  // ============================================================================

  /**
   * Returns a CreateAccount command JSON payload for JMS
   */
  public static String createAccountCommandJson(UUID customerId, String accountNumber) {
    return """
        {
          "customerId": "%s",
          "accountNumber": "%s",
          "currencyCode": "USD",
          "accountType": "CHECKING",
          "transitNumber": "001"
        }
        """.formatted(customerId, accountNumber);
  }

  /**
   * Returns a CreateLimits command JSON payload for JMS
   */
  public static String createLimitsCommandJson(UUID accountId) {
    return """
        {
          "accountId": "%s",
          "dailyLimit": "10000",
          "currency": "USD"
        }
        """.formatted(accountId);
  }

  /**
   * Returns a CreatePayment command JSON payload for JMS
   */
  public static String createPaymentCommandJson(
      UUID debitAccountId, BigDecimal amount, String beneficiaryName) {
    return """
        {
          "debitAccountId": "%s",
          "debitAmount": "%s",
          "debitCurrency": "USD",
          "creditAmount": "%s",
          "creditCurrency": "USD",
          "valueDate": "%s",
          "beneficiary": {
            "name": "%s",
            "accountNumber": "ACCT123456",
            "transitNumber": "001",
            "bankName": "BENEFICIARY BANK"
          }
        }
        """
        .formatted(
            debitAccountId,
            amount,
            amount,
            LocalDate.now(),
            beneficiaryName);
  }

  // ============================================================================
  // Money Test Data
  // ============================================================================

  public static Money usd(BigDecimal amount) {
    return new Money(amount, "USD");
  }

  public static Money usd(double amount) {
    return new Money(BigDecimal.valueOf(amount), "USD");
  }

  public static Money cad(BigDecimal amount) {
    return new Money(amount, "CAD");
  }

  public static Money cad(double amount) {
    return new Money(BigDecimal.valueOf(amount), "CAD");
  }

  // ============================================================================
  // UUID Test Data
  // ============================================================================

  public static UUID randomUUID() {
    return UUID.randomUUID();
  }

  public static UUID customerId() {
    return randomUUID();
  }

  public static UUID accountId() {
    return randomUUID();
  }

  public static UUID paymentId() {
    return randomUUID();
  }
}
