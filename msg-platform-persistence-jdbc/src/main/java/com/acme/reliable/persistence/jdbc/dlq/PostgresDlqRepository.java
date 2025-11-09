package com.acme.reliable.persistence.jdbc.dlq;

import com.acme.reliable.repository.DlqRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javax.sql.DataSource;

/** PostgreSQL-specific implementation of DlqRepository */
@Singleton
@Requires(property = "db.dialect", value = "PostgreSQL")
public class PostgresDlqRepository extends JdbcDlqRepository {

  public PostgresDlqRepository(DataSource dataSource) {
    super(dataSource);
  }

  @Override
  protected String getInsertDlqEntrySql() {
    return """
        INSERT INTO dlq
        (id, command_id, command_name, business_key, payload, failed_status, error_class, error_message, attempts, parked_by, parked_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
  }
}
