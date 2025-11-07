package com.acme.reliable.persistence.jdbc.service;

import com.acme.reliable.persistence.jdbc.JdbcOutboxDao;
import com.acme.reliable.spi.OutboxRow;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class JdbcOutboxDaoService {

  private final JdbcOutboxDao dao;

  public JdbcOutboxDaoService(JdbcOutboxDao dao) {
    this.dao = dao;
  }

  @Transactional
  public long insertReturningId(OutboxRow row) {
    return dao.insertReturningId(row);
  }

  public long insertReturningId(
      String category,
      String topic,
      String key,
      String type,
      String payload,
      Map<String, String> headers) {
    return dao.insertReturningId(new OutboxRow(0, category, topic, key, type, payload, headers, 0));
  }
}
