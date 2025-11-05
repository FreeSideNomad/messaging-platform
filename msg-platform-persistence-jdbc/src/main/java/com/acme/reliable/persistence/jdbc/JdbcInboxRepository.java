package com.acme.reliable.persistence.jdbc;

import com.acme.reliable.persistence.jdbc.model.InboxEntity;
import com.acme.reliable.repository.InboxRepository;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcInboxRepository
    extends InboxRepository, GenericRepository<InboxEntity, InboxEntity.InboxId> {

  @Query(
      value =
          """
        INSERT INTO inbox (message_id, handler)
        VALUES (:messageId, :handler)
        ON CONFLICT DO NOTHING
        """,
      nativeQuery = true)
  int insertIfAbsent(String messageId, String handler);
}
