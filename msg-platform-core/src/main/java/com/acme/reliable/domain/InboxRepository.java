package com.acme.reliable.domain;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface InboxRepository extends CrudRepository<Inbox, Inbox.InboxId> {

    @Query(value = """
        INSERT INTO inbox (message_id, handler)
        VALUES (:messageId, :handler)
        ON CONFLICT DO NOTHING
        """,
        nativeQuery = true)
    int insertIfAbsent(String messageId, String handler);
}
