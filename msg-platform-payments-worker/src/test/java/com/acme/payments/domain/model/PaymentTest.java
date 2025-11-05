package com.acme.payments.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive unit tests for Payment aggregate */
class PaymentTest {

  @Nested
  @DisplayName("Construction and Validation")
  class ConstructionTests {

    @Test
    @DisplayName("Should create Payment with valid parameters")
    void testValidConstruction() {
      UUID paymentId = UUID.randomUUID();
      UUID debitAccountId = UUID.randomUUID();
      Money debitAmount = Money.of(100, "USD");
      Money creditAmount = Money.of(100, "USD");
      LocalDate valueDate = LocalDate.now();
      Beneficiary beneficiary = new Beneficiary("John Doe", "ACC123", "BANK001", "123 Main St");

      Payment payment =
          new Payment(paymentId, debitAccountId, debitAmount, creditAmount, valueDate, beneficiary);

      assertThat(payment.getPaymentId()).isEqualTo(paymentId);
      assertThat(payment.getDebitAccountId()).isEqualTo(debitAccountId);
      assertThat(payment.getDebitAmount()).isEqualTo(debitAmount);
      assertThat(payment.getCreditAmount()).isEqualTo(creditAmount);
      assertThat(payment.getValueDate()).isEqualTo(valueDate);
      assertThat(payment.getBeneficiary()).isEqualTo(beneficiary);
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
      assertThat(payment.getFeeAmount()).isEqualTo(Money.zero("USD"));
      assertThat(payment.getCreatedAt()).isNotNull();
      assertThat(payment.getDebitTransactionId()).isNull();
      assertThat(payment.getFxContractId()).isNull();
    }

