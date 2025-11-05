package com.acme.payments.infrastructure.persistence;

import com.acme.payments.domain.model.FxContract;
import com.acme.payments.domain.model.FxStatus;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.repository.FxContractRepository;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * JDBC implementation of FxContractRepository
 */
@Singleton
@RequiredArgsConstructor
@Slf4j
public class JdbcFxContractRepository implements FxContractRepository {
    private final DataSource dataSource;

    @Override
    public void save(FxContract fxContract) {
        log.debug("Saving FX contract: {}", fxContract.getFxContractId());

        try (Connection conn = dataSource.getConnection()) {
            boolean exists = fxContractExists(conn, fxContract.getFxContractId());

            if (exists) {
                updateFxContract(conn, fxContract);
            } else {
                insertFxContract(conn, fxContract);
            }

            // Don't commit here - let the ambient transaction (if any) handle it
            // This allows repositories to work both in tests (@Transactional) and production
        } catch (SQLException e) {
            log.error("Error saving FX contract: {}", fxContract.getFxContractId(), e);
            throw new RuntimeException("Failed to save FX contract", e);
        }
    }

    @Override
    public Optional<FxContract> findById(UUID fxContractId) {
        log.debug("Finding FX contract by id: {}", fxContractId);

        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT fx_contract_id, customer_id, debit_account_id,
                       debit_amount, debit_currency_code,
                       credit_amount, credit_currency_code,
                       rate, value_date, status
                FROM fx_contract
                WHERE fx_contract_id = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, fxContractId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapFxContract(rs));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error finding FX contract by id: {}", fxContractId, e);
            throw new RuntimeException("Failed to find FX contract", e);
        }

        return Optional.empty();
    }

    private boolean fxContractExists(Connection conn, UUID fxContractId) throws SQLException {
        String sql = "SELECT 1 FROM fx_contract WHERE fx_contract_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, fxContractId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertFxContract(Connection conn, FxContract fxContract) throws SQLException {
        String sql = """
            INSERT INTO fx_contract (fx_contract_id, customer_id, debit_account_id,
                                    debit_amount, debit_currency_code,
                                    credit_amount, credit_currency_code,
                                    rate, value_date, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, fxContract.getFxContractId());
            stmt.setObject(2, fxContract.getCustomerId());
            stmt.setObject(3, fxContract.getDebitAccountId());
            stmt.setBigDecimal(4, fxContract.getDebitAmount().amount());
            stmt.setString(5, fxContract.getDebitAmount().currencyCode());
            stmt.setBigDecimal(6, fxContract.getCreditAmount().amount());
            stmt.setString(7, fxContract.getCreditAmount().currencyCode());
            stmt.setBigDecimal(8, fxContract.getRate());
            stmt.setDate(9, Date.valueOf(fxContract.getValueDate()));
            stmt.setString(10, fxContract.getStatus().name());

            stmt.executeUpdate();
        }
    }

    private void updateFxContract(Connection conn, FxContract fxContract) throws SQLException {
        String sql = """
            UPDATE fx_contract
            SET status = ?
            WHERE fx_contract_id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fxContract.getStatus().name());
            stmt.setObject(2, fxContract.getFxContractId());

            stmt.executeUpdate();
        }
    }

    private FxContract mapFxContract(ResultSet rs) throws SQLException {
        UUID fxContractId = (UUID) rs.getObject("fx_contract_id");
        UUID customerId = (UUID) rs.getObject("customer_id");
        UUID debitAccountId = (UUID) rs.getObject("debit_account_id");
        Money debitAmount = new Money(
            rs.getBigDecimal("debit_amount").setScale(2, java.math.RoundingMode.HALF_UP),
            rs.getString("debit_currency_code")
        );
        Money creditAmount = new Money(
            rs.getBigDecimal("credit_amount").setScale(2, java.math.RoundingMode.HALF_UP),
            rs.getString("credit_currency_code")
        );

        FxContract fxContract = new FxContract(
            fxContractId,
            customerId,
            debitAccountId,
            debitAmount,
            creditAmount,
            rs.getBigDecimal("rate"),
            rs.getDate("value_date").toLocalDate()
        );

        // Set status if it's UNWOUND
        String status = rs.getString("status");
        if (FxStatus.UNWOUND.name().equals(status)) {
            fxContract.unwind("Loaded as unwound");
        }

        return fxContract;
    }
}
