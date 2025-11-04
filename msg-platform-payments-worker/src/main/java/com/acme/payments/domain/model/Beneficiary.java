package com.acme.payments.domain.model;

/**
 * Value object representing payment beneficiary details
 */
public record Beneficiary(
    String name,
    String accountNumber,
    String transitNumber,
    String bankName
) {
    public Beneficiary {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Beneficiary name cannot be null or blank");
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("Account number cannot be null or blank");
        }
        if (transitNumber == null || transitNumber.isBlank()) {
            throw new IllegalArgumentException("Transit number cannot be null or blank");
        }
    }
}
