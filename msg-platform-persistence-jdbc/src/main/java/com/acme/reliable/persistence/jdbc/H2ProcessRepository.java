package com.acme.reliable.persistence.jdbc;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * H2-specific implementation of ProcessRepository
 */
@Singleton
@Requires(property = "db.dialect", value = "H2")
public class H2ProcessRepository extends JdbcProcessRepository {

    public H2ProcessRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected String getInsertSql() {
        return """
                INSERT INTO process_instance
                (process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
    }

    @Override
    protected String getFindByIdSql() {
        return """
                SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
                FROM process_instance
                WHERE process_id = ?
                """;
    }

    @Override
    protected String getFindByStatusSql() {
        return """
                SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
                FROM process_instance
                WHERE status = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
    }

    @Override
    protected String getFindByTypeAndStatusSql() {
        return """
                SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
                FROM process_instance
                WHERE process_type = ? AND status = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
    }

    @Override
    protected String getUpdateSql() {
        return """
                UPDATE process_instance
                SET status = ?, current_step = ?, data = ?, retries = ?, updated_at = ?
                WHERE process_id = ?
                """;
    }

    @Override
    protected String getLogQuerySql() {
        return """
                SELECT process_id, seq, at, event
                FROM process_log
                WHERE process_id = ?
                ORDER BY seq DESC
                LIMIT ?
                """;
    }

    @Override
    protected String getFindByBusinessKeySql() {
        return """
                SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
                FROM process_instance
                WHERE process_type = ? AND business_key = ?
                """;
    }

    @Override
    protected String getInsertLogEntrySql() {
        return """
                INSERT INTO process_log (process_id, event)
                VALUES (?, ?)
                """;
    }
}
