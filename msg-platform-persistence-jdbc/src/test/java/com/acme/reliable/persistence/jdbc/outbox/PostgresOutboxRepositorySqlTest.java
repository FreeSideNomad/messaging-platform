package com.acme.reliable.persistence.jdbc.outbox;

import com.acme.reliable.persistence.jdbc.PostgresOutboxRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL verification tests for PostgresOutboxRepository.
 * Verifies that all SQL statements are correctly formed for PostgreSQL dialect.
 */
@DisplayName("PostgreSQL Outbox Repository SQL Verification")
class PostgresOutboxRepositorySqlTest {

    private PostgresOutboxRepository repository;
    private HikariDataSource dataSource;

    @BeforeEach
    void setup() {
        // Create minimal H2 DataSource for testing (not used for actual queries)
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:test");
        config.setUsername("sa");
        config.setPassword("");
        dataSource = new HikariDataSource(config);

        repository = new PostgresOutboxRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("Insert SQL should use JSONB cast for headers")
    void testInsertSql() {
        String sql = invokeProtectedMethod("getInsertSql");

        assertThat(sql)
                .contains("INSERT INTO platform.outbox")
                .contains("(category, topic, key, type, payload, headers, status, attempts, created_at)")
                .contains("VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)")
                .contains("?::jsonb")
                .doesNotContain("H2")
                .doesNotContain("PRAGMA");
    }

    @Test
    @DisplayName("Claim if new SQL should use RETURNING clause")
    void testClaimIfNewSql() {
        String sql = invokeProtectedMethod("getClaimIfNewSql");

        assertThat(sql)
                .contains("UPDATE platform.outbox")
                .contains("SET status = 'CLAIMED'")
                .contains("WHERE id = ? AND status = 'NEW'")
                .contains("RETURNING")
                .contains("id, category, topic, key, type, payload, headers, status, attempts")
                .contains("next_at, claimed_by, created_at, published_at, last_error");
    }

    @Test
    @DisplayName("Sweep batch SQL should use CTE with FOR UPDATE SKIP LOCKED")
    void testSweepBatchSql() {
        String sql = invokeProtectedMethod("getSweepBatchSql");

        assertThat(sql)
                .contains("WITH available AS")
                .contains("SELECT id")
                .contains("FROM platform.outbox")
                .contains("WHERE (status = 'NEW' OR (status = 'CLAIMED' AND created_at < now() - interval '5 minutes'))")
                .contains("AND (next_at IS NULL OR next_at <= now())")
                .contains("ORDER BY created_at ASC")
                .contains("LIMIT ? FOR UPDATE SKIP LOCKED")
                .contains("UPDATE platform.outbox o")
                .contains("FROM available")
                .contains("WHERE o.id = available.id")
                .contains("RETURNING");
    }

    @Test
    @DisplayName("Mark published SQL should update status and published_at")
    void testMarkPublishedSql() {
        String sql = invokeProtectedMethod("getMarkPublishedSql");

        assertThat(sql)
                .contains("UPDATE platform.outbox")
                .contains("SET status = 'PUBLISHED', published_at = ?")
                .contains("WHERE id = ?");
    }

    @Test
    @DisplayName("Mark failed SQL should set error and next_at")
    void testMarkFailedSql() {
        String sql = invokeProtectedMethod("getMarkFailedSql");

        assertThat(sql)
                .contains("UPDATE platform.outbox")
                .contains("SET last_error = ?, next_at = ?")
                .contains("WHERE id = ?");
    }

    @Test
    @DisplayName("Reschedule SQL should update next_at and error")
    void testRescheduleSql() {
        String sql = invokeProtectedMethod("getRescheduleSql");

        assertThat(sql)
                .contains("UPDATE platform.outbox")
                .contains("SET next_at = ?, last_error = ?")
                .contains("WHERE id = ?");
    }

    @Test
    @DisplayName("Recover stuck SQL should reset CLAIMED status older than threshold")
    void testRecoverStuckSql() {
        String sql = invokeProtectedMethod("getRecoverStuckSql");

        assertThat(sql)
                .contains("UPDATE platform.outbox")
                .contains("SET status = 'NEW', next_at = NULL")
                .contains("WHERE status = 'CLAIMED' AND created_at < ?");
    }

    /**
     * Helper method to invoke protected SQL methods via reflection.
     */
    private String invokeProtectedMethod(String methodName) {
        try {
            Method method = PostgresOutboxRepository.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (String) method.invoke(repository);
        } catch (Exception e) {
            throw new AssertionError("Failed to invoke method: " + methodName, e);
        }
    }
}
