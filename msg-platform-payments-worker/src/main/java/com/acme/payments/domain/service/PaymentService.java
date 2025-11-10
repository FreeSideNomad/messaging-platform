package com.acme.payments.domain.service;

import com.acme.payments.application.command.CreatePaymentCommand;
import com.acme.payments.domain.model.Payment;
import com.acme.payments.domain.repository.PaymentRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Domain service for Payment operations
 */
@Singleton
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment createPayment(CreatePaymentCommand cmd) {
        log.info(
                "Creating payment from account {} debit={} credit={}",
                cmd.debitAccountId(),
                cmd.debitAmount(),
                cmd.creditAmount());

        Payment payment =
                new Payment(
                        UUID.randomUUID(),
                        cmd.debitAccountId(),
                        cmd.debitAmount(),
                        cmd.creditAmount(),
                        cmd.valueDate(),
                        cmd.beneficiary());

        paymentRepository.save(payment);
        log.info("Payment created: {}", payment.getPaymentId());

        return payment;
    }

    @Transactional
    public Payment updatePayment(Payment payment) {
        paymentRepository.save(payment);
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getPaymentById(UUID paymentId) {
        return paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(UUID paymentId) {
            super("Payment not found: " + paymentId);
        }
    }
}
