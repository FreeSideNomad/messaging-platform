package com.acme.payments.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for Account aggregate
 */
class AccountTest {

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {

        @Test
        @DisplayName("Should create Account with valid parameters")
        void testValidConstruction() {
            UUID accountId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            Money initialBalance = Money.zero("USD");

            Account account = new Account(
                accountId,
                customerId,
                "ACC123456789",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                initialBalance
            );

            assertThat(account.getAccountId()).isEqualTo(accountId);
            assertThat(account.getCustomerId()).isEqualTo(customerId);
            assertThat(account.getAccountNumber()).isEqualTo("ACC123456789");
            assertThat(account.getCurrencyCode()).isEqualTo("USD");
            assertThat(account.getAccountType()).isEqualTo(AccountType.CHECKING);
            assertThat(account.getTransitNumber()).isEqualTo("001");
            assertThat(account.isLimitBased()).isFalse();
            assertThat(account.getAvailableBalance()).isEqualTo(initialBalance);
            assertThat(account.getTransactions()).isEmpty();
            assertThat(account.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should create limit-based account")
        void testLimitBasedAccount() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.SAVINGS,
                "002",
                true,
                Money.zero("USD")
            );

            assertThat(account.isLimitBased()).isTrue();
        }

        @Test
        @DisplayName("Should reject null account ID")
        void testNullAccountId() {
            assertThatThrownBy(() -> new Account(
                null,
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.zero("USD")
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account ID cannot be null");
        }

        @Test
        @DisplayName("Should reject null customer ID")
        void testNullCustomerId() {
            assertThatThrownBy(() -> new Account(
                UUID.randomUUID(),
                null,
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.zero("USD")
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer ID cannot be null");
        }

        @Test
        @DisplayName("Should reject balance with mismatched currency")
        void testMismatchedBalanceCurrency() {
            assertThatThrownBy(() -> new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.zero("EUR")
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Balance currency must match account currency");
        }

        @Test
        @DisplayName("Should create account with initial balance")
        void testWithInitialBalance() {
            Money initialBalance = Money.of(1000, "USD");

            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                initialBalance
            );

            assertThat(account.getAvailableBalance()).isEqualTo(initialBalance);
        }

        @Test
        @DisplayName("Should handle different account types")
        void testDifferentAccountTypes() {
            assertThatNoException().isThrownBy(() ->
                new Account(UUID.randomUUID(), UUID.randomUUID(), "ACC1", "USD",
                    AccountType.CHECKING, "001", false, Money.zero("USD")));

            assertThatNoException().isThrownBy(() ->
                new Account(UUID.randomUUID(), UUID.randomUUID(), "ACC2", "USD",
                    AccountType.SAVINGS, "001", false, Money.zero("USD")));
        }
    }

    @Nested
    @DisplayName("Credit Transactions")
    class CreditTransactionTests {

        @Test
        @DisplayName("Should create credit transaction and increase balance")
        void testCreditTransaction() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.zero("USD")
            );

            Transaction transaction = account.createTransaction(
                TransactionType.CREDIT,
                Money.of(500, "USD"),
                "Initial deposit"
            );

            assertThat(transaction).isNotNull();
            assertThat(transaction.transactionType()).isEqualTo(TransactionType.CREDIT);
            assertThat(transaction.amount()).isEqualTo(Money.of(500, "USD"));
            assertThat(transaction.description()).isEqualTo("Initial deposit");
            assertThat(transaction.balance()).isEqualTo(Money.of(500, "USD"));
            assertThat(account.getAvailableBalance()).isEqualTo(Money.of(500, "USD"));
            assertThat(account.getTransactions()).hasSize(1);
        }

        @Test
        @DisplayName("Should create multiple credit transactions cumulatively")
        void testMultipleCreditTransactions() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.zero("USD")
            );

            account.createTransaction(TransactionType.CREDIT, Money.of(100, "USD"), "Deposit 1");
            account.createTransaction(TransactionType.CREDIT, Money.of(200, "USD"), "Deposit 2");
            account.createTransaction(TransactionType.CREDIT, Money.of(300, "USD"), "Deposit 3");

            assertThat(account.getAvailableBalance()).isEqualTo(Money.of(600, "USD"));
            assertThat(account.getTransactions()).hasSize(3);
        }

        @Test
        @DisplayName("Should reject credit transaction with wrong currency")
        void testCreditWrongCurrency() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.zero("USD")
            );

