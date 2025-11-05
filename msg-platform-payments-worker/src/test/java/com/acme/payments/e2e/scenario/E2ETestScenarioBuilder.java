package com.acme.payments.e2e.scenario;

import com.acme.payments.application.command.CreateTransactionCommand;
import com.acme.payments.application.command.InitiateCreateAccountProcess;
import com.acme.payments.application.command.InitiateSimplePaymentCommand;
import com.acme.payments.domain.model.Money;
import com.acme.payments.e2e.generator.AccountDataGenerator;
import com.acme.payments.e2e.generator.PaymentDataGenerator;
import com.acme.payments.e2e.generator.TransactionDataGenerator;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates generation of complete E2E test scenarios. Maintains referential integrity across
 * accounts, transactions, and payments.
 */
@Slf4j
public class E2ETestScenarioBuilder {

  private final AccountDataGenerator accountGenerator;
  private final TransactionDataGenerator transactionGenerator;
  private final PaymentDataGenerator paymentGenerator;

  public E2ETestScenarioBuilder() {
    this.accountGenerator = new AccountDataGenerator();
    this.transactionGenerator = new TransactionDataGenerator();
    this.paymentGenerator = new PaymentDataGenerator();
  }

  public E2ETestScenarioBuilder(long seed) {
    this.accountGenerator = new AccountDataGenerator(seed);
    this.transactionGenerator = new TransactionDataGenerator(seed);
    this.paymentGenerator = new PaymentDataGenerator(seed);
  }

  /** Build a complete test scenario from configuration */
  public E2ETestScenario build(TestScenarioConfig config) {
    log.info(
        "Building E2E test scenario with config: {} accounts, {}-{} payments, {}-{} funding",
        config.accountCount(),
        config.minPaymentsPerAccount(),
        config.maxPaymentsPerAccount(),
        config.minFundingPerAccount(),
        config.maxFundingPerAccount());

    List<InitiateCreateAccountProcess> accountCommands = new ArrayList<>();
    List<CreateTransactionCommand> openingTransactions = new ArrayList<>();
    List<CreateTransactionCommand> fundingTransactions = new ArrayList<>();
    List<InitiateSimplePaymentCommand> paymentCommands = new ArrayList<>();
    Map<UUID, AccountMetadata> accountIndex = new HashMap<>();

    // Generate accounts and related data
    for (int i = 0; i < config.accountCount(); i++) {
      if (i > 0 && i % 1000 == 0) {
        log.info("Generated {} / {} accounts", i, config.accountCount());
      }

      // Generate account
      InitiateCreateAccountProcess accountCommand =
          accountGenerator.generateAccount(config.limitBasedAccountPercentage());
      accountCommands.add(accountCommand);

      // Store metadata for reference
      AccountMetadata metadata = createAccountMetadata(accountCommand);
      accountIndex.put(metadata.accountId(), metadata);

      // Generate opening credit transaction
      Money openingAmount = new Money(metadata.initialBalance(), metadata.currencyCode());
      CreateTransactionCommand openingTxn =
          transactionGenerator.generateOpeningCredit(metadata.accountId(), openingAmount);
      openingTransactions.add(openingTxn);

      // Generate funding transactions
      List<CreateTransactionCommand> funding =
          transactionGenerator.generateFundingTransactions(
              metadata.accountId(),
              metadata.currencyCode(),
              metadata.initialBalance(),
              config.minFundingPerAccount(),
              config.maxFundingPerAccount());
      fundingTransactions.addAll(funding);

      // Generate payments
      List<InitiateSimplePaymentCommand> payments =
          paymentGenerator.generatePayments(
              metadata.customerId(),
              metadata.accountId(),
              metadata.currencyCode(),
              metadata.limits(),
              config.minPaymentsPerAccount(),
              config.maxPaymentsPerAccount(),
              config.fxPaymentPercentage());
      paymentCommands.addAll(payments);
    }

    log.info(
        "Generated {} accounts, {} opening txns, {} funding txns, {} payments",
        accountCommands.size(),
        openingTransactions.size(),
        fundingTransactions.size(),
        paymentCommands.size());

    return new E2ETestScenario(
        accountCommands, openingTransactions, fundingTransactions, paymentCommands, accountIndex);
  }

  /** Create metadata from account command for internal tracking */
  private AccountMetadata createAccountMetadata(InitiateCreateAccountProcess command) {
    // Extract account ID - we need to generate one since the command will get one when created
    UUID accountId = UUID.randomUUID();

    // Calculate initial balance from limits if limit-based
    // For non-limit-based accounts, use a default balance
    java.math.BigDecimal initialBalance;
    if (command.limitBased() && command.limits() != null) {
      // Hourly limit is 10% of balance, so balance = hourly limit / 0.10
      Money hourlyLimit = command.limits().get(com.acme.payments.domain.model.PeriodType.HOUR);
      initialBalance =
          hourlyLimit
              .amount()
              .divide(java.math.BigDecimal.valueOf(0.10), 2, java.math.RoundingMode.HALF_UP);
    } else {
      // Non-limit-based accounts: use default balance of 50,000
      initialBalance = java.math.BigDecimal.valueOf(50_000);
    }

    return new AccountMetadata(
        accountId,
        command.customerId(),
        command.currencyCode(),
        initialBalance,
        command.limits(),
        command.accountType(),
        command.transitNumber(),
        command.limitBased());
  }
}
