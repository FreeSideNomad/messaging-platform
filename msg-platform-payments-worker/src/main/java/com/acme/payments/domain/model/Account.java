package com.acme.payments.domain.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for Account
 */
@Getter
public class Account {
    private final UUID accountId;
    private final UUID customerId;
    private final String accountNumber;
    private final String currencyCode;
    private final AccountType accountType;
    private final String transitNumber;
    private final boolean limitBased;
    private final List<Transaction> transactions;
    private final java.time.Instant createdAt;
    private Money availableBalance;

    public Account(
            UUID accountId,
            UUID customerId,
            String accountNumber,
            String currencyCode,
            AccountType accountType,
            String transitNumber,
            boolean limitBased,
            Money availableBalance) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }
        if (!availableBalance.currencyCode().equals(currencyCode)) {
            throw new IllegalArgumentException("Balance currency must match account currency");
        }

        this.accountId = accountId;
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.currencyCode = currencyCode;
        this.accountType = accountType;
        this.transitNumber = transitNumber;
        this.limitBased = limitBased;
        this.availableBalance = availableBalance;
        this.transactions = new ArrayList<>();
        this.createdAt = java.time.Instant.now();
    }

    /**
     * Create a new transaction on this account
     */
    public Transaction createTransaction(TransactionType type, Money amount, String description) {
        if (!amount.currencyCode().equals(currencyCode)) {
            throw new IllegalArgumentException("Transaction currency must match account currency");
        }

        Money newBalance = calculateNewBalance(type, amount);

        // Check for insufficient funds (only for non-limit-based accounts)
        if (!limitBased && type.isDebit()) {
            if (newBalance.isNegative()) {
                throw new InsufficientFundsException(accountId, availableBalance, amount);
            }
        }

        Transaction transaction =
                new Transaction(
                        UUID.randomUUID(),
                        accountId,
                        java.time.Instant.now(),
                        type,
                        amount,
                        description,
                        newBalance);

        transactions.add(transaction);
        availableBalance = newBalance;

        return transaction;
    }

    private Money calculateNewBalance(TransactionType type, Money amount) {
        if (type.isCredit()) {
            return availableBalance.add(amount);
        } else {
            return availableBalance.subtract(amount);
        }
    }

    // Override Lombok getter to return unmodifiable list
    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    /**
     * Restores a transaction from persistence. This should only be called by the repository when
     * loading from the database. Does NOT update the balance - assumes balance is already correct
     * from DB.
     *
     * @param transaction The transaction to restore
     */
    public void restoreTransaction(Transaction transaction) {
        if (!transaction.accountId().equals(this.accountId)) {
            throw new IllegalArgumentException("Transaction does not belong to this account");
        }
        this.transactions.add(transaction);
    }

    /**
     * Exception thrown when insufficient funds
     */
    @Getter
    public static class InsufficientFundsException extends RuntimeException {
        private final UUID accountId;
        private final Money availableBalance;
        private final Money requestedAmount;

        public InsufficientFundsException(
                UUID accountId, Money availableBalance, Money requestedAmount) {
            super(
                    String.format(
                            "Insufficient funds in account %s: available=%s, requested=%s",
                            accountId, availableBalance.amount(), requestedAmount.amount()));
            this.accountId = accountId;
            this.availableBalance = availableBalance;
            this.requestedAmount = requestedAmount;
        }
    }
}
