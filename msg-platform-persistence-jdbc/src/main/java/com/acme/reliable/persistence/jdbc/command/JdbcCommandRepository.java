package com.acme.reliable.persistence.jdbc.command;

import com.acme.reliable.domain.Command;
import com.acme.reliable.persistence.jdbc.ExceptionTranslator;
import com.acme.reliable.repository.CommandRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract JDBC implementation of CommandRepository using Template Method pattern.
 * Subclasses override database-specific SQL methods.
 */
public abstract class JdbcCommandRepository implements CommandRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcCommandRepository.class);

  protected final DataSource dataSource;

  public JdbcCommandRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  @Transactional
  public void insertPending(
      UUID id,
      String name,
      String businessKey,
      String payload,
      String idempotencyKey,
      String reply) {
    String sql = getInsertPendingSql();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setObject(1, id);
      ps.setString(2, name);
      ps.setString(3, businessKey);
      ps.setString(4, payload);
      ps.setString(5, idempotencyKey);
      ps.setString(6, "PENDING");
      ps.setInt(7, 0);
      ps.setTimestamp(8, Timestamp.from(java.time.Instant.now()));
      ps.setString(9, reply);

      ps.executeUpdate();
      LOG.debug("Inserted command: id={}, name={}", id, name);

    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "insert pending command", LOG);
    }
  }

  @Transactional(readOnly = true)
  public Optional<Command> findById(UUID id) {
    String sql = getFindByIdSql();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setObject(1, id);

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapResultSetToCommand(rs));
        }
      }

      return Optional.empty();

    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "find command by id", LOG);
    }
  }

  @Override
  @Transactional
  public void updateToRunning(UUID id, Timestamp lease) {
    String sql = getUpdateToRunningSql();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, "RUNNING");
      ps.setTimestamp(2, lease);
      ps.setTimestamp(3, Timestamp.from(java.time.Instant.now()));
      ps.setObject(4, id);

      int updated = ps.executeUpdate();
      if (updated == 0) {
        LOG.warn("No rows updated for updateToRunning: id={}", id);
      }

    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "update command to RUNNING", LOG);
    }
  }

  @Override
  @Transactional
  public void updateToSucceeded(UUID id) {
    String sql = getUpdateToSucceededSql();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, "SUCCEEDED");
      ps.setTimestamp(2, Timestamp.from(java.time.Instant.now()));
      ps.setObject(3, id);

      int updated = ps.executeUpdate();
      if (updated == 0) {
        LOG.warn("No rows updated for updateToSucceeded: id={}", id);
      }

    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "update command to SUCCEEDED", LOG);
    }
  }

  @Override
  @Transactional
  public void updateToFailed(UUID id, String error) {
    String sql = getUpdateToFailedSql();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, "FAILED");
      ps.setString(2, error);
      ps.setTimestamp(3, Timestamp.from(java.time.Instant.now()));
      ps.setObject(4, id);

      int updated = ps.executeUpdate();
      if (updated == 0) {
        LOG.warn("No rows updated for updateToFailed: id={}", id);
      }

    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "update command to FAILED", LOG);
    }
  }

  @Override
  @Transactional
  public void incrementRetries(UUID id, String error) {
    String sql = getIncrementRetriesSql();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, error);
      ps.setTimestamp(2, Timestamp.from(java.time.Instant.now()));
      ps.setObject(3, id);

      int updated = ps.executeUpdate();
      if (updated == 0) {
        LOG.warn("No rows updated for incrementRetries: id={}", id);
      }

    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "increment retries", LOG);
    }
  }

  @Override
  @Transactional
  public void updateToTimedOut(UUID id, String reason) {
    String sql = getUpdateToTimedOutSql();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, "TIMED_OUT");
      ps.setString(2, reason);
      ps.setTimestamp(3, Timestamp.from(java.time.Instant.now()));
      ps.setObject(4, id);

      int updated = ps.executeUpdate();
      if (updated == 0) {
        LOG.warn("No rows updated for updateToTimedOut: id={}", id);
      }

    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "update command to TIMED_OUT", LOG);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByIdempotencyKey(String key) {
    String sql = getExistsByIdempotencyKeySql();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, key);

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getInt(1) > 0;
      }

    } catch (SQLException e) {
      throw ExceptionTranslator.translateException(e, "check idempotency key", LOG);
    }
  }

  // Template methods for database-specific SQL

  protected abstract String getInsertPendingSql();

  protected abstract String getFindByIdSql();

  protected abstract String getUpdateToRunningSql();

  protected abstract String getUpdateToSucceededSql();

  protected abstract String getUpdateToFailedSql();

  protected abstract String getIncrementRetriesSql();

  protected abstract String getUpdateToTimedOutSql();

  protected abstract String getExistsByIdempotencyKeySql();

  // Helper method for result set mapping

  protected Command mapResultSetToCommand(ResultSet rs) throws SQLException {
    Command command = new Command();
    command.setId((UUID) rs.getObject("id"));
    command.setName(rs.getString("name"));
    command.setBusinessKey(rs.getString("business_key"));
    command.setPayload(rs.getString("payload"));
    command.setIdempotencyKey(rs.getString("idempotency_key"));
    command.setStatus(rs.getString("status"));

    Timestamp requestedAt = rs.getTimestamp("requested_at");
    if (requestedAt != null) {
      command.setRequestedAt(requestedAt.toInstant());
    }

    Timestamp updatedAt = rs.getTimestamp("updated_at");
    if (updatedAt != null) {
      command.setUpdatedAt(updatedAt.toInstant());
    }

    command.setRetries(rs.getInt("retries"));

    Timestamp lease = rs.getTimestamp("processing_lease_until");
    if (lease != null) {
      command.setProcessingLeaseUntil(lease.toInstant());
    }

    String lastError = rs.getString("last_error");
    if (lastError != null) {
      command.setLastError(lastError);
    }

    String reply = rs.getString("reply");
    if (reply != null) {
      command.setReply(reply);
    }

    return command;
  }
}
