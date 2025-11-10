package com.acme.reliable.persistence.jdbc;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * PostgreSQL-specific implementation of InboxRepository
 */
@Singleton
@Requires(property = "db.dialect", value = "PostgreSQL")
public class PostgresInboxRepository extends JdbcInboxRepository {

    public PostgresInboxRepository(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected String getInsertIfAbsentSql() {
        return """
                INSERT INTO inbox (message_id, handler, processed_at)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """;
    }
}
