package com.acme.payments.domain.service;

import com.acme.payments.application.command.CreateAccountCommand;
import com.acme.payments.application.command.CreateTransactionCommand;
import com.acme.payments.application.command.ReverseTransactionCommand;
import com.acme.payments.domain.model.Account;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.Transaction;
import com.acme.payments.domain.model.TransactionType;
import com.acme.payments.domain.repository.AccountRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Domain service for Account operations
 */
@Singleton
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    private final AccountRepository accountRepository;

    @Transactional
    public Account handleCreateAccount(CreateAccountCommand cmd) {
        log.info("Creating account for customer {} with currency {}",
            cmd.customerId(), cmd.currencyCode());

        Account account = new Account(
            UUID.randomUUID(),
            cmd.customerId(),
            generateAccountNumber(),
            cmd.currencyCode(),
            cmd.accountType(),
            cmd.transitNumber(),
            cmd.limitBased(),
            Money.zero(cmd.currencyCode())
        );

        accountRepository.save(account);
        log.info("Account created: {}", account.getAccountId());

        return account;
    }

    @Transactional
    public Transaction createTransaction(CreateTransactionCommand cmd) {
        log.info("Creating {} transaction on account {} for amount {}",
            cmd.transactionType(), cmd.accountId(), cmd.amount().amount());

        Account account = accountRepository.findById(cmd.accountId())
            .orElseThrow(() -> new AccountNotFoundException(cmd.accountId()));

        Transaction transaction = account.createTransaction(
            cmd.transactionType(),
            cmd.amount(),
            cmd.description()
        );

        accountRepository.save(account);
        log.info("Transaction created: {}", transaction.transactionId());

        return transaction;
    }

    @Transactional(readOnly = true)
    public Account getAccountById(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Transactional
    public Transaction reverseTransaction(UUID transactionId, String reason) {
        log.info("Reversing transaction {} reason: {}", transactionId, reason);

        // Find the original transaction by searching through accounts
        // In a real implementation, you'd have a transaction repository or index
        Transaction originalTransaction = findTransactionById(transactionId);

        Account account = accountRepository.findById(originalTransaction.accountId())
            .orElseThrow(() -> new AccountNotFoundException(originalTransaction.accountId()));

        // Create reversal transaction with opposite type and amount
        TransactionType reversalType = originalTransaction.transactionType().reverse();
        String reversalDescription = "Reversal: " + reason + " (Original: " + originalTransaction.description() + ")";

        Transaction reversalTransaction = account.createTransaction(
            reversalType,
            originalTransaction.amount(),
            reversalDescription
        );

        accountRepository.save(account);
        log.info("Transaction reversed: {} -> {}", transactionId, reversalTransaction.transactionId());

        return reversalTransaction;
    }

    /**
     * Command handler for ReverseTransactionCommand
     * This method will be auto-discovered by AutoCommandHandlerRegistry
     */
    public Transaction handleReverseTransaction(ReverseTransactionCommand cmd) {
        return reverseTransaction(cmd.transactionId(), cmd.reason());
    }

    private Transaction findTransactionById(UUID transactionId) {
        // Simple implementation - in production would use proper indexing
        // For now, we'll throw an exception if not found
        throw new UnsupportedOperationException(
            "Transaction lookup by ID not yet implemented. " +
            "Would need transaction index or search across accounts."
        );
    }

    private String generateAccountNumber() {
        // Simple implementation - in reality would use a proper account number generator
        return "ACC" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
    }

    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(UUID accountId) {
            super("Account not found: " + accountId);
        }
    }

    public static class TransactionNotFoundException extends RuntimeException {
        public TransactionNotFoundException(UUID transactionId) {
            super("Transaction not found: " + transactionId);
        }
    }
}
