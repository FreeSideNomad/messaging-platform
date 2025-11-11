package com.acme.reliable.persistence.jdbc;

import com.acme.reliable.repository.OutboxRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * PostgreSQL-specific implementation of OutboxRepository using PostgreSQL dialect.
 * Uses PostgreSQL-specific SQL features like RETURNING clause.
 */
@Singleton
@Requires(property = "db.dialect", value = "PostgreSQL")
public class PostgresOutboxRepository extends JdbcOutboxRepository implements OutboxRepository {

    public PostgresOutboxRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected String getInsertSql() {
        return """
                INSERT INTO platform.outbox
                (id, category, topic, key, type, payload, headers, status, attempts, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """;
    }

    @Override
    protected String getClaimIfNewSql() {
        return """
                UPDATE platform.outbox
                SET status = 'CLAIMED'
                WHERE id = ? AND status = 'NEW'
                RETURNING id, category, topic, key, type, payload, headers, status, attempts,
                          next_at, claimed_by, created_at, published_at, last_error
                """;
    }

    @Override
    protected String getSweepBatchSql() {
        return """
                WITH available AS (
                  SELECT id
                  FROM platform.outbox
                  WHERE (status = 'NEW' OR (status = 'CLAIMED' AND created_at < now() - interval '5 minutes'))
                    AND (next_at IS NULL OR next_at <= now())
                  ORDER BY created_at ASC
                  LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE platform.outbox o
                SET status = 'CLAIMED'
                FROM available
                WHERE o.id = available.id
                RETURNING o.id, o.category, o.topic, o.key, o.type, o.payload, o.headers, o.status, o.attempts,
                          o.next_at, o.claimed_by, o.created_at, o.published_at, o.last_error
                """;
    }

    @Override
    protected String getMarkPublishedSql() {
        return """
                UPDATE platform.outbox
                SET status = 'PUBLISHED', published_at = ?
                WHERE id = ?
                """;
    }

    @Override
    protected String getMarkFailedSql() {
        return """
                UPDATE platform.outbox
                SET last_error = ?, next_at = ?
                WHERE id = ?
                """;
    }

    @Override
    protected String getRescheduleSql() {
        return """
                UPDATE platform.outbox
                SET next_at = ?, last_error = ?
                WHERE id = ?
                """;
    }

    @Override
    protected String getRecoverStuckSql() {
        return """
                UPDATE platform.outbox
                SET status = 'NEW', next_at = NULL
                WHERE status = 'CLAIMED' AND created_at < ?
                """;
    }
}
