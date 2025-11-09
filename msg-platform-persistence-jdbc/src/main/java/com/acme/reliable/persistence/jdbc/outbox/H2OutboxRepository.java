package com.acme.reliable.persistence.jdbc.outbox;

import com.acme.reliable.domain.Outbox;
import com.acme.reliable.repository.OutboxRepository;
import io.micronaut.context.annotation.Requires;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * H2-specific implementation of OutboxRepository using H2 dialect.
 * H2 doesn't support RETURNING clause, so batch operations need special handling.
 */
@Singleton
@Requires(property = "db.dialect", value = "H2")
public class H2OutboxRepository extends JdbcOutboxRepository implements OutboxRepository {

  private static final Logger LOG = LoggerFactory.getLogger(H2OutboxRepository.class);

  public H2OutboxRepository(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  protected String getInsertSql() {
    return """
        INSERT INTO outbox
        (category, topic, "key", "type", payload, headers, status, attempts, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
  }

  @Override
  protected String getClaimIfNewSql() {
    return """
        UPDATE outbox
        SET status = 'CLAIMED'
        WHERE id = ? AND status = 'NEW'
        """;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Outbox> claimIfNew(long id) {
    // First, try to update the entry to CLAIMED
    String updateSql = getClaimIfNewSql();

    try (Connection conn = dataSource.getConnection()) {
      // Update
      try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
        updatePs.setLong(1, id);
        int rowsUpdated = updatePs.executeUpdate();

        if (rowsUpdated == 0) {
          // Entry was already claimed or doesn't exist in NEW status
          return Optional.empty();
        }
      }

      // Fetch the updated entry
      String selectSql =
          """
              SELECT id, category, topic, "key", "type", payload, headers, status, attempts,
                     next_at, claimed_by, created_at, published_at, last_error
              FROM outbox
              WHERE id = ?
              """;

      try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
        selectPs.setLong(1, id);

        try (ResultSet rs = selectPs.executeQuery()) {
          if (rs.next()) {
            return Optional.of(mapResultSetToOutbox(rs));
          }
        }
      }

      return Optional.empty();

    } catch (SQLException e) {
      LOG.error("Failed to claim outbox entry by id: {}", id, e);
      throw new RuntimeException("Failed to claim outbox entry", e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<Outbox> sweepBatch(int max) {
    String selectSql =
        """
            SELECT id, category, topic, "key", "type", payload, headers, status, attempts,
                   next_at, claimed_by, created_at, published_at, last_error
            FROM outbox
            WHERE (status = 'NEW' OR (status = 'CLAIMED' AND created_at < DATEADD('MINUTE', -5, NOW())))
              AND (next_at IS NULL OR next_at <= NOW())
            ORDER BY created_at ASC
            LIMIT ?
            """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement selectPs = conn.prepareStatement(selectSql)) {

      selectPs.setInt(1, max);

      List<Outbox> results = new ArrayList<>();
      List<Long> ids = new ArrayList<>();

      try (ResultSet rs = selectPs.executeQuery()) {
        while (rs.next()) {
          long id = rs.getLong("id");
          ids.add(id);
          results.add(mapResultSetToOutbox(rs));
        }
      }

      // Update the status of claimed entries to CLAIMED
      if (!ids.isEmpty()) {
        String updateSql = "UPDATE outbox SET status = 'CLAIMED' WHERE id IN (";
        for (int i = 0; i < ids.size(); i++) {
          updateSql += "?";
          if (i < ids.size() - 1) {
            updateSql += ", ";
          }
        }
        updateSql += ")";

        try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
          for (int i = 0; i < ids.size(); i++) {
            updatePs.setLong(i + 1, ids.get(i));
          }
          updatePs.executeUpdate();
        }

        // Set status to CLAIMED in the returned objects to reflect the update
        for (Outbox outbox : results) {
          outbox.setStatus("CLAIMED");
        }
      }

      return results;

    } catch (SQLException e) {
      LOG.error("Failed to sweep batch of outbox entries, max={}", max, e);
      throw new RuntimeException("Failed to sweep batch", e);
    }
  }

  @Override
  protected String getSweepBatchSql() {
    // Not used in H2 implementation due to different query strategy
    return "";
  }

  @Override
  protected String getMarkPublishedSql() {
    return """
        UPDATE outbox
        SET status = 'PUBLISHED', published_at = ?
        WHERE id = ?
        """;
  }

  @Override
  protected String getMarkFailedSql() {
    return """
        UPDATE outbox
        SET last_error = ?, next_at = ?
        WHERE id = ?
        """;
  }

  @Override
  protected String getRescheduleSql() {
    return """
        UPDATE outbox
        SET next_at = ?, last_error = ?
        WHERE id = ?
        """;
  }

  @Override
  protected String getRecoverStuckSql() {
    return """
        UPDATE outbox
        SET status = 'NEW', next_at = NULL
        WHERE status = 'CLAIMED' AND created_at < ?
        """;
  }
}
