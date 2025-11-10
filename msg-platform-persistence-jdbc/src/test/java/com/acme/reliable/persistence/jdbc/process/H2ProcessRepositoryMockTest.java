package com.acme.reliable.persistence.jdbc.process;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.acme.reliable.persistence.jdbc.JdbcProcessRepository;
import com.acme.reliable.process.ProcessEvent;
import com.acme.reliable.process.ProcessInstance;
import com.acme.reliable.process.ProcessStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mock-based tests for comprehensive branch coverage of JdbcProcessRepository.
 * Uses Mockito to simulate database failures and edge cases that are hard to trigger naturally.
 */
@DisplayName("JdbcProcessRepository Mock-Based Branch Coverage Tests")
class H2ProcessRepositoryMockTest {

  private JdbcProcessRepository createMockRepository(DataSource mockDataSource) {
    return new JdbcProcessRepository(mockDataSource) {
      @Override
      protected String getInsertSql() {
        return "INSERT INTO process_instance (id, process_type, business_key, status, current_step, data, retries, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
      }

      @Override
      protected String getFindByIdSql() {
        return "SELECT id, process_type, business_key, status, current_step, data, retries, created_at, updated_at FROM process_instance WHERE id = ?";
      }

      @Override
      protected String getFindByStatusSql() {
        return "SELECT id, process_type, business_key, status, current_step, data, retries, created_at, updated_at FROM process_instance WHERE status = ? LIMIT ?";
      }

      @Override
      protected String getFindByTypeAndStatusSql() {
        return "SELECT id, process_type, business_key, status, current_step, data, retries, created_at, updated_at FROM process_instance WHERE process_type = ? AND status = ? LIMIT ?";
      }

      @Override
      protected String getUpdateSql() {
        return "UPDATE process_instance SET status = ?, current_step = ?, data = ?, retries = ?, updated_at = ? WHERE id = ?";
      }

      @Override
      protected String getLogQuerySql() {
        return "SELECT id, process_id, event_type, event_data, created_at FROM process_log WHERE process_id = ? ORDER BY created_at ASC";
      }

      @Override
      protected String getFindByBusinessKeySql() {
        return "SELECT id, process_type, business_key, status, current_step, data, retries, created_at, updated_at FROM process_instance WHERE process_type = ? AND business_key = ?";
      }

      @Override
      protected String getInsertLogEntrySql() {
        return "INSERT INTO process_log (process_id, event_type, event_data, created_at) VALUES (?, ?, ?, ?)";
      }
    };
  }

  @Nested
  @DisplayName("findById Exception and Edge Case Branches")
  class FindByIdTests {

    @Test
    @DisplayName("findById should handle SQLException on query")
    void testFindByIdQueryException() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenThrow(new SQLException("Query failed"));

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      assertThatThrownBy(() -> repository.findById(UUID.randomUUID()))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("find process by ID");
    }

