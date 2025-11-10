package com.acme.payments.domain.repository;

import com.acme.payments.domain.model.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Payment aggregate
 */
public interface PaymentRepository {
    void save(Payment payment);

    Optional<Payment> findById(UUID paymentId);

    List<Payment> findByDebitAccountId(UUID debitAccountId);
}
