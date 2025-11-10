package com.acme.reliable.persistence.jdbc;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javax.sql.DataSource;

/** H2-specific implementation of InboxRepository */
@Singleton
@Requires(property = "db.dialect", value = "H2")
public class H2InboxRepository extends JdbcInboxRepository {

  public H2InboxRepository(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  protected String getInsertIfAbsentSql() {
    return """
        INSERT INTO inbox (message_id, handler, processed_at)
        SELECT ?, ?, ?
        WHERE NOT EXISTS(SELECT 1 FROM inbox WHERE message_id = ? AND handler = ?)
        """;
  }
}
