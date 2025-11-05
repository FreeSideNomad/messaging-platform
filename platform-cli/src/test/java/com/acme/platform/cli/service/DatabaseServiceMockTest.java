package com.acme.platform.cli.service;

import com.acme.platform.cli.model.PaginatedResult;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseServiceMockTest {

    @Mock
    private HikariDataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private ResultSetMetaData metaData;

    private DatabaseService databaseService;

    @BeforeEach
    void setUp() throws Exception {
        databaseService = DatabaseService.getInstance();

        // Use reflection to inject mock dataSource
        Field dataSourceField = DatabaseService.class.getDeclaredField("dataSource");
        dataSourceField.setAccessible(true);
        dataSourceField.set(databaseService, dataSource);
    }

    @Test
    void testQueryTable_returnsValidPaginatedResult() throws Exception {
        // Arrange
        String tableName = "users";
        int page = 1;
        int pageSize = 10;

        // Mock count query
        ResultSet countRs = mock(ResultSet.class);
        when(countRs.next()).thenReturn(true);
        when(countRs.getLong(1)).thenReturn(25L);

        // Mock data query
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnName(1)).thenReturn("id");
        when(metaData.getColumnName(2)).thenReturn("name");

        when(resultSet.getMetaData()).thenReturn(metaData);
        when(resultSet.next()).thenReturn(true, true, false); // 2 rows
        when(resultSet.getObject(1)).thenReturn(1, 2);
        when(resultSet.getObject(2)).thenReturn("Alice", "Bob");

        Statement countStmt = mock(Statement.class);
        when(countStmt.executeQuery(anyString())).thenReturn(countRs);

        when(statement.executeQuery(anyString())).thenReturn(resultSet);

        Connection conn1 = mock(Connection.class);
        Connection conn2 = mock(Connection.class);
        when(conn1.createStatement()).thenReturn(countStmt);
        when(conn2.createStatement()).thenReturn(statement);

        when(dataSource.getConnection()).thenReturn(conn1, conn2);

        // Act
        PaginatedResult result = databaseService.queryTable(tableName, page, pageSize);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getPagination().getPage()).isEqualTo(1);
        assertThat(result.getPagination().getPageSize()).isEqualTo(10);
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(25);
        assertThat(result.getPagination().getTotalPages()).isEqualTo(3);

        Map<String, Object> firstRow = result.getData().get(0);
        assertThat(firstRow.get("id")).isEqualTo(1);
        assertThat(firstRow.get("name")).isEqualTo("Alice");
    }

    @Test
    void testQueryTable_withInvalidTableName_throwsException() {
        // Act & Assert
        assertThatThrownBy(() -> databaseService.queryTable("users; DROP TABLE", 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid table name");

        assertThatThrownBy(() -> databaseService.queryTable("users--comment", 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid table name");

        assertThatThrownBy(() -> databaseService.queryTable("users' OR '1'='1", 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid table name");
    }


    @Test
    void testListTables_returnsTableNames() throws Exception {
        // Arrange
        when(resultSet.next()).thenReturn(true, true, true, false); // 3 tables
        when(resultSet.getString("tablename")).thenReturn("users", "orders", "products");

        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(connection.createStatement()).thenReturn(statement);
        when(dataSource.getConnection()).thenReturn(connection);

        // Act
        List<String> tables = databaseService.listTables();

        // Assert
        assertThat(tables).hasSize(3);
        assertThat(tables).containsExactly("users", "orders", "products");
    }

    @Test
    void testListTables_withEmptyDatabase_returnsEmptyList() throws Exception {
        // Arrange
        when(resultSet.next()).thenReturn(false); // No tables
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(connection.createStatement()).thenReturn(statement);
        when(dataSource.getConnection()).thenReturn(connection);

        // Act
        List<String> tables = databaseService.listTables();

        // Assert
        assertThat(tables).isEmpty();
    }

    @Test
    void testGetTableInfo_returnsCompleteInformation() throws Exception {
        // Arrange
        String tableName = "users";

        // Mock count query
        ResultSet countRs = mock(ResultSet.class);
        when(countRs.next()).thenReturn(true);
        when(countRs.getLong("count")).thenReturn(100L);

        // Mock columns query
        ResultSet columnsRs = mock(ResultSet.class);
        when(columnsRs.next()).thenReturn(true, true, false); // 2 columns
        when(columnsRs.getString("column_name")).thenReturn("id", "name");
        when(columnsRs.getString("data_type")).thenReturn("integer", "character varying");
        when(columnsRs.getString("is_nullable")).thenReturn("NO", "YES");

        Statement countStmt = mock(Statement.class);
        Statement columnsStmt = mock(Statement.class);
        when(countStmt.executeQuery(anyString())).thenReturn(countRs);
        when(columnsStmt.executeQuery(anyString())).thenReturn(columnsRs);

        Connection conn1 = mock(Connection.class);
        Connection conn2 = mock(Connection.class);
        when(conn1.createStatement()).thenReturn(countStmt);
        when(conn2.createStatement()).thenReturn(columnsStmt);

        when(dataSource.getConnection()).thenReturn(conn1, conn2);

        // Act
        Map<String, Object> info = databaseService.getTableInfo(tableName);

        // Assert
        assertThat(info).isNotNull();
        assertThat(info.get("rowCount")).isEqualTo(100L);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> columns = (List<Map<String, String>>) info.get("columns");
        assertThat(columns).hasSize(2);

        assertThat(columns.get(0).get("name")).isEqualTo("id");
        assertThat(columns.get(0).get("type")).isEqualTo("integer");
        assertThat(columns.get(0).get("nullable")).isEqualTo("NO");

        assertThat(columns.get(1).get("name")).isEqualTo("name");
        assertThat(columns.get(1).get("type")).isEqualTo("character varying");
        assertThat(columns.get(1).get("nullable")).isEqualTo("YES");
    }

    @Test
    void testGetTableInfo_withInvalidTableName_throwsException() {
        // Act & Assert
        assertThatThrownBy(() -> databaseService.getTableInfo("users; DROP TABLE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid table name");
    }

    @Test
    void testTestConnection_successful() throws Exception {
        // Arrange
        when(dataSource.getConnection()).thenReturn(connection);

        // Act & Assert
        assertThatCode(() -> databaseService.testConnection()).doesNotThrowAnyException();
        verify(dataSource).getConnection();
        verify(connection).close();
    }

    @Test
    void testTestConnection_withSQLException_throwsException() throws Exception {
        // Arrange
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        // Act & Assert
        assertThatThrownBy(() -> databaseService.testConnection())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Connection failed");
    }

    @Test
    void testClose_closesDataSource() {
        // Arrange
        when(dataSource.isClosed()).thenReturn(false);

        // Act
        databaseService.close();

        // Assert
        verify(dataSource).isClosed();
        verify(dataSource).close();
    }

    @Test
    void testClose_whenAlreadyClosed_doesNothing() {
        // Arrange
        when(dataSource.isClosed()).thenReturn(true);

        // Act
        databaseService.close();

        // Assert
        verify(dataSource).isClosed();
        verify(dataSource, never()).close();
    }

}
