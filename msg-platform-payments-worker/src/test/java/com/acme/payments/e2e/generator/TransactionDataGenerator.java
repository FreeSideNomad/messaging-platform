package com.acme.payments.e2e.generator;

import com.acme.payments.application.command.CreateTransactionCommand;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.TransactionType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates transaction creation commands.
 *
 * <p>Types of transactions: - Opening Credit: Initial balance for new accounts - Funding:
 * Additional credits to top up account (1-20% of balance)
 */
public class TransactionDataGenerator extends BaseDataGenerator {

  public TransactionDataGenerator() {
    super();
  }

  public TransactionDataGenerator(long seed) {
    super(seed);
  }

  /** Generate an opening credit transaction (initial account funding) */
  public CreateTransactionCommand generateOpeningCredit(UUID accountId, Money amount) {
    return new CreateTransactionCommand(
        accountId, TransactionType.CREDIT, amount, "Opening credit - initial account funding");
  }

  /** Generate a funding transaction (top-up) */
  public CreateTransactionCommand generateFundingTransaction(
      UUID accountId, String currencyCode, BigDecimal accountBalance) {
    BigDecimal fundingAmount = generateFundingAmount(accountBalance);
    Money amount = new Money(fundingAmount, currencyCode);

    return new CreateTransactionCommand(
        accountId,
        TransactionType.CREDIT,
        amount,
        "Account funding - " + faker.commerce().productName());
  }

  /** Generate multiple funding transactions */
  public List<CreateTransactionCommand> generateFundingTransactions(
      UUID accountId, String currencyCode, BigDecimal accountBalance, int minCount, int maxCount) {
    int count = generateRandomInt(minCount, maxCount);
    List<CreateTransactionCommand> transactions = new ArrayList<>(count);

    for (int i = 0; i < count; i++) {
      transactions.add(generateFundingTransaction(accountId, currencyCode, accountBalance));
    }

    return transactions;
  }

  /** Generate funding amount proportional to account balance (1-20% of balance) */
  protected BigDecimal generateFundingAmount(BigDecimal accountBalance) {
    // Generate 1-20% of account balance
    double percentage = 1.0 + (random.nextDouble() * 19.0); // 1.0 to 20.0
    BigDecimal amount = accountBalance.multiply(BigDecimal.valueOf(percentage / 100.0));

    // Ensure at least 100 and round to 2 decimals
    return amount.max(BigDecimal.valueOf(100)).setScale(2, java.math.RoundingMode.HALF_UP);
  }
}
