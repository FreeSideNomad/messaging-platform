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
        RETURNING id, category, topic, key, type, payload::text,
                  COALESCE((SELECT jsonb_object_agg(key, value) FROM jsonb_each_text(headers)), '{}'::jsonb)::text as headers_map,
                  attempts
        """,
      nativeQuery = true)
  Optional<OutboxRowDto> claimIfNewDto(long id);

  @Override
  default Optional<OutboxRow> claimIfNew(long id) {
    return claimIfNewDto(id)
        .map(
            dto ->
                new OutboxRow(
                    dto.id(),
                    dto.category(),
                    dto.topic(),
                    dto.key(),
                    dto.type(),
                    dto.payload(),
                    parseHeaders(dto.headersMap()),
                    dto.attempts()));
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
        RETURNING o.id, o.category, o.topic, o.key, o.type, o.payload::text,
                  COALESCE((SELECT jsonb_object_agg(key, value) FROM jsonb_each_text(o.headers)), '{}'::jsonb)::text as headers_map,
                  o.attempts
        """,
      nativeQuery = true)
  List<OutboxRowDto> sweepBatchDto(int max);

  @Override
  default List<OutboxRow> sweepBatch(int max) {
    return sweepBatchDto(max).stream()
        .map(
            dto ->
                new OutboxRow(
                    dto.id(),
                    dto.category(),
                    dto.topic(),
                    dto.key(),
                    dto.type(),
                    dto.payload(),
                    parseHeaders(dto.headersMap()),
                    dto.attempts()))
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

  private static Map<String, String> parseHeaders(String headersJson) {
    if (headersJson == null || headersJson.isEmpty() || headersJson.equals("{}")) {
      return Map.of();
    }
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper()
          .readValue(
              headersJson,
              new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }

  record OutboxRowDto(
      long id,
      String category,
      String topic,
      String key,
      String type,
      String payload,
      String headersMap,
      int attempts) {}
}
