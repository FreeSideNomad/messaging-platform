package com.acme.reliable.persistence.jdbc;

import static org.assertj.core.api.Assertions.*;

import com.acme.reliable.core.PermanentException;
import com.acme.reliable.core.TransientException;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comprehensive tests for ExceptionTranslator utility.
 * Tests translation of SQLException to PermanentException or TransientException.
 */
class ExceptionTranslatorTest {

  private static final Logger logger = LoggerFactory.getLogger(ExceptionTranslatorTest.class);

  @Nested
  @DisplayName("Transient Error Detection")
  class TransientErrorTests {

    @Test
    @DisplayName("should throw TransientException for connection timeout")
    void testConnectionTimeout() {
      // Given
      SQLException cause = new SQLException("Connection timeout", "08001");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "connect", logger);

      // Then
      assertThat(result).isInstanceOf(TransientException.class);
      assertThat(result.getMessage()).containsIgnoringCase("connection timeout");
    }

    @Test
    @DisplayName("should throw TransientException for connection refused")
    void testConnectionRefused() {
      // Given
      SQLException cause = new SQLException("Connection refused to host", "08001");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "connect", logger);

      // Then
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should throw TransientException for deadlock detected")
    void testDeadlockDetected() {
      // Given
      SQLException cause = new SQLException("Deadlock detected", "40P01");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "update", logger);

