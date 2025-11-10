package com.acme.reliable.persistence.jdbc;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * H2-specific implementation of DlqRepository
 */
@Singleton
@Requires(property = "db.dialect", value = "H2")
public class H2DlqRepository extends JdbcDlqRepository {

    public H2DlqRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected String getInsertDlqEntrySql() {
        return """
                INSERT INTO command_dlq
                (id, command_id, command_name, business_key, payload, failed_status, error_class, error_message, attempts, parked_by, parked_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
    }
}
