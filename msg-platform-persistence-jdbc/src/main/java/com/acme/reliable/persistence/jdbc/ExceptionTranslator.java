package com.acme.reliable.persistence.jdbc;

import com.acme.reliable.core.PermanentException;
import com.acme.reliable.core.TransientException;
import org.slf4j.Logger;

import java.sql.SQLException;

/**
 * Utility class for translating SQLException to domain exceptions.
 * Determines whether an exception is permanent (non-retryable) or transient (retryable).
 */
public class ExceptionTranslator {

    private ExceptionTranslator() {
        // Utility class - no instantiation
    }

    /**
     * Translates a SQLException to either PermanentException or TransientException.
     *
     * @param originalException The SQLException that occurred
     * @param operation         Description of the operation that failed
     * @param logger            Logger for error reporting
     * @return PermanentException for non-retryable errors, TransientException for retryable ones
     */
    public static RuntimeException translateException(
            SQLException originalException, String operation, Logger logger) {

        logger.error("Database operation failed: {}", operation, originalException);

        // Check for transient error conditions that can be retried
        if (isTransientError(originalException)) {
            return new TransientException(
                    String.format("Transient database error during %s: %s", operation,
                            originalException.getMessage()));
        }

        // Check for permanent error conditions that cannot be retried
        if (isPermanentError(originalException)) {
            return new PermanentException(
                    String.format("Permanent database error during %s: %s", operation,
                            originalException.getMessage()));
        }

        // Default to TransientException when in doubt
        return new TransientException(
                String.format("Database error during %s: %s", operation, originalException.getMessage()));
    }

    /**
     * Determines if an exception represents a transient error that can be retried.
     * <p>
     * Transient errors include:
     * - Connection timeout/refused
     * - Lock timeout
     * - Deadlock detected
     * - Connection pool exhaustion
     * - Temporary server unavailable
     *
     * @param exception The SQLException to check
     * @return true if the error is transient and retryable
     */
    private static boolean isTransientError(SQLException exception) {
        if (exception == null) {
            return false;
        }

        String message = exception.getMessage();
        if (message == null) {
            message = "";
        } else {
            message = message.toLowerCase();
        }
        String sqlState = exception.getSQLState();

        // Check error message for transient indicators
        if (message.contains("timeout") || message.contains("connection refused") ||
                message.contains("deadlock") || message.contains("lock timeout") ||
                message.contains("too many connections") || message.contains("pool exhausted")) {
            return true;
        }

        // Check SQL state codes for transient errors
        if (sqlState != null) {
            // 08xxx - Connection Exception
            // 40xxx - Transaction Rollback
            if (sqlState.startsWith("08") || sqlState.startsWith("40")) {
                return true;
            }
            // 57P03 - Cannot execute queries while in copying state
            if (sqlState.equals("57P03")) {
                return true;
            }
        }

        // Check for database-specific transient errors
        if (isDatabaseSpecificTransient(exception)) {
            return true;
        }

        return false;
    }

    /**
     * Determines if an exception represents a permanent error that cannot be retried.
     * <p>
     * Permanent errors include:
     * - Schema not found / table not found
     * - Column not found
     * - Foreign key constraint violation
     * - Unique constraint violation
     * - Syntax error
     * - Data type mismatch
     * - Invalid operation
     *
     * @param exception The SQLException to check
     * @return true if the error is permanent and non-retryable
     */
    private static boolean isPermanentError(SQLException exception) {
        if (exception == null) {
            return false;
        }

        String message = exception.getMessage();
        if (message == null) {
            message = "";
        } else {
            message = message.toLowerCase();
        }
        String sqlState = exception.getSQLState();

        // Check error message for permanent indicators
        if (message.contains("syntax error") || message.contains("table not found") ||
                message.contains("column not found") || message.contains("does not exist") ||
                message.contains("schema not found") || message.contains("database not found") ||
                message.contains("constraint violation") || message.contains("unique constraint") ||
                message.contains("foreign key") || message.contains("type mismatch") ||
                message.contains("invalid column")) {
            return true;
        }

        // Check SQL state codes for permanent errors
        if (sqlState != null) {
            // 22xxx - Data Exception
            // 23xxx - Integrity Constraint Violation
            // 42xxx - Syntax Error / Access Violation
            // 3Dxxx - Invalid Catalog Name
            // 3Fxxx - Invalid Schema Name
            if (sqlState.startsWith("22") || sqlState.startsWith("23") || sqlState.startsWith("42") ||
                    sqlState.startsWith("3D") || sqlState.startsWith("3F")) {
                return true;
            }
        }

        // Check for database-specific permanent errors
        if (isDatabaseSpecificPermanent(exception)) {
            return true;
        }

        return false;
    }

    /**
     * Checks for database-specific transient errors.
     * Database systems have their own error codes and messages.
     *
     * @param exception The SQLException to check
     * @return true if the error is a known database-specific transient error
     */
    private static boolean isDatabaseSpecificTransient(SQLException exception) {
        if (exception == null || exception.getErrorCode() == 0) {
            return false;
        }

        int errorCode = exception.getErrorCode();

        // PostgreSQL-specific transient errors
        // 40001 - Serialization failure (deadlock)
        // 08006 - Connection failure
        if (errorCode == 40001 || errorCode == 8006 || errorCode == 8003) {
            return true;
        }

        // H2-specific transient errors
        // 90008 - General error / timeout
        if (errorCode == 90008) {
            return true;
        }

        return false;
    }

    /**
     * Checks for database-specific permanent errors.
     * Database systems have their own error codes and messages.
     *
     * @param exception The SQLException to check
     * @return true if the error is a known database-specific permanent error
     */
    private static boolean isDatabaseSpecificPermanent(SQLException exception) {
        if (exception == null || exception.getErrorCode() == 0) {
            return false;
        }

        int errorCode = exception.getErrorCode();

        // PostgreSQL-specific permanent errors
        // 42703 - Undefined column
        // 23505 - Unique violation
        // 23503 - Foreign key violation
        if (errorCode == 42703 || errorCode == 23505 || errorCode == 23503) {
            return true;
        }

        // H2-specific permanent errors
        // 90002 - Table not found
        // 90007 - Parameter count mismatch
        // 42122 - Column not found
        if (errorCode == 90002 || errorCode == 90007 || errorCode == 42122) {
            return true;
        }

        return false;
    }
}
