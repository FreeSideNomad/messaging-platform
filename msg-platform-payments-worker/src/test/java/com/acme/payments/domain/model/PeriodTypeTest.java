package com.acme.payments.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PeriodType time bucket alignment
 */
class PeriodTypeTest {

    @Test
    @DisplayName("MINUTE should align to start of current minute")
    void testMinuteAlignment() {
        // Given: A timestamp in the middle of a minute
        Instant timestamp = Instant.parse("2025-01-15T14:23:45.678Z");

        // When: Aligning to minute bucket
        Instant aligned = PeriodType.MINUTE.alignToBucketStart(timestamp);

        // Then: Should truncate to start of minute
        assertThat(aligned).isEqualTo(Instant.parse("2025-01-15T14:23:00.000Z"));
    }

    @Test
    @DisplayName("HOUR should align to start of current hour")
    void testHourAlignment() {
        // Given: A timestamp in the middle of an hour
        Instant timestamp = Instant.parse("2025-01-15T14:23:45.678Z");

        // When: Aligning to hour bucket
        Instant aligned = PeriodType.HOUR.alignToBucketStart(timestamp);

        // Then: Should truncate to start of hour
        assertThat(aligned).isEqualTo(Instant.parse("2025-01-15T14:00:00.000Z"));
    }

    @Test
    @DisplayName("DAY should align to start of current day")
    void testDayAlignment() {
        // Given: A timestamp in the middle of a day
        Instant timestamp = Instant.parse("2025-01-15T14:23:45.678Z");

        // When: Aligning to day bucket
        Instant aligned = PeriodType.DAY.alignToBucketStart(timestamp);

        // Then: Should truncate to start of day
        assertThat(aligned).isEqualTo(Instant.parse("2025-01-15T00:00:00.000Z"));
    }

    @Test
    @DisplayName("WEEK should align to Monday 00:00:00 of current week")
    void testWeekAlignment_Wednesday() {
        // Given: Wednesday, January 15, 2025 at 14:23
        Instant timestamp = Instant.parse("2025-01-15T14:23:45.678Z");

        // When: Aligning to week bucket
        Instant aligned = PeriodType.WEEK.alignToBucketStart(timestamp);

        // Then: Should align to Monday, January 13, 2025 at 00:00:00
        assertThat(aligned).isEqualTo(Instant.parse("2025-01-13T00:00:00.000Z"));
    }

    @Test
    @DisplayName("WEEK should align to Monday 00:00:00 when already on Monday")
    void testWeekAlignment_Monday() {
        // Given: Monday, January 13, 2025 at 14:23
        Instant timestamp = Instant.parse("2025-01-13T14:23:45.678Z");

        // When: Aligning to week bucket
        Instant aligned = PeriodType.WEEK.alignToBucketStart(timestamp);

        // Then: Should stay on same Monday at 00:00:00
        assertThat(aligned).isEqualTo(Instant.parse("2025-01-13T00:00:00.000Z"));
    }

    @Test
    @DisplayName("WEEK should align to Monday 00:00:00 for Sunday")
    void testWeekAlignment_Sunday() {
        // Given: Sunday, January 19, 2025 at 14:23
        Instant timestamp = Instant.parse("2025-01-19T14:23:45.678Z");

        // When: Aligning to week bucket
        Instant aligned = PeriodType.WEEK.alignToBucketStart(timestamp);

        // Then: Should align to previous Monday, January 13, 2025 at 00:00:00
        assertThat(aligned).isEqualTo(Instant.parse("2025-01-13T00:00:00.000Z"));
    }

    @Test
    @DisplayName("MONTH should align to first day of current month at 00:00:00")
    void testMonthAlignment_MiddleOfMonth() {
        // Given: January 15, 2025 at 14:23
        Instant timestamp = Instant.parse("2025-01-15T14:23:45.678Z");

        // When: Aligning to month bucket
        Instant aligned = PeriodType.MONTH.alignToBucketStart(timestamp);

        // Then: Should align to January 1, 2025 at 00:00:00
        assertThat(aligned).isEqualTo(Instant.parse("2025-01-01T00:00:00.000Z"));
    }

    @Test
    @DisplayName("MONTH should align to first day when already on first day")
    void testMonthAlignment_FirstDay() {
        // Given: January 1, 2025 at 14:23
        Instant timestamp = Instant.parse("2025-01-01T14:23:45.678Z");

        // When: Aligning to month bucket
        Instant aligned = PeriodType.MONTH.alignToBucketStart(timestamp);

        // Then: Should stay on same day at 00:00:00
        assertThat(aligned).isEqualTo(Instant.parse("2025-01-01T00:00:00.000Z"));
    }

    @Test
    @DisplayName("MONTH should align to first day for end of month")
    void testMonthAlignment_EndOfMonth() {
        // Given: January 31, 2025 at 23:59
        Instant timestamp = Instant.parse("2025-01-31T23:59:59.999Z");

        // When: Aligning to month bucket
        Instant aligned = PeriodType.MONTH.alignToBucketStart(timestamp);

        // Then: Should align to January 1, 2025 at 00:00:00
        assertThat(aligned).isEqualTo(Instant.parse("2025-01-01T00:00:00.000Z"));
    }

    @Test
    @DisplayName("Alignment should be idempotent")
    void testAlignmentIdempotence() {
        // Given: A timestamp
        Instant timestamp = Instant.parse("2025-01-15T14:23:45.678Z");

        // When: Aligning twice
        Instant aligned1 = PeriodType.DAY.alignToBucketStart(timestamp);
        Instant aligned2 = PeriodType.DAY.alignToBucketStart(aligned1);

        // Then: Second alignment should produce same result
        assertThat(aligned1).isEqualTo(aligned2);
    }

    @Test
    @DisplayName("calculateEndTime should add duration to start time")
    void testCalculateEndTime() {
        // Given: A start time
        Instant startTime = Instant.parse("2025-01-15T00:00:00.000Z");

        // When: Calculating end times for different periods
        Instant hourEnd = PeriodType.HOUR.calculateEndTime(startTime);
        Instant dayEnd = PeriodType.DAY.calculateEndTime(startTime);
        Instant weekEnd = PeriodType.WEEK.calculateEndTime(startTime);
        Instant monthEnd = PeriodType.MONTH.calculateEndTime(startTime);

        // Then: Should add the correct duration
        assertThat(hourEnd).isEqualTo(startTime.plus(1, ChronoUnit.HOURS));
        assertThat(dayEnd).isEqualTo(startTime.plus(1, ChronoUnit.DAYS));
        assertThat(weekEnd).isEqualTo(startTime.plus(7, ChronoUnit.DAYS));
        assertThat(monthEnd).isEqualTo(startTime.plus(30, ChronoUnit.DAYS));
    }
}
