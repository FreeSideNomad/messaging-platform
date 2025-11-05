package com.acme.payments.infrastructure.persistence;

import com.acme.payments.domain.model.Beneficiary;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.Payment;
import com.acme.payments.domain.model.PaymentStatus;
import com.acme.payments.domain.repository.PaymentRepository;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * JDBC implementation of PaymentRepository
 */
@Singleton
@RequiredArgsConstructor
@Slf4j
public class JdbcPaymentRepository implements PaymentRepository {
    private final DataSource dataSource;

    @Override
    public void save(Payment payment) {
        log.debug("Saving payment: {}", payment.getPaymentId());

        try (Connection conn = dataSource.getConnection()) {
            boolean exists = paymentExists(conn, payment.getPaymentId());

            if (exists) {
                updatePayment(conn, payment);
            } else {
                insertPayment(conn, payment);
            }

            // Don't commit here - let the ambient transaction (if any) handle it
            // This allows repositories to work both in tests (@Transactional) and production
        } catch (SQLException e) {
            log.error("Error saving payment: {}", payment.getPaymentId(), e);
            throw new RuntimeException("Failed to save payment", e);
        }
    }

    @Override
    public Optional<Payment> findById(UUID paymentId) {
        log.debug("Finding payment by id: {}", paymentId);

        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT payment_id, debit_account_id, debit_transaction_id, fx_contract_id,
                       debit_amount, debit_currency_code,
                       credit_amount, credit_currency_code,
                       value_date, status,
                       beneficiary_name, beneficiary_account_number,
                       beneficiary_transit_number, beneficiary_bank_name,
                       created_at
                FROM payment
                WHERE payment_id = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, paymentId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapPayment(rs));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error finding payment by id: {}", paymentId, e);
            throw new RuntimeException("Failed to find payment", e);
        }

        return Optional.empty();
    }

    private boolean paymentExists(Connection conn, UUID paymentId) throws SQLException {
        String sql = "SELECT 1 FROM payment WHERE payment_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, paymentId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertPayment(Connection conn, Payment payment) throws SQLException {
        String sql = """
            INSERT INTO payment (payment_id, debit_account_id, debit_transaction_id, fx_contract_id,
                               debit_amount, debit_currency_code,
                               credit_amount, credit_currency_code,
                               value_date, status,
                               beneficiary_name, beneficiary_account_number,
                               beneficiary_transit_number, beneficiary_bank_name,
                               created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, payment.getPaymentId());
            stmt.setObject(2, payment.getDebitAccountId());
            stmt.setObject(3, payment.getDebitTransactionId());
            stmt.setObject(4, payment.getFxContractId());
            stmt.setBigDecimal(5, payment.getDebitAmount().amount());
            stmt.setString(6, payment.getDebitAmount().currencyCode());
            stmt.setBigDecimal(7, payment.getCreditAmount().amount());
            stmt.setString(8, payment.getCreditAmount().currencyCode());
            stmt.setDate(9, Date.valueOf(payment.getValueDate()));
            stmt.setString(10, payment.getStatus().name());
            stmt.setString(11, payment.getBeneficiary().name());
            stmt.setString(12, payment.getBeneficiary().accountNumber());
            stmt.setString(13, payment.getBeneficiary().transitNumber());
            stmt.setString(14, payment.getBeneficiary().bankName());
            stmt.setTimestamp(15, Timestamp.from(payment.getCreatedAt()));

            stmt.executeUpdate();
        }
    }

    private void updatePayment(Connection conn, Payment payment) throws SQLException {
        String sql = """
            UPDATE payment
            SET debit_transaction_id = ?, fx_contract_id = ?, status = ?
            WHERE payment_id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, payment.getDebitTransactionId());
            stmt.setObject(2, payment.getFxContractId());
            stmt.setString(3, payment.getStatus().name());
            stmt.setObject(4, payment.getPaymentId());

            stmt.executeUpdate();
        }
    }

    private Payment mapPayment(ResultSet rs) throws SQLException {
        UUID paymentId = (UUID) rs.getObject("payment_id");
        UUID debitAccountId = (UUID) rs.getObject("debit_account_id");
        Money debitAmount = new Money(
            rs.getBigDecimal("debit_amount").setScale(2, java.math.RoundingMode.HALF_UP),
            rs.getString("debit_currency_code")
        );
        Money creditAmount = new Money(
            rs.getBigDecimal("credit_amount").setScale(2, java.math.RoundingMode.HALF_UP),
            rs.getString("credit_currency_code")
        );
        Beneficiary beneficiary = new Beneficiary(
            rs.getString("beneficiary_name"),
            rs.getString("beneficiary_account_number"),
            rs.getString("beneficiary_transit_number"),
            rs.getString("beneficiary_bank_name")
        );

        Payment payment = new Payment(
            paymentId,
            debitAccountId,
            debitAmount,
            creditAmount,
            rs.getDate("value_date").toLocalDate(),
            beneficiary
        );

        // Restore state
        UUID debitTransactionId = (UUID) rs.getObject("debit_transaction_id");
        UUID fxContractId = (UUID) rs.getObject("fx_contract_id");
        PaymentStatus status = PaymentStatus.valueOf(rs.getString("status"));

        if (debitTransactionId != null) {
            payment.recordDebitTransaction(debitTransactionId);
        }
        if (fxContractId != null) {
            payment.recordFxContract(fxContractId);
        }

        // Set status based on loaded value - must follow valid state transitions
        switch (status) {
            case PROCESSING -> payment.markAsProcessing();
            case COMPLETED -> {
                payment.markAsProcessing();
                payment.markAsCompleted();
            }
            case FAILED -> payment.markAsFailed("Loaded as failed");
            case REVERSED -> {
                payment.markAsProcessing();
                payment.markAsCompleted();
                payment.reverse("Loaded as reversed");
            }
            default -> {} // PENDING is the initial state
        }

        return payment;
    }
}
