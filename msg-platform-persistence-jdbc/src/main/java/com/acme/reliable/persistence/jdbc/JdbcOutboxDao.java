package com.acme.reliable.persistence.jdbc;

import com.acme.reliable.persistence.jdbc.model.OutboxEntity;
import com.acme.reliable.spi.OutboxDao;
import com.acme.reliable.spi.OutboxRow;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcOutboxDao extends OutboxDao, GenericRepository<OutboxEntity, Long> {

  @Query(
      value =
          """
        UPDATE outbox
        SET status='SENDING', claimed_at=NOW(), attempts=attempts+1
        WHERE id = :id AND status='NEW'
        """,
      nativeQuery = true)
  int claimIfNewUpdate(long id);

  @Query(
      value =
          """
        SELECT * FROM outbox WHERE id = :id AND status='SENDING'
        """,
      nativeQuery = true)
  Optional<OutboxEntity> selectClaimedRow(long id);

  @Override
  default Optional<OutboxRow> claimIfNew(long id) {
    int updated = claimIfNewUpdate(id);
    if (updated == 0) {
      return Optional.empty();
    }
    return selectClaimedRow(id)
        .map(
            entity ->
                new OutboxRow(
                    entity.getId(),
                    entity.getCategory(),
                    entity.getTopic(),
                    entity.getKey(),
                    entity.getType(),
                    entity.getPayload(),
                    entity.getHeaders(),
                    entity.getAttempts()));
  }

  @Query(
      value =
          """
        WITH c AS (
            SELECT id
            FROM outbox
            WHERE status IN ('NEW', 'FAILED')
              AND (next_at IS NULL OR next_at <= NOW())
            ORDER BY created_at
            LIMIT :max
            FOR UPDATE SKIP LOCKED
        )
        UPDATE outbox o
        SET status='SENDING', claimed_at=NOW(), attempts=o.attempts+1
        FROM c
        WHERE o.id = c.id
        """,
      nativeQuery = true)
  int sweepBatchUpdate(int max);

  @Query(
      value =
          """
        SELECT * FROM outbox WHERE status='SENDING' AND claimed_at >= NOW() - INTERVAL '1 second'
        ORDER BY id LIMIT :max
        """,
      nativeQuery = true)
  List<OutboxEntity> selectRecentlyClaimed(int max);

  @Override
  default List<OutboxRow> sweepBatch(int max) {
    int updated = sweepBatchUpdate(max);
    if (updated == 0) {
      return List.of();
    }
    return selectRecentlyClaimed(max).stream()
        .map(
            entity ->
                new OutboxRow(
                    entity.getId(),
                    entity.getCategory(),
                    entity.getTopic(),
                    entity.getKey(),
                    entity.getType(),
                    entity.getPayload(),
                    entity.getHeaders(),
                    entity.getAttempts()))
        .toList();
  }

  @Query(value = "UPDATE outbox SET status='PUBLISHED' WHERE id = :id", nativeQuery = true)
  void markPublished(long id);

  @Query(
      value =
          """
        UPDATE outbox
        SET status='FAILED',
            next_at = :nextAttempt,
            last_error = :error
        WHERE id = :id
        """,
      nativeQuery = true)
  void markFailed(long id, String error, Instant nextAttempt);

  @Query(
      value =
          """
        UPDATE outbox
        SET status='NEW', claimed_at=NULL
        WHERE status='SENDING'
          AND claimed_at < (NOW() - :olderThanSeconds * INTERVAL '1 second')
        """,
      nativeQuery = true)
  int recoverStuckBySeconds(long olderThanSeconds);

  @Override
  default int recoverStuck(Duration olderThan) {
    return recoverStuckBySeconds(olderThan.getSeconds());
  }

  @Query(
      value =
          """
        INSERT INTO outbox (category, topic, key, type, payload, headers, status, attempts)
        VALUES (:category, :topic, :key, :type, :payload::jsonb, :headers::jsonb, 'NEW', 0)
        RETURNING id
        """,
      nativeQuery = true)
  long insertReturningId(
      String category, String topic, String key, String type, String payload, String headers);

  default long insertReturningId(OutboxRow row) {
    String headersJson =
        row.headers() != null && !row.headers().isEmpty() ? toJson(row.headers()) : "{}";
    return insertReturningId(
        row.category(), row.topic(), row.key(), row.type(), row.payload(), headersJson);
  }

  private static String toJson(Map<String, String> map) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
    } catch (Exception e) {
      return "{}";
    }
  }
}
