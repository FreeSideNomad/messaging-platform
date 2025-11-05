package com.acme.payments.domain.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for TransactionType enum */
class TransactionTypeTest {

  @Test
  @DisplayName("DEBIT should be a debit transaction")
  void testDebitIsDebit() {
    assertThat(TransactionType.DEBIT.isDebit()).isTrue();
    assertThat(TransactionType.DEBIT.isCredit()).isFalse();
  }

  @Test
  @DisplayName("CREDIT should be a credit transaction")
  void testCreditIsCredit() {
    assertThat(TransactionType.CREDIT.isCredit()).isTrue();
    assertThat(TransactionType.CREDIT.isDebit()).isFalse();
  }

  @Test
  @DisplayName("FEE should be a debit transaction")
  void testFeeIsDebit() {
    assertThat(TransactionType.FEE.isDebit()).isTrue();
    assertThat(TransactionType.FEE.isCredit()).isFalse();
  }

  @Test
  @DisplayName("REVERSAL should be a credit transaction")
  void testReversalIsCredit() {
    assertThat(TransactionType.REVERSAL.isCredit()).isTrue();
    assertThat(TransactionType.REVERSAL.isDebit()).isFalse();
  }

  @Test
  @DisplayName("reverse() - DEBIT should reverse to CREDIT")
  void testReverseDebit() {
    assertThat(TransactionType.DEBIT.reverse()).isEqualTo(TransactionType.CREDIT);
  }

  @Test
  @DisplayName("reverse() - CREDIT should reverse to DEBIT")
  void testReverseCredit() {
    assertThat(TransactionType.CREDIT.reverse()).isEqualTo(TransactionType.DEBIT);
  }

  @Test
  @DisplayName("reverse() - FEE should reverse to CREDIT")
  void testReverseFee() {
    assertThat(TransactionType.FEE.reverse()).isEqualTo(TransactionType.CREDIT);
  }

  @Test
  @DisplayName("reverse() - REVERSAL should throw exception")
  void testReverseReversal() {
    assertThatThrownBy(() -> TransactionType.REVERSAL.reverse())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot reverse a reversal transaction");
  }

  @Test
  @DisplayName("Should have all expected transaction types")
  void testAllValues() {
    TransactionType[] types = TransactionType.values();

    assertThat(types).hasSize(4);
    assertThat(types)
        .contains(
            TransactionType.DEBIT,
            TransactionType.CREDIT,
            TransactionType.FEE,
            TransactionType.REVERSAL);
  }
}
