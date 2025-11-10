package com.acme.reliable.persistence.jdbc;

import com.acme.reliable.repository.CommandRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javax.sql.DataSource;

/** PostgreSQL-specific implementation of CommandRepository */
@Singleton
@Requires(property = "db.dialect", value = "PostgreSQL")
public class PostgresCommandRepository extends JdbcCommandRepository implements CommandRepository {

  public PostgresCommandRepository(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  protected String getInsertPendingSql() {
    return """
        INSERT INTO command
        (id, name, business_key, payload, idempotency_key, status, retries, requested_at, reply)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
  }

  @Override
  protected String getFindByIdSql() {
    return """
        SELECT id, name, business_key, payload, idempotency_key, status, requested_at, updated_at,
               retries, processing_lease_until, last_error, reply
        FROM command
        WHERE id = ?
        """;
  }

  @Override
  protected String getUpdateToRunningSql() {
    return """
        UPDATE command
        SET status = ?, processing_lease_until = ?, updated_at = ?
        WHERE id = ?
        """;
  }

  @Override
  protected String getUpdateToSucceededSql() {
    return """
        UPDATE command
        SET status = ?, updated_at = ?
        WHERE id = ?
        """;
  }

  @Override
  protected String getUpdateToFailedSql() {
    return """
        UPDATE command
        SET status = ?, last_error = ?, updated_at = ?
        WHERE id = ?
        """;
  }

  @Override
  protected String getIncrementRetriesSql() {
    return """
        UPDATE command
        SET retries = retries + 1, last_error = ?, updated_at = ?
        WHERE id = ?
        """;
  }

  @Override
  protected String getUpdateToTimedOutSql() {
    return """
        UPDATE command
        SET status = ?, last_error = ?, updated_at = ?
        WHERE id = ?
        """;
  }

  @Override
  protected String getExistsByIdempotencyKeySql() {
    return """
        SELECT COUNT(*) FROM command WHERE idempotency_key = ?
        """;
  }
}