    @Test
    @DisplayName("Should reject null payment ID")
    void testNullPaymentId() {
      assertThatThrownBy(
              () ->
                  new Payment(
                      null,
                      UUID.randomUUID(),
                      Money.of(100, "USD"),
                      Money.of(100, "USD"),
                      LocalDate.now(),
                      new Beneficiary("John Doe", "ACC123", "BANK001", "123 Main St")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Payment ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null debit account ID")
    void testNullDebitAccountId() {
      assertThatThrownBy(
              () ->
                  new Payment(
                      UUID.randomUUID(),
                      null,
                      Money.of(100, "USD"),
                      Money.of(100, "USD"),
                      LocalDate.now(),
                      new Beneficiary("John Doe", "ACC123", "BANK001", "123 Main St")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Debit account ID cannot be null");
    }

    @Test
    @DisplayName("Should reject null debit amount")
    void testNullDebitAmount() {
      assertThatThrownBy(
              () ->
                  new Payment(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      null,
                      Money.of(100, "USD"),
                      LocalDate.now(),
                      new Beneficiary("John Doe", "ACC123", "BANK001", "123 Main St")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Debit amount cannot be null");
    }

    @Test
    @DisplayName("Should reject null credit amount")
    void testNullCreditAmount() {
      assertThatThrownBy(
              () ->
                  new Payment(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      Money.of(100, "USD"),
                      null,
                      LocalDate.now(),
                      new Beneficiary("John Doe", "ACC123", "BANK001", "123 Main St")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Credit amount cannot be null");
    }

    @Test
    @DisplayName("Should reject null value date")
    void testNullValueDate() {
      assertThatThrownBy(
              () ->
                  new Payment(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      Money.of(100, "USD"),
                      Money.of(100, "USD"),
                      null,
                      new Beneficiary("John Doe", "ACC123", "BANK001", "123 Main St")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Value date cannot be null");
    }

    @Test
    @DisplayName("Should reject null beneficiary")
    void testNullBeneficiary() {
      assertThatThrownBy(
              () ->
                  new Payment(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      Money.of(100, "USD"),
                      Money.of(100, "USD"),
                      LocalDate.now(),
                      null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Beneficiary cannot be null");
    }
  }

  @Nested
  @DisplayName("Status Transitions - Happy Path")
  class StatusTransitionTests {

    @Test
    @DisplayName("Should transition from PENDING to PROCESSING")
    void testMarkAsProcessing() {
      Payment payment = createValidPayment();

      payment.markAsProcessing();

      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    @DisplayName("Should transition from PROCESSING to COMPLETED")
    void testMarkAsCompleted() {
      Payment payment = createValidPayment();

      payment.markAsProcessing();
      payment.markAsCompleted();

      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should transition full lifecycle: PENDING -> PROCESSING -> COMPLETED")
    void testFullLifecycle() {
      Payment payment = createValidPayment();

      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

      payment.markAsProcessing();
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

      payment.markAsCompleted();
      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should reverse COMPLETED payment")
    void testReverse() {
      Payment payment = createValidPayment();

      payment.markAsProcessing();
      payment.markAsCompleted();
      payment.reverse("Customer request");

      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REVERSED);
    }
  }

  @Nested
  @DisplayName("Status Transitions - Invalid Transitions")
  class InvalidTransitionTests {

    @Test
    @DisplayName("Should reject marking PROCESSING as PROCESSING")
    void testInvalidProcessingToProcessing() {
      Payment payment = createValidPayment();
      payment.markAsProcessing();

      assertThatThrownBy(() -> payment.markAsProcessing())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Can only mark PENDING payments as PROCESSING");
    }

    @Test
    @DisplayName("Should reject marking COMPLETED as PROCESSING")
    void testInvalidCompletedToProcessing() {
      Payment payment = createValidPayment();
      payment.markAsProcessing();
      payment.markAsCompleted();

      assertThatThrownBy(() -> payment.markAsProcessing())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Can only mark PENDING payments as PROCESSING");
    }

    @Test
    @DisplayName("Should reject marking PENDING as COMPLETED")
    void testInvalidPendingToCompleted() {
      Payment payment = createValidPayment();

      assertThatThrownBy(() -> payment.markAsCompleted())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Can only mark PROCESSING payments as COMPLETED");
    }

    @Test
    @DisplayName("Should reject marking COMPLETED as COMPLETED")
    void testInvalidCompletedToCompleted() {
      Payment payment = createValidPayment();
      payment.markAsProcessing();
      payment.markAsCompleted();

      assertThatThrownBy(() -> payment.markAsCompleted())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Can only mark PROCESSING payments as COMPLETED");
    }

    @Test
    @DisplayName("Should reject reversing PENDING payment")
    void testInvalidReversePending() {
      Payment payment = createValidPayment();

      assertThatThrownBy(() -> payment.reverse("Test"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Can only reverse COMPLETED payments");
    }

    @Test
    @DisplayName("Should reject reversing PROCESSING payment")
    void testInvalidReverseProcessing() {
      Payment payment = createValidPayment();
      payment.markAsProcessing();

      assertThatThrownBy(() -> payment.reverse("Test"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Can only reverse COMPLETED payments");
    }

    @Test
    @DisplayName("Should reject marking COMPLETED payment as FAILED")
    void testInvalidCompletedToFailed() {
      Payment payment = createValidPayment();
      payment.markAsProcessing();
      payment.markAsCompleted();

      assertThatThrownBy(() -> payment.markAsFailed("Test"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot mark COMPLETED payments as FAILED");
    }
  }

  @Nested
  @DisplayName("Failure Scenarios")
  class FailureTests {

    @Test
    @DisplayName("Should mark PENDING payment as FAILED")
    void testFailPending() {
      Payment payment = createValidPayment();

      payment.markAsFailed("Insufficient funds");

      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("Should mark PROCESSING payment as FAILED")
    void testFailProcessing() {
      Payment payment = createValidPayment();
      payment.markAsProcessing();

      payment.markAsFailed("FX contract failed");

      assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
  }

  @Nested
  @DisplayName("Foreign Exchange Requirements")
  class FxRequirementTests {

    @Test
    @DisplayName("Should not require FX when currencies match")
    void testNoFxRequired() {
      Payment payment =
          new Payment(
              UUID.randomUUID(),
              UUID.randomUUID(),
              Money.of(100, "USD"),
              Money.of(100, "USD"),
              LocalDate.now(),
              new Beneficiary("John Doe", "ACC123", "BANK001", "123 Main St"));

      assertThat(payment.requiresFx()).isFalse();
    }

    @Test
    @DisplayName("Should require FX when currencies differ")
    void testFxRequired() {
      Payment payment =
          new Payment(
              UUID.randomUUID(),
              UUID.randomUUID(),
              Money.of(100, "USD"),
              Money.of(85, "EUR"),
              LocalDate.now(),
              new Beneficiary("John Doe", "ACC123", "BANK001", "123 Main St"));

      assertThat(payment.requiresFx()).isTrue();
    }

    @Test
    @DisplayName("Should record FX contract ID")
    void testRecordFxContract() {
      Payment payment = createValidPayment();
      UUID fxContractId = UUID.randomUUID();

      payment.recordFxContract(fxContractId);

      assertThat(payment.getFxContractId()).isEqualTo(fxContractId);
    }

    @Test
    @DisplayName("Should set FX contract ID via setter")
    void testSetFxContractId() {
      Payment payment = createValidPayment();
      UUID fxContractId = UUID.randomUUID();

      payment.setFxContractId(fxContractId);

      assertThat(payment.getFxContractId()).isEqualTo(fxContractId);
    }
  }

  @Nested
  @DisplayName("Transaction Recording")
  class TransactionRecordingTests {

    @Test
    @DisplayName("Should record debit transaction ID")
    void testRecordDebitTransaction() {
      Payment payment = createValidPayment();
      UUID transactionId = UUID.randomUUID();

      payment.recordDebitTransaction(transactionId);

      assertThat(payment.getDebitTransactionId()).isEqualTo(transactionId);
    }

    @Test
    @DisplayName("Should set debit transaction ID via setter")
    void testSetDebitTransactionId() {
      Payment payment = createValidPayment();
      UUID transactionId = UUID.randomUUID();

      payment.setDebitTransactionId(transactionId);

      assertThat(payment.getDebitTransactionId()).isEqualTo(transactionId);
    }

    @Test
    @DisplayName("Should record both debit transaction and FX contract")
    void testRecordBoth() {
      Payment payment = createCrossCurrencyPayment();
      UUID transactionId = UUID.randomUUID();
      UUID fxContractId = UUID.randomUUID();

      payment.recordDebitTransaction(transactionId);
      payment.recordFxContract(fxContractId);

      assertThat(payment.getDebitTransactionId()).isEqualTo(transactionId);
      assertThat(payment.getFxContractId()).isEqualTo(fxContractId);
    }
  }

  @Nested
  @DisplayName("Fee Management")
  class FeeTests {

    @Test
    @DisplayName("Should initialize with zero fee")
    void testDefaultFee() {
      Payment payment = createValidPayment();

      assertThat(payment.getFeeAmount()).isEqualTo(Money.zero("USD"));
    }

    @Test
    @DisplayName("Should set fee amount")
    void testSetFee() {
      Payment payment = createValidPayment();
      Money fee = Money.of(2.50, "USD");

      payment.setFeeAmount(fee);

      assertThat(payment.getFeeAmount()).isEqualTo(fee);
    }

    @Test
    @DisplayName("Should reject fee with wrong currency")
    void testInvalidFeeCurrency() {
      Payment payment = createValidPayment(); // USD payment

      assertThatThrownBy(() -> payment.setFeeAmount(Money.of(2.50, "EUR")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Fee currency must match debit amount currency");
    }

    @Test
    @DisplayName("Should accept zero fee")
    void testZeroFee() {
      Payment payment = createValidPayment();

      payment.setFeeAmount(Money.zero("USD"));

      assertThat(payment.getFeeAmount()).isEqualTo(Money.zero("USD"));
    }
  }

  @Nested
  @DisplayName("Cross-Currency Payments")
  class CrossCurrencyTests {

    @Test
    @DisplayName("Should create cross-currency payment (USD to EUR)")
    void testUsdToEur() {
      Payment payment =
          new Payment(
              UUID.randomUUID(),
              UUID.randomUUID(),
              Money.of(100, "USD"),
              Money.of(85, "EUR"),
              LocalDate.now(),
              new Beneficiary("John Doe", "ACC123", "BANK001", "123 Main St"));

      assertThat(payment.requiresFx()).isTrue();
      assertThat(payment.getDebitAmount().currencyCode()).isEqualTo("USD");
      assertThat(payment.getCreditAmount().currencyCode()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Should create cross-currency payment (GBP to USD)")
    void testGbpToUsd() {
      Payment payment =
          new Payment(
              UUID.randomUUID(),
              UUID.randomUUID(),
              Money.of(100, "GBP"),
              Money.of(125, "USD"),
              LocalDate.now(),
              new Beneficiary("Jane Smith", "ACC456", "BANK002", "456 Oak Ave"));

      assertThat(payment.requiresFx()).isTrue();
      assertThat(payment.getDebitAmount().currencyCode()).isEqualTo("GBP");
      assertThat(payment.getCreditAmount().currencyCode()).isEqualTo("USD");
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle large payment amounts")
    void testLargeAmounts() {
      Payment payment =
          new Payment(
              UUID.randomUUID(),
              UUID.randomUUID(),
              Money.of(999999, "USD"),
              Money.of(999999, "USD"),
              LocalDate.now(),
              new Beneficiary("Corp Inc", "ACC789", "BANK003", "789 Business Blvd"));

      assertThat(payment.getDebitAmount()).isEqualTo(Money.of(999999, "USD"));
      assertThat(payment.getCreditAmount()).isEqualTo(Money.of(999999, "USD"));
    }

    @Test
    @DisplayName("Should handle various value dates")
    void testDifferentValueDates() {
      LocalDate today = LocalDate.now();
      LocalDate tomorrow = today.plusDays(1);
      LocalDate nextWeek = today.plusWeeks(1);

      assertThatNoException()
          .isThrownBy(
              () ->
                  new Payment(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      Money.of(100, "USD"),
                      Money.of(100, "USD"),
                      today,
                      new Beneficiary("Test", "ACC", "BANK", "Address")));

      assertThatNoException()
          .isThrownBy(
              () ->
                  new Payment(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      Money.of(100, "USD"),
                      Money.of(100, "USD"),
                      tomorrow,
                      new Beneficiary("Test", "ACC", "BANK", "Address")));

      assertThatNoException()
          .isThrownBy(
              () ->
                  new Payment(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      Money.of(100, "USD"),
                      Money.of(100, "USD"),
                      nextWeek,
                      new Beneficiary("Test", "ACC", "BANK", "Address")));
    }
  }

  // Helper methods
  private Payment createValidPayment() {
    return new Payment(
        UUID.randomUUID(),
        UUID.randomUUID(),
        Money.of(100, "USD"),
        Money.of(100, "USD"),
        LocalDate.now(),
        new Beneficiary("John Doe", "ACC123", "BANK001", "123 Main St"));
  }

  private Payment createCrossCurrencyPayment() {
    return new Payment(
        UUID.randomUUID(),
        UUID.randomUUID(),
        Money.of(100, "USD"),
        Money.of(85, "EUR"),
        LocalDate.now(),
        new Beneficiary("John Doe", "ACC123", "BANK001", "123 Main St"));
  }
}
