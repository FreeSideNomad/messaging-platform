package com.acme.reliable.persistence.jdbc.process;

import com.acme.reliable.core.Jsons;
import com.acme.reliable.process.*;
import com.acme.reliable.repository.ProcessRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JDBC implementation of ProcessRepository with full CRUD operations */
@Singleton
public class JdbcProcessRepository implements ProcessRepository {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcProcessRepository.class);

  private final DataSource dataSource;

  public JdbcProcessRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  @Transactional
  public void insert(ProcessInstance instance, ProcessEvent initialEvent) {
    try (Connection conn = dataSource.getConnection()) {
      // Insert process_instance
      String insertSql =
          """
                INSERT INTO process_instance
                (process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """;

      try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
        ps.setObject(1, instance.processId());
        ps.setString(2, instance.processType());
        ps.setString(3, instance.businessKey());
        ps.setString(4, instance.status().name());
        ps.setString(5, instance.currentStep());
        ps.setString(6, Jsons.toJson(instance.data()));
        ps.setInt(7, instance.retries());
        ps.setTimestamp(8, Timestamp.from(instance.createdAt()));
        ps.setTimestamp(9, Timestamp.from(instance.updatedAt()));
        ps.executeUpdate();
      }

      // Insert initial event to process_log
      insertLogEntry(conn, instance.processId(), initialEvent);

      LOG.info(
          "Inserted process instance: {} type={} key={}",
          instance.processId(),
          instance.processType(),
          instance.businessKey());

    } catch (SQLException e) {
      LOG.error("Failed to insert process instance: {}", instance.processId(), e);
      throw new RuntimeException("Failed to insert process instance", e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ProcessInstance> findById(UUID processId) {
    String sql =
        """
            SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
            FROM process_instance
            WHERE process_id = ?
            """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setObject(1, processId);

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapResultSetToInstance(rs));
        }
      }

      return Optional.empty();

    } catch (SQLException e) {
      LOG.error("Failed to find process by ID: {}", processId, e);
      throw new RuntimeException("Failed to find process", e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProcessInstance> findByStatus(ProcessStatus status, int limit) {
    String sql =
        """
            SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
            FROM process_instance
            WHERE status = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;

    return findByQuery(sql, status.name(), limit);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProcessInstance> findByTypeAndStatus(
      String processType, ProcessStatus status, int limit) {
    String sql =
        """
            SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
            FROM process_instance
            WHERE process_type = ? AND status = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, processType);
      ps.setString(2, status.name());
      ps.setInt(3, limit);

      List<ProcessInstance> instances = new ArrayList<>();
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          instances.add(mapResultSetToInstance(rs));
        }
      }

      return instances;

    } catch (SQLException e) {
      LOG.error("Failed to find by type and status: {} {}", processType, status, e);
      throw new RuntimeException("Failed to find processes", e);
    }
  }

  @Override
  @Transactional
  public void update(ProcessInstance instance, ProcessEvent event) {
    try (Connection conn = dataSource.getConnection()) {
      // Update process_instance
      String updateSql =
          """
                UPDATE process_instance
                SET status = ?,
                    current_step = ?,
                    data = ?::jsonb,
                    retries = ?,
                    updated_at = ?
                WHERE process_id = ?
                """;

      try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
        ps.setString(1, instance.status().name());
        ps.setString(2, instance.currentStep());
        ps.setString(3, Jsons.toJson(instance.data()));
        ps.setInt(4, instance.retries());
        ps.setTimestamp(5, Timestamp.from(instance.updatedAt()));
        ps.setObject(6, instance.processId());

        int updated = ps.executeUpdate();
        if (updated == 0) {
          LOG.warn("No rows updated for process: {}", instance.processId());
        }
      }

      // Insert event to process_log
      insertLogEntry(conn, instance.processId(), event);

      LOG.debug("Updated process instance: {} status={}", instance.processId(), instance.status());

    } catch (SQLException e) {
      LOG.error("Failed to update process instance: {}", instance.processId(), e);
      throw new RuntimeException("Failed to update process instance", e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProcessLogEntry> getLog(UUID processId) {
    return getLog(processId, Integer.MAX_VALUE);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProcessLogEntry> getLog(UUID processId, int limit) {
    String sql =
        """
            SELECT process_id, seq, at, event
            FROM process_log
            WHERE process_id = ?
            ORDER BY seq
            LIMIT ?
            """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setObject(1, processId);
      ps.setInt(2, limit);

      List<ProcessLogEntry> entries = new ArrayList<>();
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          entries.add(mapResultSetToLogEntry(rs));
        }
      }

      return entries;

    } catch (SQLException e) {
      LOG.error("Failed to get log for process: {}", processId, e);
      throw new RuntimeException("Failed to get process log", e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ProcessInstance> findByBusinessKey(String processType, String businessKey) {
    String sql =
        """
            SELECT process_id, process_type, business_key, status, current_step, data, retries, created_at, updated_at
            FROM process_instance
            WHERE process_type = ? AND business_key = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, processType);
      ps.setString(2, businessKey);

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return Optional.of(mapResultSetToInstance(rs));
        }
      }

      return Optional.empty();

    } catch (SQLException e) {
      LOG.error("Failed to find by business key: {} {}", processType, businessKey, e);
      throw new RuntimeException("Failed to find process", e);
    }
  }

  // Helper methods

  private List<ProcessInstance> findByQuery(String sql, String statusName, int limit) {
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, statusName);
      ps.setInt(2, limit);

      List<ProcessInstance> instances = new ArrayList<>();
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          instances.add(mapResultSetToInstance(rs));
        }
      }

      return instances;

    } catch (SQLException e) {
      LOG.error("Failed to execute query", e);
      throw new RuntimeException("Failed to find processes", e);
    }
  }

  private void insertLogEntry(Connection conn, UUID processId, ProcessEvent event)
      throws SQLException {
    String sql =
        """
            INSERT INTO process_log (process_id, event)
            VALUES (?, ?::jsonb)
            """;

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setObject(1, processId);
      ps.setString(2, Jsons.toJson(event));
      ps.executeUpdate();
    }
  }

  private ProcessInstance mapResultSetToInstance(ResultSet rs) throws SQLException {
    UUID processId = (UUID) rs.getObject("process_id");
    String processType = rs.getString("process_type");
    String businessKey = rs.getString("business_key");
    ProcessStatus status = ProcessStatus.valueOf(rs.getString("status"));
    String currentStep = rs.getString("current_step");
    String dataJson = rs.getString("data");
    int retries = rs.getInt("retries");
    Instant createdAt = rs.getTimestamp("created_at").toInstant();
    Instant updatedAt = rs.getTimestamp("updated_at").toInstant();

    @SuppressWarnings("unchecked")
    Map<String, Object> data = Jsons.fromJson(dataJson, Map.class);

    return new ProcessInstance(
        processId,
        processType,
        businessKey,
        status,
        currentStep,
        data,
        retries,
        createdAt,
        updatedAt);
  }

  private ProcessLogEntry mapResultSetToLogEntry(ResultSet rs) throws SQLException {
    UUID processId = (UUID) rs.getObject("process_id");
    long seq = rs.getLong("seq");
    Instant at = rs.getTimestamp("at").toInstant();
    String eventJson = rs.getString("event");

    ProcessEvent event = Jsons.fromJson(eventJson, ProcessEvent.class);

    return new ProcessLogEntry(processId, seq, at, event);
  }
}
