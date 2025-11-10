package com.acme.payments.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Aggregate root for Payment
 */
@Getter
public class Payment {
    private final UUID paymentId;
    private final UUID debitAccountId;
    private final Money debitAmount;
    private final Money creditAmount;
    private final LocalDate valueDate;
    private final Beneficiary beneficiary;
    private final Instant createdAt;
    private UUID debitTransactionId;
    private UUID fxContractId;
    private Money feeAmount;
    private PaymentStatus status;

    public Payment(
            UUID paymentId,
            UUID debitAccountId,
            Money debitAmount,
            Money creditAmount,
            LocalDate valueDate,
            Beneficiary beneficiary) {
        if (paymentId == null) {
            throw new IllegalArgumentException("Payment ID cannot be null");
        }
        if (debitAccountId == null) {
            throw new IllegalArgumentException("Debit account ID cannot be null");
        }
        if (debitAmount == null) {
            throw new IllegalArgumentException("Debit amount cannot be null");
        }
        if (creditAmount == null) {
            throw new IllegalArgumentException("Credit amount cannot be null");
        }
        if (valueDate == null) {
            throw new IllegalArgumentException("Value date cannot be null");
        }
        if (beneficiary == null) {
            throw new IllegalArgumentException("Beneficiary cannot be null");
        }

        this.paymentId = paymentId;
        this.debitAccountId = debitAccountId;
        this.debitAmount = debitAmount;
        this.creditAmount = creditAmount;
        this.valueDate = valueDate;
        this.beneficiary = beneficiary;
        this.status = PaymentStatus.PENDING;
        this.feeAmount = Money.zero(debitAmount.currencyCode());
        this.createdAt = Instant.now();
    }

    public void setDebitTransactionId(UUID debitTransactionId) {
        this.debitTransactionId = debitTransactionId;
    }

    public void setFxContractId(UUID fxContractId) {
        this.fxContractId = fxContractId;
    }

    public void setFeeAmount(Money feeAmount) {
        if (!feeAmount.currencyCode().equals(debitAmount.currencyCode())) {
            throw new IllegalArgumentException("Fee currency must match debit amount currency");
        }
        this.feeAmount = feeAmount;
    }

    public void markAsProcessing() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Can only mark PENDING payments as PROCESSING");
        }
        this.status = PaymentStatus.PROCESSING;
    }

    public void markAsCompleted() {
        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Can only mark PROCESSING payments as COMPLETED");
        }
        this.status = PaymentStatus.COMPLETED;
    }

    public void markAsFailed(String reason) {
        if (status == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Cannot mark COMPLETED payments as FAILED");
        }
        this.status = PaymentStatus.FAILED;
    }

    public void reverse(String reason) {
        if (status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Can only reverse COMPLETED payments");
        }
        this.status = PaymentStatus.REVERSED;
    }

    public void recordDebitTransaction(UUID debitTransactionId) {
        this.debitTransactionId = debitTransactionId;
    }

    public void recordFxContract(UUID fxContractId) {
        this.fxContractId = fxContractId;
    }

    public boolean requiresFx() {
        return !debitAmount.currencyCode().equals(creditAmount.currencyCode());
    }
}
