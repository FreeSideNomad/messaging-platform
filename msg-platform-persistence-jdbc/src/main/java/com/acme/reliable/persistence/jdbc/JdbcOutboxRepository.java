package com.acme.reliable.persistence.jdbc;

import com.acme.reliable.persistence.jdbc.model.OutboxEntity;
import com.acme.reliable.repository.OutboxRepository;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcOutboxRepository extends OutboxRepository, GenericRepository<OutboxEntity, UUID> {

    @Query(value = """
        INSERT INTO outbox (id, category, topic, key, type, payload, headers, status, attempts)
        VALUES (:id, :category, :topic, :key, :type, :payload::jsonb, :headers::jsonb, :status, :attempts)
        """,
        nativeQuery = true)
    void insert(UUID id, String category, String topic, String key, String type,
                String payload, String headers, String status, int attempts);

    @Query(value = """
        UPDATE outbox
        SET status = 'CLAIMED', claimed_by = :claimer
        WHERE id = :id AND status = 'NEW'
        RETURNING id, category, topic, key, type, payload, headers, status, attempts,
                  next_at, claimed_by, created_at, published_at, last_error
        """,
        nativeQuery = true)
    Optional<OutboxEntity> claimOne(UUID id, String claimer);

    @Query(value = """
        WITH c AS (
            SELECT id
            FROM outbox
            WHERE status = 'NEW'
              AND (next_at IS NULL OR next_at <= now())
            ORDER BY created_at
            LIMIT :maxRecords
            FOR UPDATE SKIP LOCKED
        )
        UPDATE outbox o
        SET status = 'CLAIMED', claimed_by = :claimer, attempts = o.attempts
        FROM c
        WHERE o.id = c.id
        RETURNING o.id, o.category, o.topic, o.key, o.type, o.payload, o.headers,
                  o.status, o.attempts, o.next_at, o.claimed_by, o.created_at,
                  o.published_at, o.last_error
        """,
        nativeQuery = true)
    List<OutboxEntity> claimBatch(int maxRecords, String claimer);

    @Query(value = "UPDATE outbox SET status = 'PUBLISHED', published_at = now() WHERE id = :id",
           nativeQuery = true)
    void markPublished(UUID id);

    @Query(value = """
        UPDATE outbox
        SET status = 'NEW',
            next_at = now() + (:backoffMs || ' milliseconds')::interval,
            attempts = attempts + 1,
            last_error = :error
        WHERE id = :id
        """,
        nativeQuery = true)
    void reschedule(UUID id, long backoffMs, String error);
}
