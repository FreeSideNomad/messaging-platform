package com.acme.payments.domain.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for Beneficiary value object */
class BeneficiaryTest {

  @Nested
  @DisplayName("Construction and Validation")
  class ConstructionTests {

    @Test
    @DisplayName("Should create Beneficiary with valid parameters")
    void testValidConstruction() {
      Beneficiary beneficiary = new Beneficiary("John Doe", "123456789", "001", "Test Bank");

      assertThat(beneficiary.name()).isEqualTo("John Doe");
      assertThat(beneficiary.accountNumber()).isEqualTo("123456789");
      assertThat(beneficiary.transitNumber()).isEqualTo("001");
      assertThat(beneficiary.bankName()).isEqualTo("Test Bank");
    }

    @Test
    @DisplayName("Should allow null bank name")
    void testNullBankName() {
      Beneficiary beneficiary = new Beneficiary("John Doe", "123456789", "001", null);

      assertThat(beneficiary.bankName()).isNull();
    }

    @Test
    @DisplayName("Should reject null name")
    void testNullName() {
      assertThatThrownBy(() -> new Beneficiary(null, "123456789", "001", "Test Bank"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Beneficiary name cannot be null or blank");
    }

    @Test
    @DisplayName("Should reject blank name")
    void testBlankName() {
      assertThatThrownBy(() -> new Beneficiary("   ", "123456789", "001", "Test Bank"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Beneficiary name cannot be null or blank");
    }

    @Test
    @DisplayName("Should reject empty name")
    void testEmptyName() {
      assertThatThrownBy(() -> new Beneficiary("", "123456789", "001", "Test Bank"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Beneficiary name cannot be null or blank");
    }

    @Test
    @DisplayName("Should reject null account number")
    void testNullAccountNumber() {
      assertThatThrownBy(() -> new Beneficiary("John Doe", null, "001", "Test Bank"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Account number cannot be null or blank");
    }

    @Test
    @DisplayName("Should reject blank account number")
    void testBlankAccountNumber() {
      assertThatThrownBy(() -> new Beneficiary("John Doe", "  ", "001", "Test Bank"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Account number cannot be null or blank");
    }

    @Test
    @DisplayName("Should reject empty account number")
    void testEmptyAccountNumber() {
      assertThatThrownBy(() -> new Beneficiary("John Doe", "", "001", "Test Bank"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Account number cannot be null or blank");
    }

    @Test
    @DisplayName("Should reject null transit number")
    void testNullTransitNumber() {
      assertThatThrownBy(() -> new Beneficiary("John Doe", "123456789", null, "Test Bank"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Transit number cannot be null or blank");
    }

    @Test
    @DisplayName("Should reject blank transit number")
    void testBlankTransitNumber() {
      assertThatThrownBy(() -> new Beneficiary("John Doe", "123456789", "   ", "Test Bank"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Transit number cannot be null or blank");
    }

    @Test
    @DisplayName("Should reject empty transit number")
    void testEmptyTransitNumber() {
      assertThatThrownBy(() -> new Beneficiary("John Doe", "123456789", "", "Test Bank"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Transit number cannot be null or blank");
    }
  }

  @Nested
  @DisplayName("Equality")
  class EqualityTests {

    @Test
    @DisplayName("Should be equal when all fields match")
    void testEquality() {
      Beneficiary ben1 = new Beneficiary("John Doe", "123456789", "001", "Test Bank");
      Beneficiary ben2 = new Beneficiary("John Doe", "123456789", "001", "Test Bank");

      assertThat(ben1).isEqualTo(ben2);
      assertThat(ben1.hashCode()).isEqualTo(ben2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when names differ")
    void testInequalityName() {
      Beneficiary ben1 = new Beneficiary("John Doe", "123456789", "001", "Test Bank");
      Beneficiary ben2 = new Beneficiary("Jane Smith", "123456789", "001", "Test Bank");

      assertThat(ben1).isNotEqualTo(ben2);
    }

    @Test
    @DisplayName("Should not be equal when account numbers differ")
    void testInequalityAccountNumber() {
      Beneficiary ben1 = new Beneficiary("John Doe", "123456789", "001", "Test Bank");
      Beneficiary ben2 = new Beneficiary("John Doe", "987654321", "001", "Test Bank");

      assertThat(ben1).isNotEqualTo(ben2);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle long names")
    void testLongName() {
      String longName = "A".repeat(100);
      Beneficiary beneficiary = new Beneficiary(longName, "123456789", "001", "Test Bank");

      assertThat(beneficiary.name()).isEqualTo(longName);
    }

    @Test
    @DisplayName("Should handle names with special characters")
    void testSpecialCharactersInName() {
      Beneficiary beneficiary =
          new Beneficiary("O'Brien-Smith Jr.", "123456789", "001", "Test Bank");

      assertThat(beneficiary.name()).isEqualTo("O'Brien-Smith Jr.");
    }

    @Test
    @DisplayName("Should handle various account number formats")
    void testVariousAccountNumbers() {
      assertThatNoException().isThrownBy(() -> new Beneficiary("John", "12345", "001", "Bank"));

      assertThatNoException()
          .isThrownBy(() -> new Beneficiary("John", "123456789012345", "001", "Bank"));

      assertThatNoException()
          .isThrownBy(() -> new Beneficiary("John", "ABC-123-XYZ", "001", "Bank"));
    }
  }
}
