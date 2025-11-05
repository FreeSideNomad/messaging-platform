package com.acme.payments.infrastructure.persistence;

import com.acme.payments.domain.model.AccountLimit;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;
import com.acme.payments.domain.repository.AccountLimitRepository;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * JDBC implementation of AccountLimitRepository
 */
@Singleton
@RequiredArgsConstructor
@Slf4j
public class JdbcAccountLimitRepository implements AccountLimitRepository {
    private final DataSource dataSource;

    @Override
    public void save(AccountLimit limit) {
        log.debug("Saving account limit: {}", limit.getLimitId());

        try (Connection conn = dataSource.getConnection()) {
            boolean exists = limitExists(conn, limit.getLimitId());

            if (exists) {
                updateLimit(conn, limit);
            } else {
                insertLimit(conn, limit);
            }

            // Don't commit here - let the ambient transaction (if any) handle it
            // This allows repositories to work both in tests (@Transactional) and production
        } catch (SQLException e) {
            log.error("Error saving account limit: {}", limit.getLimitId(), e);
            throw new RuntimeException("Failed to save account limit", e);
        }
    }

    @Override
    public List<AccountLimit> findActiveByAccountId(UUID accountId) {
        log.debug("Finding active limits for account: {}", accountId);

        List<AccountLimit> limits = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT limit_id, account_id, period_type, limit_amount, utilized,
                       currency_code, period_start, period_end
                FROM account_limit
                WHERE account_id = ?
                AND period_start <= CURRENT_TIMESTAMP
                AND period_end > CURRENT_TIMESTAMP
                ORDER BY period_type
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, accountId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        limits.add(mapLimit(rs));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error finding active limits for account: {}", accountId, e);
            throw new RuntimeException("Failed to find active limits", e);
        }

        return limits;
    }

    @Override
    public List<AccountLimit> findByAccountIdAndPeriodType(UUID accountId, PeriodType periodType) {
        log.debug("Finding limits for account {} and period {}", accountId, periodType);

        List<AccountLimit> limits = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT limit_id, account_id, period_type, limit_amount, utilized,
                       currency_code, period_start, period_end
                FROM account_limit
                WHERE account_id = ?
                AND period_type = ?
                AND period_start <= CURRENT_TIMESTAMP
                AND period_end > CURRENT_TIMESTAMP
                ORDER BY period_start DESC
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, accountId);
                stmt.setString(2, periodType.name());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        limits.add(mapLimit(rs));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error finding limits for account {} and period {}", accountId, periodType, e);
            throw new RuntimeException("Failed to find limits", e);
        }

        return limits;
    }

    private boolean limitExists(Connection conn, UUID limitId) throws SQLException {
        String sql = "SELECT 1 FROM account_limit WHERE limit_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, limitId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertLimit(Connection conn, AccountLimit limit) throws SQLException {
        String sql = """
            INSERT INTO account_limit (limit_id, account_id, period_type, limit_amount, utilized,
                                      currency_code, period_start, period_end)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, limit.getLimitId());
            stmt.setObject(2, limit.getAccountId());
            stmt.setString(3, limit.getPeriodType().name());
            stmt.setBigDecimal(4, limit.getLimitAmount().amount());
            stmt.setBigDecimal(5, limit.getUtilized().amount());
            stmt.setString(6, limit.getLimitAmount().currencyCode());
            stmt.setTimestamp(7, Timestamp.from(limit.getStartTime()));
            stmt.setTimestamp(8, Timestamp.from(limit.getEndTime()));

            stmt.executeUpdate();
        }
    }

    private void updateLimit(Connection conn, AccountLimit limit) throws SQLException {
        String sql = """
            UPDATE account_limit
            SET utilized = ?, period_end = ?
            WHERE limit_id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBigDecimal(1, limit.getUtilized().amount());
            stmt.setTimestamp(2, Timestamp.from(limit.getEndTime()));
            stmt.setObject(3, limit.getLimitId());

            stmt.executeUpdate();
        }
    }

    private AccountLimit mapLimit(ResultSet rs) throws SQLException {
        UUID limitId = (UUID) rs.getObject("limit_id");
        UUID accountId = (UUID) rs.getObject("account_id");
        PeriodType periodType = PeriodType.valueOf(rs.getString("period_type"));
        String currencyCode = rs.getString("currency_code");
        Money limitAmount = new Money(rs.getBigDecimal("limit_amount").setScale(2, java.math.RoundingMode.HALF_UP), currencyCode);
        Instant startTime = rs.getTimestamp("period_start").toInstant();

        AccountLimit limit = new AccountLimit(
            limitId,
            accountId,
            periodType,
            startTime,
            limitAmount
        );

        // Restore utilized amount by booking it
        Money utilized = new Money(rs.getBigDecimal("utilized").setScale(2, java.math.RoundingMode.HALF_UP), currencyCode);
        if (utilized.isPositive()) {
            limit.book(utilized);
        }

        return limit;
    }
}
