package com.acme.payments.domain.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.acme.payments.application.command.CompleteAccountCreationCommand;
import com.acme.payments.application.command.CreateAccountCommand;
import com.acme.payments.application.command.CreateTransactionCommand;
import com.acme.payments.domain.model.*;
import com.acme.payments.domain.repository.AccountRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for AccountService. Uses Mockito to mock repository dependencies. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

  @Mock private AccountRepository accountRepository;

  @InjectMocks private AccountService accountService;

  private UUID customerId;
  private UUID accountId;

  @BeforeEach
  void setUp() {
    customerId = UUID.randomUUID();
    accountId = UUID.randomUUID();
  }

  @Test
  @DisplayName("createAccount - should create account with generated account number")
  void testCreateAccount_Success() {
    // Given
    CreateAccountCommand command =
        new CreateAccountCommand(customerId, "USD", "001", AccountType.CHECKING, false);

    // When
    Map<String, Object> result = accountService.handleCreateAccount(command);

    // Then: Result contains accountId and accountNumber
    assertThat(result).isNotNull();
    assertThat(result.get("accountId")).isNotNull();
    assertThat(result.get("accountNumber")).asString().startsWith("ACC");

    // Verify repository was called
    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).save(accountCaptor.capture());

    Account savedAccount = accountCaptor.getValue();
    assertThat(savedAccount.getCustomerId()).isEqualTo(customerId);
    assertThat(savedAccount.getCurrencyCode()).isEqualTo("USD");
    assertThat(savedAccount.getAccountType()).isEqualTo(AccountType.CHECKING);
    assertThat(savedAccount.getTransitNumber()).isEqualTo("001");
    assertThat(savedAccount.isLimitBased()).isFalse();
    assertThat(savedAccount.getAvailableBalance()).isEqualTo(Money.zero("USD"));
  }

  @Test
  @DisplayName("createAccount - should create limit-based account")
  void testCreateAccount_LimitBased() {
    // Given: limit-based account (limits are created separately via CreateLimitsCommand)
    CreateAccountCommand command =
        new CreateAccountCommand(
            customerId, "EUR", "002", AccountType.SAVINGS, true // limit-based
            );

    // When
    Map<String, Object> result = accountService.handleCreateAccount(command);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.get("accountId")).isNotNull();
    assertThat(result.get("accountNumber")).asString().startsWith("ACC");

    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).save(accountCaptor.capture());

    Account savedAccount = accountCaptor.getValue();
    assertThat(savedAccount.isLimitBased()).isTrue();
    assertThat(savedAccount.getAccountType()).isEqualTo(AccountType.SAVINGS);
    assertThat(savedAccount.getCurrencyCode()).isEqualTo("EUR");
  }

  @Test
  @DisplayName("createTransaction - CREDIT should increase account balance")
  void testCreateTransaction_Credit() {
    // Given
    Account account = createTestAccount(Money.of(1000, "USD"));
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    CreateTransactionCommand command =
        new CreateTransactionCommand(
            accountId, TransactionType.CREDIT, Money.of(500, "USD"), "Deposit");

    // When
    Transaction result = accountService.createTransaction(command);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.transactionId()).isNotNull();
    assertThat(result.accountId()).isEqualTo(accountId);
    assertThat(result.transactionType()).isEqualTo(TransactionType.CREDIT);
    assertThat(result.amount()).isEqualTo(Money.of(500, "USD"));
    assertThat(result.description()).isEqualTo("Deposit");

    // Verify balance increased
    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).save(accountCaptor.capture());

    Account savedAccount = accountCaptor.getValue();
    assertThat(savedAccount.getAvailableBalance()).isEqualTo(Money.of(1500, "USD"));
  }

  @Test
  @DisplayName("createTransaction - DEBIT should decrease account balance")
  void testCreateTransaction_Debit() {
    // Given
    Account account = createTestAccount(Money.of(1000, "USD"));
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    CreateTransactionCommand command =
        new CreateTransactionCommand(
            accountId, TransactionType.DEBIT, Money.of(300, "USD"), "Withdrawal");

    // When
    Transaction result = accountService.createTransaction(command);

    // Then
    assertThat(result.transactionType()).isEqualTo(TransactionType.DEBIT);
    assertThat(result.amount()).isEqualTo(Money.of(300, "USD"));

    // Verify balance decreased
    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).save(accountCaptor.capture());

    Account savedAccount = accountCaptor.getValue();
    assertThat(savedAccount.getAvailableBalance()).isEqualTo(Money.of(700, "USD"));
  }

  @Test
  @DisplayName("createTransaction - should throw AccountNotFoundException when account not found")
  void testCreateTransaction_AccountNotFound() {
    // Given
    UUID nonExistentAccountId = UUID.randomUUID();
    when(accountRepository.findById(nonExistentAccountId)).thenReturn(Optional.empty());

    CreateTransactionCommand command =
        new CreateTransactionCommand(
            nonExistentAccountId, TransactionType.CREDIT, Money.of(100, "USD"), "Test");

    // When / Then
    assertThatThrownBy(() -> accountService.createTransaction(command))
        .isInstanceOf(AccountService.AccountNotFoundException.class)
        .hasMessageContaining(nonExistentAccountId.toString());

    verify(accountRepository, never()).save(any(Account.class));
  }

  @Test
  @DisplayName("getAccountById - should return account when found")
  void testGetAccountById_Success() {
    // Given
    Account account = createTestAccount(Money.of(500, "USD"));
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    // When
    Account result = accountService.getAccountById(accountId);

    // Then
    assertThat(result).isEqualTo(account);
    verify(accountRepository).findById(accountId);
  }

  @Test
  @DisplayName("getAccountById - should throw AccountNotFoundException when not found")
  void testGetAccountById_NotFound() {
    // Given
    when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> accountService.getAccountById(accountId))
        .isInstanceOf(AccountService.AccountNotFoundException.class)
        .hasMessageContaining(accountId.toString());
  }

  @Test
  @DisplayName("handleCompleteAccountCreation - should return completion status")
  void testHandleCompleteAccountCreation() {
    // Given
    CompleteAccountCreationCommand command = new CompleteAccountCreationCommand(accountId);

    // When
    Map<String, Object> result = accountService.handleCompleteAccountCreation(command);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.get("status")).isEqualTo("completed");
  }

  @Test
  @DisplayName("createTransaction - should handle multiple transactions correctly")
  void testCreateTransaction_Multiple() {
    // Given
    Account account = createTestAccount(Money.of(1000, "USD"));
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    // When - First transaction: Credit 500
    CreateTransactionCommand credit =
        new CreateTransactionCommand(
            accountId, TransactionType.CREDIT, Money.of(500, "USD"), "Credit 1");
    accountService.createTransaction(credit);

    // Verify balance after first transaction
    ArgumentCaptor<Account> captor1 = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository, times(1)).save(captor1.capture());
    Account afterCredit = captor1.getValue();
    assertThat(afterCredit.getAvailableBalance()).isEqualTo(Money.of(1500, "USD"));

    // Setup for second transaction - return updated account
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(afterCredit));

    // When - Second transaction: Debit 200
    CreateTransactionCommand debit =
        new CreateTransactionCommand(
            accountId, TransactionType.DEBIT, Money.of(200, "USD"), "Debit 1");
    accountService.createTransaction(debit);

    // Then - Verify final balance
    ArgumentCaptor<Account> captor2 = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository, times(2)).save(captor2.capture());
    Account afterDebit = captor2.getAllValues().get(1);
    assertThat(afterDebit.getAvailableBalance()).isEqualTo(Money.of(1300, "USD"));
  }

  // Helper method to create test accounts
  private Account createTestAccount(Money initialBalance) {
    Account account =
        new Account(
            accountId,
            customerId,
            "ACC123456",
            initialBalance.currencyCode(),
            AccountType.CHECKING,
            "001",
            false,
            Money.zero(initialBalance.currencyCode()));

    // If initial balance > 0, create a credit transaction to set it
    if (initialBalance.amount().doubleValue() > 0) {
      account.createTransaction(TransactionType.CREDIT, initialBalance, "Initial balance");
    }

    return account;
  }
}
