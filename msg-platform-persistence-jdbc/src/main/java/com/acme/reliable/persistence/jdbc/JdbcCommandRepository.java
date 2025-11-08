package com.acme.reliable.persistence.jdbc;

import com.acme.reliable.persistence.jdbc.model.CommandEntity;
import com.acme.reliable.repository.CommandRepository;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcCommandRepository
    extends CommandRepository, GenericRepository<CommandEntity, UUID> {

  @Query(
      value =
          """
        INSERT INTO platform.command (id, name, business_key, payload, idempotency_key, status, reply)
        VALUES (:id, :name, :businessKey, :payload::jsonb, :idempotencyKey, 'PENDING', :reply::jsonb)
        """,
      nativeQuery = true)
  void insertPending(
      UUID id,
      String name,
      String businessKey,
      String payload,
      String idempotencyKey,
      String reply);

  Optional<CommandEntity> findById(UUID id);

  @Query(
      value =
          """
        UPDATE platform.command
        SET status = 'RUNNING', processing_lease_until = :lease
        WHERE id = :id
        """,
      nativeQuery = true)
  void updateToRunning(UUID id, Timestamp lease);

  @Query(
      value =
          """
        UPDATE platform.command
        SET status = 'SUCCEEDED', updated_at = now()
        WHERE id = :id
        """,
      nativeQuery = true)
  void updateToSucceeded(UUID id);

  @Query(
      value =
          """
        UPDATE platform.command
        SET status = 'FAILED', last_error = :error, updated_at = now()
        WHERE id = :id
        """,
      nativeQuery = true)
  void updateToFailed(UUID id, String error);

  @Query(
      value =
          """
        UPDATE platform.command
        SET retries = retries + 1, last_error = :error, updated_at = now()
        WHERE id = :id
        """,
      nativeQuery = true)
  void incrementRetries(UUID id, String error);

  @Query(
      value =
          """
        UPDATE platform.command
        SET status = 'TIMED_OUT', last_error = :reason, updated_at = now()
        WHERE id = :id
        """,
      nativeQuery = true)
  void updateToTimedOut(UUID id, String reason);

  @Query(
      value = "SELECT EXISTS(SELECT 1 FROM platform.command WHERE idempotency_key = :key)",
      nativeQuery = true)
  boolean existsByIdempotencyKey(String key);
}
