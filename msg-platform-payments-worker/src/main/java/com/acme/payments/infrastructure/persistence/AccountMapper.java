package com.acme.payments.infrastructure.persistence;

import com.acme.payments.domain.model.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Mapper utility for converting ResultSet to domain objects */
public class AccountMapper {

  public static Account mapFromResultSet(ResultSet rs) throws SQLException {
    UUID accountId = (UUID) rs.getObject("account_id");
    UUID customerId = (UUID) rs.getObject("customer_id");
    String accountNumber = rs.getString("account_number");
    String currencyCode = rs.getString("currency_code");
    AccountType accountType = AccountType.valueOf(rs.getString("account_type"));
    String transitNumber = rs.getString("transit_number");
    boolean limitBased = rs.getBoolean("limit_based");
    BigDecimal balanceAmount = rs.getBigDecimal("available_balance");
    Money availableBalance =
        new Money(balanceAmount.setScale(2, RoundingMode.HALF_UP), currencyCode);

    return new Account(
        accountId,
        customerId,
        accountNumber,
        currencyCode,
        accountType,
        transitNumber,
        limitBased,
        availableBalance);
  }

  public static Transaction mapTransactionFromResultSet(ResultSet rs) throws SQLException {
    UUID transactionId = (UUID) rs.getObject("transaction_id");
    UUID accountId = (UUID) rs.getObject("account_id");
    TransactionType transactionType = TransactionType.valueOf(rs.getString("transaction_type"));
    String currencyCode = rs.getString("currency_code");
    BigDecimal amountValue = rs.getBigDecimal("amount");
    BigDecimal balanceValue = rs.getBigDecimal("balance");
    Money amount = new Money(amountValue.setScale(2, RoundingMode.HALF_UP), currencyCode);
    Money balance = new Money(balanceValue.setScale(2, RoundingMode.HALF_UP), currencyCode);
    String description = rs.getString("description");

    return new Transaction(
        transactionId,
        accountId,
        rs.getTimestamp("transaction_date").toInstant(),
        transactionType,
        amount,
        description,
        balance);
  }
}