      // Then
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should throw TransientException for lock timeout")
    void testLockTimeout() {
      // Given
      SQLException cause = new SQLException("Lock wait timeout exceeded");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert", logger);

      // Then
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should throw TransientException for too many connections")
    void testTooManyConnections() {
      // Given
      SQLException cause = new SQLException("Too many connections", "08002");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);

      // Then
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should throw TransientException for connection pool exhausted")
    void testPoolExhausted() {
      // Given
      SQLException cause = new SQLException("Connection pool exhausted");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "select", logger);

      // Then
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should throw TransientException for transaction rollback state code")
    void testTransactionRollbackSQLState() {
      // Given - SQL State 40xxx = Transaction Rollback
      SQLException cause = new SQLException("Serialization failure", "40001");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "delete", logger);

      // Then
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should throw TransientException for connection exception SQL state")
    void testConnectionExceptionSQLState() {
      // Given - SQL State 08xxx = Connection Exception
      SQLException cause = new SQLException("Connection lost", "08006");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "update", logger);

      // Then
      assertThat(result).isInstanceOf(TransientException.class);
    }

  }

  @Nested
  @DisplayName("Permanent Error Detection")
  class PermanentErrorTests {

    @Test
    @DisplayName("should throw PermanentException for table not found")
    void testTableNotFound() {
      // Given
      SQLException cause = new SQLException("Table 'users' not found", "42P01");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "select", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
      assertThat(result.getMessage()).contains("Table 'users' not found");
    }

    @Test
    @DisplayName("should throw PermanentException for column not found")
    void testColumnNotFound() {
      // Given
      SQLException cause = new SQLException("Column 'invalid_col' not found", "42703");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should throw PermanentException for schema not found")
    void testSchemaNotFound() {
      // Given
      SQLException cause = new SQLException("Schema 'invalid_schema' does not exist", "3F000");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "update", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should throw PermanentException for syntax error")
    void testSyntaxError() {
      // Given
      SQLException cause = new SQLException("Syntax error in SQL statement", "42000");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should throw PermanentException for unique constraint violation")
    void testUniqueConstraintViolation() {
      // Given
      SQLException cause = new SQLException("Unique constraint violation", "23505");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should throw PermanentException for foreign key constraint violation")
    void testForeignKeyViolation() {
      // Given
      SQLException cause = new SQLException("Foreign key constraint violation", "23503");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "delete", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should throw PermanentException for data type mismatch")
    void testDataTypeMismatch() {
      // Given
      SQLException cause = new SQLException("Data type mismatch in assignment", "22000");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "update", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should throw PermanentException for integrity constraint violation SQL state")
    void testIntegrityConstraintViolationSQLState() {
      // Given - SQL State 23xxx = Integrity Constraint Violation
      SQLException cause = new SQLException("Constraint violation", "23000");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should throw PermanentException for syntax error SQL state")
    void testSyntaxErrorSQLState() {
      // Given - SQL State 42xxx = Syntax Error / Access Violation
      SQLException cause = new SQLException("Syntax error", "42000");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should throw PermanentException for invalid catalog name SQL state")
    void testInvalidCatalogNameSQLState() {
      // Given - SQL State 3D000 = Invalid Catalog Name
      SQLException cause = new SQLException("Invalid database", "3D000");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "connect", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should throw PermanentException for invalid schema name SQL state")
    void testInvalidSchemaNameSQLState() {
      // Given - SQL State 3F000 = Invalid Schema Name
      SQLException cause = new SQLException("Invalid schema", "3F000");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "connect", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
    }

  }

  @Nested
  @DisplayName("Default Transient Behavior")
  class DefaultTransientTests {

    @Test
    @DisplayName("should throw TransientException for unknown SQL error (default behavior)")
    void testUnknownErrorDefaultsToTransient() {
      // Given - Error that doesn't match permanent or transient patterns
      SQLException cause = new SQLException("Unknown database error");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);

      // Then - Should default to TransientException when in doubt
      assertThat(result).isInstanceOf(TransientException.class);
      assertThat(result.getMessage()).contains("Database error");
    }

    @Test
    @DisplayName("should include operation name in error message")
    void testOperationNameInMessage() {
      // Given
      SQLException cause = new SQLException("Unknown error");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert user", logger);

      // Then
      assertThat(result.getMessage()).contains("insert user");
    }

    @Test
    @DisplayName("should include original error message in result")
    void testExceptionMessage() {
      // Given
      SQLException cause = new SQLException("Original error");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "delete", logger);

      // Then
      assertThat(result.getMessage()).contains("Original error");
    }
  }

  @Nested
  @DisplayName("Branch Coverage - Transient Conditions")
  class TransientBranchCoverageTests {

    @Test
    @DisplayName("should detect timeout message variation")
    void testTimeoutVariation() {
      SQLException cause = new SQLException("query timeout");
      RuntimeException result = ExceptionTranslator.translateException(cause, "select", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should detect connection refused message variation")
    void testConnectionRefusedVariation() {
      SQLException cause = new SQLException("connection refused");
      RuntimeException result = ExceptionTranslator.translateException(cause, "connect", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should detect deadlock message variation")
    void testDeadlockVariation() {
      SQLException cause = new SQLException("deadlock");
      RuntimeException result = ExceptionTranslator.translateException(cause, "update", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should detect lock timeout message variation")
    void testLockTimeoutVariation() {
      SQLException cause = new SQLException("lock timeout");
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should detect too many connections message variation")
    void testTooManyConnectionsVariation() {
      SQLException cause = new SQLException("too many connections");
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should detect pool exhausted message variation")
    void testPoolExhaustedVariation() {
      SQLException cause = new SQLException("pool exhausted");
      RuntimeException result = ExceptionTranslator.translateException(cause, "select", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should detect SQL state 08xxx (connection exception)")
    void testConnectionExceptionState() {
      SQLException cause = new SQLException("Connection error", "08001");
      RuntimeException result = ExceptionTranslator.translateException(cause, "connect", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should detect SQL state 40xxx (transaction rollback)")
    void testTransactionRollbackState() {
      SQLException cause = new SQLException("Rollback", "40000");
      RuntimeException result = ExceptionTranslator.translateException(cause, "update", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should detect SQL state 57P03 (cannot execute)")
    void testCannotExecuteState() {
      SQLException cause = new SQLException("Cannot execute", "57P03");
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }
  }

  @Nested
  @DisplayName("Branch Coverage - Permanent Conditions")
  class PermanentBranchCoverageTests {

    @Test
    @DisplayName("should detect syntax error message variation")
    void testSyntaxErrorVariation() {
      SQLException cause = new SQLException("syntax error");
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect table not found message variation")
    void testTableNotFoundVariation() {
      SQLException cause = new SQLException("table not found");
      RuntimeException result = ExceptionTranslator.translateException(cause, "select", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect column not found message variation")
    void testColumnNotFoundVariation() {
      SQLException cause = new SQLException("column not found");
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect does not exist message variation")
    void testDoesNotExistVariation() {
      SQLException cause = new SQLException("does not exist");
      RuntimeException result = ExceptionTranslator.translateException(cause, "update", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect schema not found message variation")
    void testSchemaNotFoundVariation() {
      SQLException cause = new SQLException("schema not found");
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect database not found message variation")
    void testDatabaseNotFoundVariation() {
      SQLException cause = new SQLException("database not found");
      RuntimeException result = ExceptionTranslator.translateException(cause, "connect", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect constraint violation message variation")
    void testConstraintViolationVariation() {
      SQLException cause = new SQLException("constraint violation");
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect unique constraint message variation")
    void testUniqueConstraintVariation() {
      SQLException cause = new SQLException("unique constraint");
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect foreign key message variation")
    void testForeignKeyVariation() {
      SQLException cause = new SQLException("foreign key");
      RuntimeException result = ExceptionTranslator.translateException(cause, "delete", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect type mismatch message variation")
    void testTypeMismatchVariation() {
      SQLException cause = new SQLException("type mismatch");
      RuntimeException result = ExceptionTranslator.translateException(cause, "update", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect invalid column message variation")
    void testInvalidColumnVariation() {
      SQLException cause = new SQLException("invalid column");
      RuntimeException result = ExceptionTranslator.translateException(cause, "select", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect SQL state 22xxx (data exception)")
    void testDataExceptionState() {
      SQLException cause = new SQLException("Data error", "22000");
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect SQL state 23xxx (integrity constraint)")
    void testIntegrityConstraintState() {
      SQLException cause = new SQLException("Constraint", "23000");
      RuntimeException result = ExceptionTranslator.translateException(cause, "update", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect SQL state 42xxx (syntax/access)")
    void testSyntaxAccessState() {
      SQLException cause = new SQLException("Access error", "42000");
      RuntimeException result = ExceptionTranslator.translateException(cause, "select", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect SQL state 3Dxxx (invalid catalog)")
    void testInvalidCatalogState() {
      SQLException cause = new SQLException("Catalog error", "3D000");
      RuntimeException result = ExceptionTranslator.translateException(cause, "connect", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect SQL state 3Fxxx (invalid schema)")
    void testInvalidSchemaState() {
      SQLException cause = new SQLException("Schema error", "3F000");
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }
  }

  // Custom SQLException with custom error code support
  static class TestSQLException extends SQLException {
    private final int errorCode;

    TestSQLException(String message, int errorCode) {
      super(message);
      this.errorCode = errorCode;
    }

    @Override
    public int getErrorCode() {
      return errorCode;
    }
  }

  @Nested
  @DisplayName("Database-Specific Error Codes")
  class DatabaseSpecificCodesTests {

    private SQLException createSQLExceptionWithErrorCode(String message, int errorCode) {
      return new TestSQLException(message, errorCode);
    }

    // PostgreSQL Transient Error Codes
    @Test
    @DisplayName("should detect PostgreSQL 40001 serialization failure")
    void testPostgreSQLErrorCode40001() {
      SQLException cause = createSQLExceptionWithErrorCode("Serialization failure", 40001);
      RuntimeException result = ExceptionTranslator.translateException(cause, "update", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should detect PostgreSQL 8006 connection failure")
    void testPostgreSQLErrorCode8006() {
      SQLException cause = createSQLExceptionWithErrorCode("Connection failure", 8006);
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should detect PostgreSQL 8003 connection failure")
    void testPostgreSQLErrorCode8003() {
      SQLException cause = createSQLExceptionWithErrorCode("Connection error", 8003);
      RuntimeException result = ExceptionTranslator.translateException(cause, "connect", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    // H2 Transient Error Code
    @Test
    @DisplayName("should detect H2 90008 timeout")
    void testH2ErrorCode90008() {
      SQLException cause = createSQLExceptionWithErrorCode("Timeout", 90008);
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);
      assertThat(result).isInstanceOf(TransientException.class);
    }

    // PostgreSQL Permanent Error Codes
    @Test
    @DisplayName("should detect PostgreSQL 42703 undefined column")
    void testPostgreSQLErrorCode42703() {
      SQLException cause = createSQLExceptionWithErrorCode("Undefined column", 42703);
      RuntimeException result = ExceptionTranslator.translateException(cause, "select", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect PostgreSQL 23505 unique violation")
    void testPostgreSQLErrorCode23505() {
      SQLException cause = createSQLExceptionWithErrorCode("Unique violation", 23505);
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect PostgreSQL 23503 foreign key violation")
    void testPostgreSQLErrorCode23503() {
      SQLException cause = createSQLExceptionWithErrorCode("Foreign key violation", 23503);
      RuntimeException result = ExceptionTranslator.translateException(cause, "delete", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }




    // H2 Permanent Error Codes
    @Test
    @DisplayName("should detect H2 90002 table not found")
    void testH2ErrorCode90002() {
      SQLException cause = createSQLExceptionWithErrorCode("Table not found", 90002);
      RuntimeException result = ExceptionTranslator.translateException(cause, "select", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect H2 90007 parameter count mismatch")
    void testH2ErrorCode90007() {
      SQLException cause = createSQLExceptionWithErrorCode("Parameter mismatch", 90007);
      RuntimeException result = ExceptionTranslator.translateException(cause, "insert", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should detect H2 42122 column not found")
    void testH2ErrorCode42122() {
      SQLException cause = createSQLExceptionWithErrorCode("Column not found", 42122);
      RuntimeException result = ExceptionTranslator.translateException(cause, "select", logger);
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    // Edge case: errorCode = 0
    @Test
    @DisplayName("should not match when errorCode is 0")
    void testErrorCodeZero() {
      SQLException cause = createSQLExceptionWithErrorCode("Error", 0);
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);
      // Should match on message/sqlState, not errorCode=0
      assertThat(result).isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should not match on unknown error code")
    void testUnknownErrorCode() {
      SQLException cause = createSQLExceptionWithErrorCode("Unknown", 99999);
      RuntimeException result = ExceptionTranslator.translateException(cause, "query", logger);
      // Should default to TransientException when in doubt
      assertThat(result).isInstanceOf(TransientException.class);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCasesTests {

    @Test
    @DisplayName("should handle null exception message gracefully")
    void testNullExceptionMessage() {
      // Given
      SQLException cause = new SQLException((String) null);

      // When/Then - Should not throw NPE
      assertThat(ExceptionTranslator.translateException(cause, "select", logger))
          .isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should handle null SQL state gracefully")
    void testNullSQLState() {
      // Given
      SQLException cause = new SQLException("Error message");
      // SQL state is null by default

      // When/Then - Should not throw NPE
      assertThat(ExceptionTranslator.translateException(cause, "insert", logger))
          .isInstanceOf(TransientException.class);
    }

    @Test
    @DisplayName("should be case insensitive when checking error messages")
    void testCaseInsensitiveMessageCheck() {
      // Given
      SQLException cause = new SQLException("TABLE NOT FOUND");

      // When
      RuntimeException result = ExceptionTranslator.translateException(cause, "select", logger);

      // Then
      assertThat(result).isInstanceOf(PermanentException.class);
    }

    @Test
    @DisplayName("should handle chained SQLException")
    void testChainedSQLException() {
      // Given
      SQLException root = new SQLException("Root cause");
      SQLException chained = new SQLException("Chained exception", root);

      // When
      RuntimeException result =
          ExceptionTranslator.translateException(chained, "operation", logger);

      // Then - Should handle chained exceptions without error
      assertThat(result).isNotNull();
    }
  }
}
