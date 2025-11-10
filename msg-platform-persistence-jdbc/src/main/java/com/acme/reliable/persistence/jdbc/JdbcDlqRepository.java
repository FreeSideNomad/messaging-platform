package com.acme.reliable.persistence.jdbc;

import com.acme.reliable.repository.DlqRepository;
import io.micronaut.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Abstract JDBC implementation of DlqRepository using Template Method pattern.
 * Subclasses override database-specific SQL methods.
 */
public abstract class JdbcDlqRepository implements DlqRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcDlqRepository.class);

    protected final DataSource dataSource;

    protected JdbcDlqRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @Transactional
    public void insertDlqEntry(
            UUID commandId,
            String commandName,
            String businessKey,
            String payload,
            String failedStatus,
            String errorClass,
            String errorMessage,
            int attempts,
            String parkedBy) {
        String sql = getInsertDlqEntrySql();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, commandId);
            ps.setObject(2, commandId);
            ps.setString(3, commandName);
            ps.setString(4, businessKey);
            ps.setString(5, payload);
            ps.setString(6, failedStatus);
            ps.setString(7, errorClass);
            ps.setString(8, errorMessage);
            ps.setInt(9, attempts);
            ps.setString(10, parkedBy);
            ps.setTimestamp(11, Timestamp.from(java.time.Instant.now()));

            ps.executeUpdate();
            LOG.debug(
                    "Inserted DLQ entry: commandId={}, commandName={}, parkedBy={}",
                    commandId,
                    commandName,
                    parkedBy);

        } catch (SQLException e) {
            throw ExceptionTranslator.translateException(e, "insert DLQ entry", LOG);
        }
    }

    // Template method for database-specific SQL

    protected abstract String getInsertDlqEntrySql();
}
