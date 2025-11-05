package com.acme.reliable.persistence.jdbc;

import com.acme.reliable.persistence.jdbc.model.DlqEntity;
import com.acme.reliable.repository.DlqRepository;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcDlqRepository extends DlqRepository, GenericRepository<DlqEntity, UUID> {

  @Query(
      value =
          """
        INSERT INTO command_dlq (command_id, command_name, business_key, payload,
                                   failed_status, error_class, error_message, attempts, parked_by)
        VALUES (:commandId, :commandName, :businessKey, :payload::jsonb,
                :failedStatus, :errorClass, :errorMessage, :attempts, :parkedBy)
        """,
      nativeQuery = true)
  void insertDlqEntry(
      UUID commandId,
      String commandName,
      String businessKey,
      String payload,
      String failedStatus,
      String errorClass,
      String errorMessage,
      int attempts,
      String parkedBy);
}
