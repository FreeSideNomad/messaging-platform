package com.acme.payments.domain.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

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

    /**
     * Align timestamp to the start of the current time bucket.
     * This ensures all limits for a given period start at natural boundaries.
     */
    public Instant alignToBucketStart(Instant timestamp) {
        return switch (this) {
            case MINUTE -> timestamp.truncatedTo(ChronoUnit.MINUTES);
            case HOUR -> timestamp.truncatedTo(ChronoUnit.HOURS);
            case DAY -> timestamp.truncatedTo(ChronoUnit.DAYS);
            case WEEK -> {
                LocalDateTime ldt = LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC);
                LocalDate monday = ldt.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield monday.atStartOfDay().toInstant(ZoneOffset.UTC);
            }
            case MONTH -> {
                LocalDateTime ldt = LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC);
                LocalDate firstOfMonth = ldt.toLocalDate().withDayOfMonth(1);
                yield firstOfMonth.atStartOfDay().toInstant(ZoneOffset.UTC);
            }
        };
    }
}
