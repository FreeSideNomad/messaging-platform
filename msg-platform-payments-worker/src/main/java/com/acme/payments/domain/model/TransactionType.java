package com.acme.payments.domain.model;

/**
 * Types of transactions
 */
public enum TransactionType {
    DEBIT(false),
    CREDIT(true),
    FEE(false),
    REVERSAL(true);

    private final boolean credit;

    TransactionType(boolean credit) {
        this.credit = credit;
    }

    public boolean isCredit() {
        return credit;
    }

    public boolean isDebit() {
        return !credit;
    }

    /**
     * Get the opposite transaction type for reversal
     */
    public TransactionType reverse() {
        return switch (this) {
            case DEBIT -> CREDIT;
            case CREDIT -> DEBIT;
            case FEE -> CREDIT; // Fee reversal is a credit
            case REVERSAL -> throw new IllegalStateException("Cannot reverse a reversal transaction");
        };
    }
}
