package com.acme.payments.domain.model;

/**
 * Payment lifecycle status
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REVERSED
}
