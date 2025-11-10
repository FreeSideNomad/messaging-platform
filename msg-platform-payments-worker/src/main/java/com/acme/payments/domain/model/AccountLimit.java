package com.acme.payments.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for Account Limit
 */
@Getter
public class AccountLimit {
    private final UUID limitId;
    private final UUID accountId;
    private final PeriodType periodType;
    private final Instant startTime;
    private final Instant endTime;
    private final Money limitAmount;
    private Money utilized;

    public AccountLimit(
            UUID limitId, UUID accountId, PeriodType periodType, Instant startTime, Money limitAmount) {
        if (limitId == null) {
            throw new IllegalArgumentException("Limit ID cannot be null");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (periodType == null) {
            throw new IllegalArgumentException("Period type cannot be null");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Start time cannot be null");
        }
        if (limitAmount == null || !limitAmount.isPositive()) {
            throw new IllegalArgumentException("Limit amount must be positive");
        }

        this.limitId = limitId;
        this.accountId = accountId;
        this.periodType = periodType;
        this.startTime = startTime;
        this.endTime = periodType.calculateEndTime(startTime);
        this.limitAmount = limitAmount;
        this.utilized = Money.zero(limitAmount.currencyCode());
    }

    /**
     * Book (reserve) an amount against this limit
     */
    public void book(Money amount) {
        if (!amount.currencyCode().equals(limitAmount.currencyCode())) {
            throw new IllegalArgumentException("Amount currency must match limit currency");
        }

        Money newUtilized = utilized.add(amount);
        if (newUtilized.greaterThan(limitAmount)) {
            throw new LimitExceededException(limitId, periodType, limitAmount, utilized, amount);
        }

        utilized = newUtilized;
    }

    /**
     * Reverse (release) a previously booked amount
     */
    public void reverse(Money amount) {
        if (!amount.currencyCode().equals(limitAmount.currencyCode())) {
            throw new IllegalArgumentException("Amount currency must match limit currency");
        }

        Money newUtilized = utilized.subtract(amount);
        if (newUtilized.isNegative()) {
            utilized = Money.zero(limitAmount.currencyCode());
        } else {
            utilized = newUtilized;
        }
    }

    public boolean isExpired() {
        return periodType.isExpired(endTime);
    }

    public Money getAvailable() {
        return limitAmount.subtract(utilized);
    }

    /**
     * Exception thrown when limit is exceeded
     */
    @Getter
    public static class LimitExceededException extends RuntimeException {
        private final UUID limitId;
        private final PeriodType periodType;
        private final Money limitAmount;
        private final Money utilized;
        private final Money requestedAmount;

        public LimitExceededException(
                UUID limitId,
                PeriodType periodType,
                Money limitAmount,
                Money utilized,
                Money requestedAmount) {
            super(
                    String.format(
                            "Limit exceeded for %s: limit=%s, utilized=%s, requested=%s, available=%s",
                            periodType,
                            limitAmount.amount(),
                            utilized.amount(),
                            requestedAmount.amount(),
                            limitAmount.subtract(utilized).amount()));
            this.limitId = limitId;
            this.periodType = periodType;
            this.limitAmount = limitAmount;
            this.utilized = utilized;
            this.requestedAmount = requestedAmount;
        }
    }
}
