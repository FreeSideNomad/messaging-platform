package com.acme.payments.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FxContract aggregate
 */
class FxContractTest {

    // Helper method
    private FxContract createValidContract() {
        return new FxContract(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Money.of(100, "USD"),
                Money.of(85, "EUR"),
                new BigDecimal("0.85"),
                LocalDate.now());
    }

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {

        @Test
        @DisplayName("Should create FxContract with valid parameters")
        void testValidConstruction() {
            UUID fxContractId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            UUID debitAccountId = UUID.randomUUID();
            Money debitAmount = Money.of(100, "USD");
            Money creditAmount = Money.of(85, "EUR");
            BigDecimal rate = new BigDecimal("0.85");
            LocalDate valueDate = LocalDate.now();

            FxContract contract =
                    new FxContract(
                            fxContractId, customerId, debitAccountId, debitAmount, creditAmount, rate, valueDate);

            assertThat(contract.getFxContractId()).isEqualTo(fxContractId);
            assertThat(contract.getCustomerId()).isEqualTo(customerId);
            assertThat(contract.getDebitAccountId()).isEqualTo(debitAccountId);
            assertThat(contract.getDebitAmount()).isEqualTo(debitAmount);
            assertThat(contract.getCreditAmount()).isEqualTo(creditAmount);
            assertThat(contract.getRate()).isEqualTo(rate);
            assertThat(contract.getValueDate()).isEqualTo(valueDate);
            assertThat(contract.getStatus()).isEqualTo(FxStatus.BOOKED);
        }

        @Test
        @DisplayName("Should reject null FX contract ID")
        void testNullFxContractId() {
            assertThatThrownBy(
                    () ->
                            new FxContract(
                                    null,
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    Money.of(100, "USD"),
                                    Money.of(85, "EUR"),
                                    new BigDecimal("0.85"),
                                    LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("FX contract ID cannot be null");
        }

        @Test
        @DisplayName("Should reject null customer ID")
        void testNullCustomerId() {
            assertThatThrownBy(
                    () ->
                            new FxContract(
                                    UUID.randomUUID(),
                                    null,
                                    UUID.randomUUID(),
                                    Money.of(100, "USD"),
                                    Money.of(85, "EUR"),
                                    new BigDecimal("0.85"),
                                    LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Customer ID cannot be null");
        }

        @Test
        @DisplayName("Should reject null debit account ID")
        void testNullDebitAccountId() {
            assertThatThrownBy(
                    () ->
                            new FxContract(
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    null,
                                    Money.of(100, "USD"),
                                    Money.of(85, "EUR"),
                                    new BigDecimal("0.85"),
                                    LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Debit account ID cannot be null");
        }

        @Test
        @DisplayName("Should reject null debit amount")
        void testNullDebitAmount() {
            assertThatThrownBy(
                    () ->
                            new FxContract(
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    null,
                                    Money.of(85, "EUR"),
                                    new BigDecimal("0.85"),
                                    LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Debit amount cannot be null");
        }

        @Test
        @DisplayName("Should reject null credit amount")
        void testNullCreditAmount() {
            assertThatThrownBy(
                    () ->
                            new FxContract(
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    Money.of(100, "USD"),
                                    null,
                                    new BigDecimal("0.85"),
                                    LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Credit amount cannot be null");
        }

        @Test
        @DisplayName("Should reject null rate")
        void testNullRate() {
            assertThatThrownBy(
                    () ->
                            new FxContract(
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    Money.of(100, "USD"),
                                    Money.of(85, "EUR"),
                                    null,
                                    LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rate must be positive");
        }

        @Test
        @DisplayName("Should reject zero rate")
        void testZeroRate() {
            assertThatThrownBy(
                    () ->
                            new FxContract(
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    Money.of(100, "USD"),
                                    Money.of(85, "EUR"),
                                    BigDecimal.ZERO,
                                    LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rate must be positive");
        }

        @Test
        @DisplayName("Should reject negative rate")
        void testNegativeRate() {
            assertThatThrownBy(
                    () ->
                            new FxContract(
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    Money.of(100, "USD"),
                                    Money.of(85, "EUR"),
                                    new BigDecimal("-0.85"),
                                    LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rate must be positive");
        }

        @Test
        @DisplayName("Should reject null value date")
        void testNullValueDate() {
            assertThatThrownBy(
                    () ->
                            new FxContract(
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    Money.of(100, "USD"),
                                    Money.of(85, "EUR"),
                                    new BigDecimal("0.85"),
                                    null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Value date cannot be null");
        }

        @Test
        @DisplayName("Should reject same currency for debit and credit")
        void testSameCurrency() {
            assertThatThrownBy(
                    () ->
                            new FxContract(
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    Money.of(100, "USD"),
                                    Money.of(100, "USD"), // Same currency
                                    BigDecimal.ONE,
                                    LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Debit and credit currencies must be different for FX");
        }
    }

    @Nested
    @DisplayName("Unwind Operations")
    class UnwindTests {

        @Test
        @DisplayName("Should unwind booked FX contract")
        void testUnwind() {
            FxContract contract = createValidContract();

            contract.unwind("Customer request");

            assertThat(contract.getStatus()).isEqualTo(FxStatus.UNWOUND);
        }

        @Test
        @DisplayName("Should throw exception when unwinding already unwound contract")
        void testUnwindAlreadyUnwound() {
            FxContract contract = createValidContract();
            contract.unwind("First unwind");

            assertThatThrownBy(() -> contract.unwind("Second unwind"))
                    .isInstanceOf(FxContract.FxContractAlreadyUnwoundException.class);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle various currency pairs")
        void testVariousCurrencyPairs() {
            assertThatNoException()
                    .isThrownBy(
                            () ->
                                    new FxContract(
                                            UUID.randomUUID(),
                                            UUID.randomUUID(),
                                            UUID.randomUUID(),
                                            Money.of(100, "USD"),
                                            Money.of(85, "EUR"),
                                            new BigDecimal("0.85"),
                                            LocalDate.now()));

            assertThatNoException()
                    .isThrownBy(
                            () ->
                                    new FxContract(
                                            UUID.randomUUID(),
                                            UUID.randomUUID(),
                                            UUID.randomUUID(),
                                            Money.of(100, "GBP"),
                                            Money.of(16000, "JPY"),
                                            new BigDecimal("160"),
                                            LocalDate.now()));
        }

        @Test
        @DisplayName("Should handle large amounts")
        void testLargeAmounts() {
            FxContract contract =
                    new FxContract(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            Money.of(1000000, "USD"),
                            Money.of(850000, "EUR"),
                            new BigDecimal("0.85"),
                            LocalDate.now());

            assertThat(contract.getDebitAmount()).isEqualTo(Money.of(1000000, "USD"));
            assertThat(contract.getCreditAmount()).isEqualTo(Money.of(850000, "EUR"));
        }

        @Test
        @DisplayName("Should handle high precision rates")
        void testHighPrecisionRate() {
            BigDecimal preciseRate = new BigDecimal("0.853712");

            FxContract contract =
                    new FxContract(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            Money.of(100, "USD"),
                            Money.of(85.37, "EUR"),
                            preciseRate,
                            LocalDate.now());

            assertThat(contract.getRate()).isEqualByComparingTo(preciseRate);
        }

        @Test
        @DisplayName("Should handle future value dates")
        void testFutureValueDate() {
            LocalDate futureDate = LocalDate.now().plusMonths(3);

            FxContract contract =
                    new FxContract(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            Money.of(100, "USD"),
                            Money.of(85, "EUR"),
                            new BigDecimal("0.85"),
                            futureDate);

            assertThat(contract.getValueDate()).isEqualTo(futureDate);
        }
    }
}