            assertThatThrownBy(() ->
                account.createTransaction(TransactionType.CREDIT, Money.of(100, "EUR"), "Deposit")
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction currency must match account currency");
        }
    }

    @Nested
    @DisplayName("Debit Transactions")
    class DebitTransactionTests {

        @Test
        @DisplayName("Should create debit transaction and decrease balance")
        void testDebitTransaction() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.of(1000, "USD")
            );

            Transaction transaction = account.createTransaction(
                TransactionType.DEBIT,
                Money.of(300, "USD"),
                "Withdrawal"
            );

            assertThat(transaction).isNotNull();
            assertThat(transaction.transactionType()).isEqualTo(TransactionType.DEBIT);
            assertThat(transaction.amount()).isEqualTo(Money.of(300, "USD"));
            assertThat(transaction.balance()).isEqualTo(Money.of(700, "USD"));
            assertThat(account.getAvailableBalance()).isEqualTo(Money.of(700, "USD"));
        }

        @Test
        @DisplayName("Should create multiple debit transactions cumulatively")
        void testMultipleDebitTransactions() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.of(1000, "USD")
            );

            account.createTransaction(TransactionType.DEBIT, Money.of(100, "USD"), "Withdrawal 1");
            account.createTransaction(TransactionType.DEBIT, Money.of(200, "USD"), "Withdrawal 2");
            account.createTransaction(TransactionType.DEBIT, Money.of(300, "USD"), "Withdrawal 3");

            assertThat(account.getAvailableBalance()).isEqualTo(Money.of(400, "USD"));
            assertThat(account.getTransactions()).hasSize(3);
        }

        @Test
        @DisplayName("Should reject debit exceeding balance (non-limit-based)")
        void testDebitInsufficientFunds() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.of(500, "USD")
            );

            assertThatThrownBy(() ->
                account.createTransaction(TransactionType.DEBIT, Money.of(600, "USD"), "Withdrawal")
            )
                .isInstanceOf(Account.InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");
        }

        @Test
        @DisplayName("Should allow debit exceeding balance (limit-based account)")
        void testDebitLimitBasedAccount() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                true,  // limit-based
                Money.of(100, "USD")
            );

            // Should allow debit that would make balance negative
            account.createTransaction(TransactionType.DEBIT, Money.of(200, "USD"), "Payment");

            assertThat(account.getAvailableBalance()).isEqualTo(Money.of(-100, "USD"));
        }

        @Test
        @DisplayName("Should reject debit transaction with wrong currency")
        void testDebitWrongCurrency() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.of(1000, "USD")
            );

            assertThatThrownBy(() ->
                account.createTransaction(TransactionType.DEBIT, Money.of(100, "EUR"), "Withdrawal")
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction currency must match account currency");
        }

        @Test
        @DisplayName("InsufficientFundsException should contain all relevant information")
        void testInsufficientFundsException() {
            UUID accountId = UUID.randomUUID();
            Account account = new Account(
                accountId,
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.of(100, "USD")
            );

            try {
                account.createTransaction(TransactionType.DEBIT, Money.of(200, "USD"), "Withdrawal");
                fail("Should have thrown InsufficientFundsException");
            } catch (Account.InsufficientFundsException e) {
                assertThat(e.getAccountId()).isEqualTo(accountId);
                assertThat(e.getAvailableBalance()).isEqualTo(Money.of(100, "USD"));
                assertThat(e.getRequestedAmount()).isEqualTo(Money.of(200, "USD"));
                assertThat(e.getMessage()).contains("Insufficient funds");
            }
        }
    }

    @Nested
    @DisplayName("Mixed Transactions")
    class MixedTransactionTests {

        @Test
        @DisplayName("Should handle mixed credit and debit transactions")
        void testMixedTransactions() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.of(500, "USD")
            );

            account.createTransaction(TransactionType.CREDIT, Money.of(300, "USD"), "Deposit");
            assertThat(account.getAvailableBalance()).isEqualTo(Money.of(800, "USD"));

            account.createTransaction(TransactionType.DEBIT, Money.of(200, "USD"), "Withdrawal");
            assertThat(account.getAvailableBalance()).isEqualTo(Money.of(600, "USD"));

            account.createTransaction(TransactionType.CREDIT, Money.of(400, "USD"), "Deposit");
            assertThat(account.getAvailableBalance()).isEqualTo(Money.of(1000, "USD"));

            assertThat(account.getTransactions()).hasSize(3);
        }

        @Test
        @DisplayName("Should track balance correctly through transaction sequence")
        void testBalanceTracking() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.zero("USD")
            );

            Transaction t1 = account.createTransaction(TransactionType.CREDIT, Money.of(1000, "USD"), "Initial");
            assertThat(t1.balance()).isEqualTo(Money.of(1000, "USD"));

            Transaction t2 = account.createTransaction(TransactionType.DEBIT, Money.of(300, "USD"), "Payment");
            assertThat(t2.balance()).isEqualTo(Money.of(700, "USD"));

            Transaction t3 = account.createTransaction(TransactionType.CREDIT, Money.of(500, "USD"), "Deposit");
            assertThat(t3.balance()).isEqualTo(Money.of(1200, "USD"));

            assertThat(account.getAvailableBalance()).isEqualTo(Money.of(1200, "USD"));
        }
    }

    @Nested
    @DisplayName("Transaction List Management")
    class TransactionListTests {

        @Test
        @DisplayName("Should return empty transaction list initially")
        void testEmptyTransactionList() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.zero("USD")
            );

            assertThat(account.getTransactions()).isEmpty();
        }

        @Test
        @DisplayName("Should return unmodifiable transaction list")
        void testUnmodifiableTransactionList() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.zero("USD")
            );

            account.createTransaction(TransactionType.CREDIT, Money.of(100, "USD"), "Deposit");

            assertThatThrownBy(() ->
                account.getTransactions().clear()
            ).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Should maintain transaction order")
        void testTransactionOrder() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.zero("USD")
            );

            Transaction t1 = account.createTransaction(TransactionType.CREDIT, Money.of(100, "USD"), "First");
            Transaction t2 = account.createTransaction(TransactionType.CREDIT, Money.of(200, "USD"), "Second");
            Transaction t3 = account.createTransaction(TransactionType.DEBIT, Money.of(50, "USD"), "Third");

            assertThat(account.getTransactions())
                .containsExactly(t1, t2, t3);
        }

        @Test
        @DisplayName("Should generate unique transaction IDs")
        void testUniqueTransactionIds() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.zero("USD")
            );

            Transaction t1 = account.createTransaction(TransactionType.CREDIT, Money.of(100, "USD"), "Deposit 1");
            Transaction t2 = account.createTransaction(TransactionType.CREDIT, Money.of(200, "USD"), "Deposit 2");

            assertThat(t1.transactionId()).isNotEqualTo(t2.transactionId());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle zero amount transactions")
        void testZeroAmountTransaction() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.of(100, "USD")
            );

            Transaction transaction = account.createTransaction(
                TransactionType.CREDIT,
                Money.zero("USD"),
                "Zero deposit"
            );

            assertThat(transaction.amount()).isEqualTo(Money.zero("USD"));
            assertThat(account.getAvailableBalance()).isEqualTo(Money.of(100, "USD"));
        }

        @Test
        @DisplayName("Should handle large balances")
        void testLargeBalances() {
            Account account = new Account(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC123",
                "USD",
                AccountType.CHECKING,
                "001",
                false,
                Money.of(999999, "USD")
            );

            account.createTransaction(TransactionType.CREDIT, Money.of(1, "USD"), "Deposit");

            assertThat(account.getAvailableBalance()).isEqualTo(Money.of(1000000, "USD"));
        }

        @Test
        @DisplayName("Should handle various currencies")
        void testVariousCurrencies() {
            assertThatNoException().isThrownBy(() -> {
                Account usdAccount = new Account(UUID.randomUUID(), UUID.randomUUID(),
                    "ACC1", "USD", AccountType.CHECKING, "001", false, Money.zero("USD"));
                usdAccount.createTransaction(TransactionType.CREDIT, Money.of(100, "USD"), "Deposit");
            });

            assertThatNoException().isThrownBy(() -> {
                Account eurAccount = new Account(UUID.randomUUID(), UUID.randomUUID(),
                    "ACC2", "EUR", AccountType.CHECKING, "001", false, Money.zero("EUR"));
                eurAccount.createTransaction(TransactionType.CREDIT, Money.of(100, "EUR"), "Deposit");
            });

            assertThatNoException().isThrownBy(() -> {
                Account gbpAccount = new Account(UUID.randomUUID(), UUID.randomUUID(),
                    "ACC3", "GBP", AccountType.CHECKING, "001", false, Money.zero("GBP"));
                gbpAccount.createTransaction(TransactionType.CREDIT, Money.of(100, "GBP"), "Deposit");
            });
        }
    }
}
