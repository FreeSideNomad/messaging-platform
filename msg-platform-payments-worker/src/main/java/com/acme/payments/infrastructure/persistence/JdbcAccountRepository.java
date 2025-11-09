package com.acme.payments.infrastructure.persistence;

import com.acme.payments.domain.model.Account;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.Transaction;
import com.acme.payments.domain.model.TransactionType;
import com.acme.payments.domain.repository.AccountRepository;
import com.acme.reliable.persistence.jdbc.ExceptionTranslator;
import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** JDBC implementation of AccountRepository */
@Singleton
@RequiredArgsConstructor
@Slf4j
public class JdbcAccountRepository implements AccountRepository {
  private final DataSource dataSource;

  @Override
  public void save(Account account) {
    log.debug("Saving account: {}", account.getAccountId());

    try (Connection conn = dataSource.getConnection()) {
      // Check if account exists
      boolean exists = accountExists(conn, account.getAccountId());

      if (exists) {
        updateAccount(conn, account);
      } else {
        insertAccount(conn, account);
      }

      // Save transactions
      saveTransactions(conn, account);

      // Don't commit here - let the ambient transaction (if any) handle it
      // This allows repositories to work both in tests (@Transactional) and production
    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "save account", log);
    }
  }

  @Override
  public Optional<Account> findById(UUID accountId) {
    log.debug("Finding account by id: {}", accountId);

    try (Connection conn = dataSource.getConnection()) {
      String sql =
          """
                SELECT account_id, customer_id, account_number, currency_code,
                       account_type, transit_number, limit_based, available_balance, created_at
                FROM payments.account
                WHERE account_id = ?
                """;

      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, accountId);

        try (ResultSet rs = stmt.executeQuery()) {
          if (rs.next()) {
            Account account = mapAccount(rs);
            loadTransactions(conn, account);
            return Optional.of(account);
          }
        }
      }
    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "find account by id", log);
    }

    return Optional.empty();
  }

  @Override
  public Optional<Account> findByAccountNumber(String accountNumber) {
    log.debug("Finding account by account number: {}", accountNumber);

    try (Connection conn = dataSource.getConnection()) {
      String sql =
          """
                SELECT account_id, customer_id, account_number, currency_code,
                       account_type, transit_number, limit_based, available_balance, created_at
                FROM payments.account
                WHERE account_number = ?
                """;

      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setString(1, accountNumber);

        try (ResultSet rs = stmt.executeQuery()) {
          if (rs.next()) {
            Account account = mapAccount(rs);
            loadTransactions(conn, account);
            return Optional.of(account);
          }
        }
      }
    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "find account by account number", log);
    }

    return Optional.empty();
  }

  private boolean accountExists(Connection conn, UUID accountId) throws SQLException {
    String sql = "SELECT 1 FROM payments.account WHERE account_id = ?";
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, accountId);
      try (ResultSet rs = stmt.executeQuery()) {
        return rs.next();
      }
    }
  }

  private void insertAccount(Connection conn, Account account) throws SQLException {
    String sql =
        """
            INSERT INTO payments.account (account_id, customer_id, account_number, currency_code,
                               account_type, transit_number, limit_based, available_balance, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, account.getAccountId());
      stmt.setObject(2, account.getCustomerId());
      stmt.setString(3, account.getAccountNumber());
      stmt.setString(4, account.getCurrencyCode());
      stmt.setString(5, account.getAccountType().name());
      stmt.setString(6, account.getTransitNumber());
      stmt.setBoolean(7, account.isLimitBased());
      stmt.setBigDecimal(8, account.getAvailableBalance().amount());
      stmt.setTimestamp(9, Timestamp.from(account.getCreatedAt()));

      stmt.executeUpdate();
    }
  }

  private void updateAccount(Connection conn, Account account) throws SQLException {
    String sql =
        """
            UPDATE payments.account
            SET available_balance = ?
            WHERE account_id = ?
            """;

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setBigDecimal(1, account.getAvailableBalance().amount());
      stmt.setObject(2, account.getAccountId());

      stmt.executeUpdate();
    }
  }

  private void saveTransactions(Connection conn, Account account) throws SQLException {
    // Delete existing transactions and reinsert (simpler than tracking changes)
    String deleteSql = "DELETE FROM payments.transaction WHERE account_id = ?";
    try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
      stmt.setObject(1, account.getAccountId());
      stmt.executeUpdate();
    }

    // Insert all transactions
    String insertSql =
        """
            INSERT INTO payments.transaction (transaction_id, account_id, transaction_date,
                                   transaction_type, amount, currency_code, description, balance)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
      for (Transaction txn : account.getTransactions()) {
        stmt.setObject(1, txn.transactionId());
        stmt.setObject(2, txn.accountId());
        stmt.setTimestamp(3, Timestamp.from(txn.transactionDate()));
        stmt.setString(4, txn.transactionType().name());
        stmt.setBigDecimal(5, txn.amount().amount());
        stmt.setString(6, txn.amount().currencyCode());
        stmt.setString(7, txn.description());
        stmt.setBigDecimal(8, txn.balance().amount());

        stmt.addBatch();
      }
      stmt.executeBatch();
    }
  }

  private void loadTransactions(Connection conn, Account account) throws SQLException {
    String sql =
        """
            SELECT transaction_id, account_id, transaction_date, transaction_type,
                   amount, currency_code, description, balance
            FROM payments.transaction
            WHERE account_id = ?
            ORDER BY transaction_date ASC
            """;

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, account.getAccountId());

      try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
          Transaction transaction = mapTransaction(rs);
          account.restoreTransaction(transaction);
        }
      }
    }
  }

  private Transaction mapTransaction(ResultSet rs) throws SQLException {
    UUID transactionId = (UUID) rs.getObject("transaction_id");
    UUID accountId = (UUID) rs.getObject("account_id");
    java.time.Instant transactionDate = rs.getTimestamp("transaction_date").toInstant();
    TransactionType transactionType = TransactionType.valueOf(rs.getString("transaction_type"));
    Money amount =
        new Money(
            rs.getBigDecimal("amount").setScale(2, java.math.RoundingMode.HALF_UP),
            rs.getString("currency_code"));
    String description = rs.getString("description");
    Money balance =
        new Money(
            rs.getBigDecimal("balance").setScale(2, java.math.RoundingMode.HALF_UP),
            rs.getString("currency_code"));

    return new Transaction(
        transactionId, accountId, transactionDate, transactionType, amount, description, balance);
  }

  private Account mapAccount(ResultSet rs) throws SQLException {
    return AccountMapper.mapFromResultSet(rs);
  }
}
