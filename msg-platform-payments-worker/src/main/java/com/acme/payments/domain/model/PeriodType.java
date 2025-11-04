package com.acme.payments.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Time period types for limits
 */
public enum PeriodType {
    MINUTE(Duration.ofMinutes(1)),
    HOUR(Duration.ofHours(1)),
    DAY(Duration.ofDays(1)),
    WEEK(Duration.ofDays(7)),
    MONTH(Duration.ofDays(30));

    private final Duration duration;

    PeriodType(Duration duration) {
        this.duration = duration;
    }

    public Duration getDuration() {
        return duration;
    }

    public Instant calculateEndTime(Instant startTime) {
        return startTime.plus(duration);
    }

    public boolean isExpired(Instant endTime) {
        return Instant.now().isAfter(endTime);
    }
}