    @Test
    @DisplayName("findById should handle empty ResultSet (rs.next() false)")
    void testFindByIdEmptyResult() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);
      ResultSet mockRs = mock(ResultSet.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenReturn(mockRs);
      when(mockRs.next()).thenReturn(false);

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      Optional<ProcessInstance> result = repository.findById(UUID.randomUUID());

      assertThat(result).isEmpty();
    }

  }

  @Nested
  @DisplayName("findByBusinessKey Exception Branches")
  class FindByBusinessKeyTests {

    @Test
    @DisplayName("findByBusinessKey should handle SQLException")
    void testFindByBusinessKeyException() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenThrow(new SQLException("Query failed"));

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      assertThatThrownBy(() -> repository.findByBusinessKey("Order", "order-123"))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("find process by business key");
    }

    @Test
    @DisplayName("findByBusinessKey should return empty on no results")
    void testFindByBusinessKeyEmptyResult() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);
      ResultSet mockRs = mock(ResultSet.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenReturn(mockRs);
      when(mockRs.next()).thenReturn(false);

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      Optional<ProcessInstance> result = repository.findByBusinessKey("Order", "nonexistent");

      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByStatus Exception Branches")
  class FindByStatusTests {

    @Test
    @DisplayName("findByStatus should handle SQLException")
    void testFindByStatusException() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenThrow(new SQLException("Query failed"));

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      assertThatThrownBy(() -> repository.findByStatus(ProcessStatus.RUNNING, 10))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("find processes by status");
    }

    @Test
    @DisplayName("findByStatus should return empty list on no results")
    void testFindByStatusEmptyResult() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);
      ResultSet mockRs = mock(ResultSet.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenReturn(mockRs);
      when(mockRs.next()).thenReturn(false);

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      var results = repository.findByStatus(ProcessStatus.FAILED, 10);

      assertThat(results).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByTypeAndStatus Exception Branches")
  class FindByTypeAndStatusTests {

    @Test
    @DisplayName("findByTypeAndStatus should handle SQLException")
    void testFindByTypeAndStatusException() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenThrow(new SQLException("Query failed"));

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      assertThatThrownBy(() -> repository.findByTypeAndStatus("Order", ProcessStatus.RUNNING, 10))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("find processes by type and status");
    }

    @Test
    @DisplayName("findByTypeAndStatus should return empty list on no results")
    void testFindByTypeAndStatusEmptyResult() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);
      ResultSet mockRs = mock(ResultSet.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenReturn(mockRs);
      when(mockRs.next()).thenReturn(false);

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      var results = repository.findByTypeAndStatus("Payment", ProcessStatus.SUCCEEDED, 10);

      assertThat(results).isEmpty();
    }
  }

  @Nested
  @DisplayName("update Exception Branches")
  class UpdateTests {

    @Test
    @DisplayName("update should handle SQLException on connection")
    void testUpdateConnectionException() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      UUID processId = UUID.randomUUID();
      ProcessInstance instance =
          new ProcessInstance(
              processId,
              "Order",
              "order-1",
              ProcessStatus.RUNNING,
              "step1",
              new HashMap<>(),
              0,
              java.time.Instant.now(),
              java.time.Instant.now());

      ProcessEvent event = new ProcessEvent.StepCompleted("step1", "result", new HashMap<>());

      assertThatThrownBy(() -> repository.update(instance, event))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("update should handle SQLException on executeUpdate")
    void testUpdateExecuteException() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeUpdate()).thenThrow(new SQLException("Update failed"));

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      UUID processId = UUID.randomUUID();
      ProcessInstance instance =
          new ProcessInstance(
              processId,
              "Order",
              "order-1",
              ProcessStatus.RUNNING,
              "step1",
              new HashMap<>(),
              0,
              java.time.Instant.now(),
              java.time.Instant.now());

      ProcessEvent event = new ProcessEvent.StepCompleted("step1", "result", new HashMap<>());

      assertThatThrownBy(() -> repository.update(instance, event))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("update with 0 rows affected should be handled gracefully")
    void testUpdateZeroRowsAffected() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeUpdate()).thenReturn(0);

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      UUID processId = UUID.randomUUID();
      ProcessInstance instance =
          new ProcessInstance(
              processId,
              "Order",
              "order-1",
              ProcessStatus.RUNNING,
              "step1",
              new HashMap<>(),
              0,
              java.time.Instant.now(),
              java.time.Instant.now());

      ProcessEvent event = new ProcessEvent.StepCompleted("step1", "result", new HashMap<>());

      assertThatCode(() -> repository.update(instance, event))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("update with >0 rows affected should complete successfully")
    void testUpdateWithRowsAffected() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeUpdate())
          .thenReturn(1)  // First call (main update)
          .thenReturn(1); // Second call (log entry)

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      UUID processId = UUID.randomUUID();
      ProcessInstance instance =
          new ProcessInstance(
              processId,
              "Order",
              "order-1",
              ProcessStatus.RUNNING,
              "step1",
              new HashMap<>(),
              0,
              java.time.Instant.now(),
              java.time.Instant.now());

      ProcessEvent event = new ProcessEvent.StepCompleted("step1", "result", new HashMap<>());

      assertThatCode(() -> repository.update(instance, event))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("getLog Exception Branches")
  class GetLogTests {

    @Test
    @DisplayName("getLog should handle SQLException")
    void testGetLogException() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenThrow(new SQLException("Query failed"));

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      assertThatThrownBy(() -> repository.getLog(UUID.randomUUID()))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("getLog should return empty list on no results")
    void testGetLogEmptyResult() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);
      ResultSet mockRs = mock(ResultSet.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenReturn(mockRs);
      when(mockRs.next()).thenReturn(false);

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      var log = repository.getLog(UUID.randomUUID());

      assertThat(log).isEmpty();
    }
  }

  @Nested
  @DisplayName("insert Exception Branches")
  class InsertTests {

    @Test
    @DisplayName("insert should handle SQLException on connection")
    void testInsertConnectionException() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      UUID processId = UUID.randomUUID();
      ProcessInstance instance = ProcessInstance.create(processId, "Order", "order-1", "step1", new HashMap<>());
      ProcessEvent event = new ProcessEvent.ProcessStarted("Order", "order-1", new HashMap<>());

      assertThatThrownBy(() -> repository.insert(instance, event))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("insert should handle SQLException on executeUpdate")
    void testInsertExecuteException() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeUpdate()).thenThrow(new SQLException("Insert failed"));

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      UUID processId = UUID.randomUUID();
      ProcessInstance instance = ProcessInstance.create(processId, "Order", "order-1", "step1", new HashMap<>());
      ProcessEvent event = new ProcessEvent.ProcessStarted("Order", "order-1", new HashMap<>());

      assertThatThrownBy(() -> repository.insert(instance, event))
          .isInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("Result Set Mapping with Minimal Data")
  class ResultSetMappingTests {

    @Test
    @DisplayName("findById should handle minimal data in ResultSet")
    void testFindByIdMinimalData() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);
      ResultSet mockRs = mock(ResultSet.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenReturn(mockRs);
      when(mockRs.next()).thenReturn(true);

      when(mockRs.getObject("id")).thenReturn(UUID.randomUUID());
      when(mockRs.getString("process_type")).thenReturn("Order");
      when(mockRs.getString("business_key")).thenReturn("order-1");
      when(mockRs.getString("status")).thenReturn("RUNNING");
      when(mockRs.getString("current_step")).thenReturn("step1");
      when(mockRs.getString("data")).thenReturn("{}");
      when(mockRs.getInt("retries")).thenReturn(0);
      when(mockRs.getTimestamp("created_at")).thenReturn(mock());
      when(mockRs.getTimestamp("updated_at")).thenReturn(mock());

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      assertThatCode(() -> repository.findById(UUID.randomUUID()))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("findByBusinessKey should handle minimal data")
    void testFindByBusinessKeyMinimalData() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);
      ResultSet mockRs = mock(ResultSet.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      when(mockPs.executeQuery()).thenReturn(mockRs);
      when(mockRs.next()).thenReturn(true);

      when(mockRs.getObject("id")).thenReturn(UUID.randomUUID());
      when(mockRs.getString("process_type")).thenReturn("Order");
      when(mockRs.getString("business_key")).thenReturn("order-1");
      when(mockRs.getString("status")).thenReturn("NEW");
      when(mockRs.getString("current_step")).thenReturn("start");
      when(mockRs.getString("data")).thenReturn("{}");
      when(mockRs.getInt("retries")).thenReturn(0);
      when(mockRs.getTimestamp("created_at")).thenReturn(mock());
      when(mockRs.getTimestamp("updated_at")).thenReturn(mock());

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      assertThatCode(() -> repository.findByBusinessKey("Order", "order-1"))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("Query Parameter Binding Edge Cases")
  class ParameterBindingTests {

    @Test
    @DisplayName("findByStatus should handle SQLException during parameter binding")
    void testFindByStatusParameterBindingException() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      doThrow(new SQLException("Parameter binding failed")).when(mockPs).setString(anyInt(), anyString());

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      assertThatThrownBy(() -> repository.findByStatus(ProcessStatus.RUNNING, 10))
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("update should handle SQLException during parameter binding")
    void testUpdateParameterBindingException() throws SQLException {
      DataSource mockDataSource = mock(DataSource.class);
      Connection mockConnection = mock(Connection.class);
      PreparedStatement mockPs = mock(PreparedStatement.class);

      when(mockDataSource.getConnection()).thenReturn(mockConnection);
      when(mockConnection.prepareStatement(anyString())).thenReturn(mockPs);
      doThrow(new SQLException("Binding failed")).when(mockPs).setObject(anyInt(), any());

      JdbcProcessRepository repository = createMockRepository(mockDataSource);

      UUID processId = UUID.randomUUID();
      ProcessInstance instance =
          new ProcessInstance(
              processId,
              "Order",
              "order-1",
              ProcessStatus.RUNNING,
              "step1",
              new HashMap<>(),
              0,
              java.time.Instant.now(),
              java.time.Instant.now());

      ProcessEvent event = new ProcessEvent.StepCompleted("step1", "result", new HashMap<>());

      assertThatThrownBy(() -> repository.update(instance, event))
          .isInstanceOf(RuntimeException.class);
    }
  }
}
