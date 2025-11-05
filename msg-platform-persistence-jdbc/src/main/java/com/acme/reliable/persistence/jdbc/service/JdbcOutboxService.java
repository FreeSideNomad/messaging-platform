package com.acme.reliable.persistence.jdbc.service;

import com.acme.reliable.config.TimeoutConfig;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.persistence.jdbc.JdbcOutboxRepository;
import com.acme.reliable.service.OutboxService;
import com.acme.reliable.spi.OutboxRow;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of OutboxService using hybrid approach: - Repository for simple operations
 * (insert, markPublished, reschedule) - Manual JDBC for complex RETURNING queries (claim
 * operations) - TransactionOperations ensures all operations join existing transactions
 */
@Singleton
public class JdbcOutboxService implements OutboxService {
  private final JdbcOutboxRepository repository;
  private final TransactionOperations<Connection> transactionOps;
  private final long claimTimeoutSeconds;

  public JdbcOutboxService(
      JdbcOutboxRepository repository,
      TransactionOperations<Connection> transactionOps,
      TimeoutConfig timeoutConfig) {
    this.repository = repository;
    this.transactionOps = transactionOps;
    this.claimTimeoutSeconds = timeoutConfig.getOutboxClaimTimeoutSeconds();
  }

  @Transactional
  public UUID addReturningId(OutboxRow r) {
    var id = r.id() != null ? r.id() : UUID.randomUUID();
    var headersJson =
        r.headers() != null && !r.headers().isEmpty() ? Jsons.toJson(r.headers()) : "{}";
    repository.insert(
        id, r.category(), r.topic(), r.key(), r.type(), r.payload(), headersJson, "NEW", 0);
    return id;
  }

  @Transactional
  public Optional<OutboxRow> claimOne(UUID id) {
    return transactionOps
        .findTransactionStatus()
        .map(
            status -> {
              try {
                Connection conn = (Connection) status.getConnection();
                try (var ps =
                    conn.prepareStatement(
                        "UPDATE outbox SET status='CLAIMED', claimed_by=? WHERE id=? AND status='NEW' "
                            + "RETURNING id, category, topic, key, type, payload, headers, attempts")) {
                  ps.setString(1, getHostName());
                  ps.setObject(2, id);
                  try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                      return Optional.of(mapRow(rs));
                    }
                    return Optional.<OutboxRow>empty();
                  }
                }
              } catch (SQLException e) {
                throw new RuntimeException("Failed to claim outbox entry", e);
              }
            })
        .orElseGet(Optional::empty);
  }

  @Transactional
  public List<OutboxRow> claim(int max, String claimer) {
    return transactionOps
        .findTransactionStatus()
        .map(
            status -> {
              try {
                Connection conn = (Connection) status.getConnection();
                try (var ps =
                    conn.prepareStatement(
                        "WITH c AS ("
                            + "  SELECT id FROM outbox "
                            + "  WHERE (status='NEW' OR (status='CLAIMED' AND created_at < now() - interval '"
                            + claimTimeoutSeconds
                            + " seconds')) "
                            + "    AND (next_at IS NULL OR next_at <= now()) "
                            + "  ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED"
                            + ") "
                            + "UPDATE outbox o SET status='CLAIMED', claimed_by=?, attempts=o.attempts FROM c "
                            + "WHERE o.id=c.id "
                            + "RETURNING o.id, o.category, o.topic, o.key, o.type, o.payload, o.headers, o.attempts")) {
                  ps.setInt(1, max);
                  ps.setString(2, claimer);
                  try (ResultSet rs = ps.executeQuery()) {
                    List<OutboxRow> results = new ArrayList<>();
                    while (rs.next()) {
                      results.add(mapRow(rs));
                    }
                    return results;
                  }
                }
              } catch (SQLException e) {
                throw new RuntimeException("Failed to claim outbox batch", e);
              }
            })
        .orElseGet(List::of);
  }

  @Transactional
  public void markPublished(UUID id) {
    repository.markPublished(id);
  }

  @Transactional
  public void reschedule(UUID id, long backoffMs, String err) {
    repository.reschedule(id, backoffMs, err);
  }

  @SuppressWarnings("unchecked")
  private OutboxRow mapRow(ResultSet rs) throws SQLException {
    return new OutboxRow(
        (UUID) rs.getObject("id"),
        rs.getString("category"),
        rs.getString("topic"),
        rs.getString("key"),
        rs.getString("type"),
        rs.getString("payload"),
        Jsons.fromJson(rs.getString("headers"), Map.class),
        rs.getInt("attempts"));
  }

  private String getHostName() {
    try {
      return java.net.InetAddress.getLoopbackAddress().getHostName();
    } catch (Exception e) {
      return "unknown-host";
    }
  }
}
