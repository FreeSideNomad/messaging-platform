package com.acme.reliable.persistence.jdbc;

import com.acme.reliable.repository.InboxRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.sql.*;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract JDBC implementation of InboxRepository using Template Method pattern.
 * Subclasses override database-specific SQL methods.
 */
public abstract class JdbcInboxRepository implements InboxRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcInboxRepository.class);

  protected final DataSource dataSource;

  public JdbcInboxRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  @Transactional
  public int insertIfAbsent(String messageId, String handler) {
    String sql = getInsertIfAbsentSql();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      // Check if this is the H2-style INSERT...SELECT...WHERE NOT EXISTS
      // This requires binding parameters differently
      if (sql.contains("WHERE NOT EXISTS")) {
        // H2 style: INSERT INTO inbox SELECT ?, ?, ? WHERE NOT EXISTS(...? AND ? AND ...)
        ps.setString(1, messageId);
        ps.setString(2, handler);
        ps.setTimestamp(3, Timestamp.from(java.time.Instant.now()));
        ps.setString(4, messageId);
        ps.setString(5, handler);
      } else {
        // PostgreSQL style and others: standard INSERT with parameters
        ps.setString(1, messageId);
        ps.setString(2, handler);
        ps.setTimestamp(3, Timestamp.from(java.time.Instant.now()));
      }

      int rowsInserted = ps.executeUpdate();
      if (rowsInserted > 0) {
        LOG.debug("Inserted inbox entry: messageId={}, handler={}", messageId, handler);
      } else {
        LOG.debug("Inbox entry already exists: messageId={}, handler={}", messageId, handler);
      }

      return rowsInserted;

    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "insert inbox entry", LOG);
    }
  }

  // Template method for database-specific SQL

  protected abstract String getInsertIfAbsentSql();
}
