package com.acme.reliable.process;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ProcessStatus enum
 */
class ProcessStatusTest {

    @Test
    @DisplayName("Should have all expected status values")
    void testAllValues() {
        ProcessStatus[] values = ProcessStatus.values();

        assertThat(values).hasSize(7);
        assertThat(values).contains(
            ProcessStatus.NEW,
            ProcessStatus.RUNNING,
            ProcessStatus.SUCCEEDED,
            ProcessStatus.FAILED,
            ProcessStatus.COMPENSATING,
            ProcessStatus.COMPENSATED,
            ProcessStatus.PAUSED
        );
    }

    @Test
    @DisplayName("Should parse status from string")
    void testValueOf() {
        ProcessStatus status = ProcessStatus.valueOf("RUNNING");

        assertThat(status).isEqualTo(ProcessStatus.RUNNING);
    }

    @Test
    @DisplayName("Should throw exception for invalid status")
    void testValueOfInvalid() {
        assertThatThrownBy(() -> ProcessStatus.valueOf("INVALID"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should support equality comparison")
    void testEquality() {
        ProcessStatus status1 = ProcessStatus.RUNNING;
        ProcessStatus status2 = ProcessStatus.RUNNING;

        assertThat(status1).isEqualTo(status2);
        assertThat(status1 == status2).isTrue();
    }

    @Test
    @DisplayName("Should have correct ordinal values")
    void testOrdinals() {
        assertThat(ProcessStatus.NEW.ordinal()).isEqualTo(0);
        assertThat(ProcessStatus.RUNNING.ordinal()).isEqualTo(1);
        assertThat(ProcessStatus.SUCCEEDED.ordinal()).isEqualTo(2);
        assertThat(ProcessStatus.FAILED.ordinal()).isEqualTo(3);
        assertThat(ProcessStatus.COMPENSATING.ordinal()).isEqualTo(4);
        assertThat(ProcessStatus.COMPENSATED.ordinal()).isEqualTo(5);
        assertThat(ProcessStatus.PAUSED.ordinal()).isEqualTo(6);
    }

    @Test
    @DisplayName("Should convert to string")
    void testToString() {
        assertThat(ProcessStatus.RUNNING.toString()).isEqualTo("RUNNING");
        assertThat(ProcessStatus.SUCCEEDED.toString()).isEqualTo("SUCCEEDED");
        assertThat(ProcessStatus.FAILED.toString()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("Should support switch statements")
    void testSwitchStatement() {
        ProcessStatus status = ProcessStatus.SUCCEEDED;

        String result = switch (status) {
            case NEW -> "new";
            case RUNNING -> "running";
            case SUCCEEDED -> "success";
            case FAILED -> "failed";
            case COMPENSATING -> "compensating";
            case COMPENSATED -> "compensated";
            case PAUSED -> "paused";
        };

        assertThat(result).isEqualTo("success");
    }

    @Test
    @DisplayName("Should support terminal states identification")
    void testTerminalStates() {
        // Terminal states
        assertThat(isTerminal(ProcessStatus.SUCCEEDED)).isTrue();
        assertThat(isTerminal(ProcessStatus.FAILED)).isTrue();
        assertThat(isTerminal(ProcessStatus.COMPENSATED)).isTrue();

        // Non-terminal states
        assertThat(isTerminal(ProcessStatus.NEW)).isFalse();
        assertThat(isTerminal(ProcessStatus.RUNNING)).isFalse();
        assertThat(isTerminal(ProcessStatus.COMPENSATING)).isFalse();
        assertThat(isTerminal(ProcessStatus.PAUSED)).isFalse();
    }

    @Test
    @DisplayName("Should support active states identification")
    void testActiveStates() {
        // Active states
        assertThat(isActive(ProcessStatus.RUNNING)).isTrue();
        assertThat(isActive(ProcessStatus.COMPENSATING)).isTrue();

        // Non-active states
        assertThat(isActive(ProcessStatus.NEW)).isFalse();
        assertThat(isActive(ProcessStatus.SUCCEEDED)).isFalse();
        assertThat(isActive(ProcessStatus.FAILED)).isFalse();
        assertThat(isActive(ProcessStatus.COMPENSATED)).isFalse();
        assertThat(isActive(ProcessStatus.PAUSED)).isFalse();
    }

    // Helper methods for testing semantic groups
    private boolean isTerminal(ProcessStatus status) {
        return status == ProcessStatus.SUCCEEDED ||
               status == ProcessStatus.FAILED ||
               status == ProcessStatus.COMPENSATED;
    }

    private boolean isActive(ProcessStatus status) {
        return status == ProcessStatus.RUNNING ||
               status == ProcessStatus.COMPENSATING;
    }
}
