package com.acme.payments.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an account transaction
 */
public record Transaction(
    UUID transactionId,
    UUID accountId,
    Instant transactionDate,
    TransactionType transactionType,
    Money amount,
    String description,
    Money balance
) {
    public Transaction {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (transactionDate == null) {
            throw new IllegalArgumentException("Transaction date cannot be null");
        }
        if (transactionType == null) {
            throw new IllegalArgumentException("Transaction type cannot be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (balance == null) {
            throw new IllegalArgumentException("Balance cannot be null");
        }
    }
}
