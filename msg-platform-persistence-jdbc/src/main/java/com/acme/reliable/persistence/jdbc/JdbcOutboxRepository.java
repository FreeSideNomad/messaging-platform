package com.acme.reliable.persistence.jdbc;

import com.acme.reliable.core.Jsons;
import com.acme.reliable.domain.Outbox;
import com.acme.reliable.repository.OutboxRepository;
import io.micronaut.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Abstract JDBC implementation of OutboxRepository using Template Method pattern.
 * Subclasses override database-specific SQL methods.
 */
public abstract class JdbcOutboxRepository implements OutboxRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcOutboxRepository.class);

    protected final DataSource dataSource;

    protected JdbcOutboxRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Transactional
    public void insert(
            long id,
            String category,
            String topic,
            String key,
            String type,
            String payload,
            String headers,
            String status,
            int attempts) {
        String sql = "INSERT INTO outbox (category, topic, \"key\", \"type\", payload, headers, status, attempts, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, category);
            ps.setString(2, topic);
            ps.setString(3, key);
            ps.setString(4, type);
            ps.setString(5, payload);
            ps.setString(6, headers != null && !headers.isEmpty() ? headers : "{}");
            ps.setString(7, status);
            ps.setInt(8, attempts);
            ps.setTimestamp(9, Timestamp.from(Instant.now()));

            ps.executeUpdate();
            LOG.debug("Inserted outbox entry (id auto-generated from sequence)");

        } catch (SQLException e) {
            throw ExceptionTranslator.translateException(e, "insert outbox entry", LOG);
        }
    }

    @Transactional
    public long insertReturningId(
            String category, String topic, String key, String type, String payload, String headers) {
        String sql = getInsertSql();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, category);
            ps.setString(2, topic);
            ps.setString(3, key);
            ps.setString(4, type);
            ps.setString(5, payload);
            ps.setString(6, headers != null && !headers.isEmpty() ? headers : "{}");
            ps.setString(7, "NEW");
            ps.setInt(8, 0);
            ps.setTimestamp(9, Timestamp.from(Instant.now()));

            int rowsInserted = ps.executeUpdate();
            if (rowsInserted > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        long id = rs.getLong(1);
                        LOG.debug("Inserted outbox entry with id: {}", id);
                        return id;
                    }
                }
            }

            throw ExceptionTranslator.translateException(
                    new SQLException("Failed to insert outbox entry - no rows inserted"),
                    "insertReturningId outbox entry",
                    LOG);

        } catch (SQLException e) {
            throw ExceptionTranslator.translateException(e, "insertReturningId outbox entry", LOG);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Outbox> claimIfNew(long id) {
        String sql = getClaimIfNewSql();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToOutbox(rs));
                }
            }

            return Optional.empty();

        } catch (SQLException e) {
            throw ExceptionTranslator.translateException(e, "claim outbox entry by id", LOG);
        }
    }

    @Transactional(readOnly = true)
    public List<Outbox> sweepBatch(int max) {
        String sql = getSweepBatchSql();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, max);

            List<Outbox> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToOutbox(rs));
                }
            }

            return results;

        } catch (SQLException e) {
            throw ExceptionTranslator.translateException(e, "sweep batch of outbox entries", LOG);
        }
    }

    @Transactional
    public void markPublished(long id) {
        String sql = getMarkPublishedSql();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setLong(2, id);

            int updated = ps.executeUpdate();
            if (updated == 0) {
                LOG.warn("No rows updated for markPublished: id={}", id);
            }

        } catch (SQLException e) {
            throw ExceptionTranslator.translateException(e, "mark outbox entry as published", LOG);
        }
    }

    @Transactional
    public void markFailed(long id, String error, Instant nextAttempt) {
        String sql = getMarkFailedSql();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, error);
            ps.setTimestamp(2, Timestamp.from(nextAttempt));
            ps.setLong(3, id);

            int updated = ps.executeUpdate();
            if (updated == 0) {
                LOG.warn("No rows updated for markFailed: id={}", id);
            }

        } catch (SQLException e) {
            throw ExceptionTranslator.translateException(e, "mark outbox entry as failed", LOG);
        }
    }

    @Transactional
    public void reschedule(long id, long backoffMs, String error) {
        String sql = getRescheduleSql();
        Instant nextAt =
                Instant.now().plusMillis(backoffMs);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.from(nextAt));
            ps.setString(2, error);
            ps.setLong(3, id);

            int updated = ps.executeUpdate();
            if (updated == 0) {
                LOG.warn("No rows updated for reschedule: id={}", id);
            }

        } catch (SQLException e) {
            throw ExceptionTranslator.translateException(e, "reschedule outbox entry", LOG);
        }
    }

    @Transactional(readOnly = true)
    public int recoverStuck(Duration olderThan) {
        String sql = getRecoverStuckSql();
        Instant threshold = Instant.now().minus(olderThan);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.from(threshold));

            return ps.executeUpdate();

        } catch (SQLException e) {
            throw ExceptionTranslator.translateException(e, "recover stuck outbox entries", LOG);
        }
    }

    // Template methods for database-specific SQL

    protected abstract String getInsertSql();

    protected abstract String getClaimIfNewSql();

    protected abstract String getSweepBatchSql();

    protected abstract String getMarkPublishedSql();

    protected abstract String getMarkFailedSql();

    protected abstract String getRescheduleSql();

    protected abstract String getRecoverStuckSql();

    // Helper method for result set mapping

    protected Outbox mapResultSetToOutbox(ResultSet rs) throws SQLException {
        Outbox outbox = new Outbox();
        outbox.setId(rs.getLong("id"));
        outbox.setCategory(rs.getString("category"));
        outbox.setTopic(rs.getString("topic"));
        outbox.setKey(rs.getString("key"));
        outbox.setType(rs.getString("type"));
        outbox.setPayload(rs.getString("payload"));

        String headersJson = rs.getString("headers");
        if (headersJson != null && !headersJson.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, String> headers = Jsons.fromJson(headersJson, Map.class);
            outbox.setHeaders(headers);
        } else {
            outbox.setHeaders(new HashMap<>());
        }

        outbox.setStatus(rs.getString("status"));
        outbox.setAttempts(rs.getInt("attempts"));

        Timestamp nextAt = rs.getTimestamp("next_at");
        if (nextAt != null) {
            outbox.setNextAt(nextAt.toInstant());
        }

        String claimedBy = rs.getString("claimed_by");
        if (claimedBy != null) {
            outbox.setClaimedBy(claimedBy);
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            outbox.setCreatedAt(createdAt.toInstant());
        }

        Timestamp publishedAt = rs.getTimestamp("published_at");
        if (publishedAt != null) {
            outbox.setPublishedAt(publishedAt.toInstant());
        }

        String lastError = rs.getString("last_error");
        if (lastError != null) {
            outbox.setLastError(lastError);
        }

        return outbox;
    }
}
