package com.acme.reliable.persistence.jdbc;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * PostgreSQL-specific implementation of ProcessRepository
 */
@Singleton
@Requires(property = "db.dialect", value = "PostgreSQL")
public class PostgresProcessRepository extends JdbcProcessRepository {

    public PostgresProcessRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected String getInsertSql() {
        return """
                INSERT INTO platform.process_instance
                (process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """;
    }

    @Override
    protected String getFindByIdSql() {
        return """
                SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
                FROM platform.process_instance
                WHERE process_id = ?
                """;
    }

    @Override
    protected String getFindByStatusSql() {
        return """
                SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
                FROM platform.process_instance
                WHERE status = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
    }

    @Override
    protected String getFindByTypeAndStatusSql() {
        return """
                SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
                FROM platform.process_instance
                WHERE process_type = ? AND status = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
    }

    @Override
    protected String getUpdateSql() {
        return """
                UPDATE platform.process_instance
                SET status = ?, current_step = ?, data = ?::jsonb, retries = ?, updated_at = ?
                WHERE process_id = ?
                """;
    }

    @Override
    protected String getLogQuerySql() {
        return """
                SELECT process_id, seq, at, event
                FROM platform.process_log
                WHERE process_id = ?
                ORDER BY seq DESC
                LIMIT ?
                """;
    }

    @Override
    protected String getFindByBusinessKeySql() {
        return """
                SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
                FROM platform.process_instance
                WHERE process_type = ? AND business_key = ?
                """;
    }

    @Override
    protected String getInsertLogEntrySql() {
        return """
                INSERT INTO platform.process_log (process_id, event)
                VALUES (?, ?::jsonb)
                """;
    }
}
