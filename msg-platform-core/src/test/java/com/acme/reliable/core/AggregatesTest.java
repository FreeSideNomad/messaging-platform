package com.acme.reliable.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for Aggregates utility class
 */
class AggregatesTest {

    @Test
    @DisplayName("snapshot - should create JSON snapshot with key")
    void testSnapshotWithKey() {
        String result = Aggregates.snapshot("user-123");

        assertThat(result).contains("\"aggregateKey\":\"user-123\"");
        assertThat(result).contains("\"version\":1");
    }

    @Test
    @DisplayName("snapshot - should handle various key formats")
    void testSnapshotVariousKeys() {
        assertThat(Aggregates.snapshot("order-456")).contains("order-456");

        assertThat(Aggregates.snapshot("payment:abc:xyz")).contains("payment:abc:xyz");

        assertThat(Aggregates.snapshot("simple")).contains("simple");
    }

    @Test
    @DisplayName("snapshot - should handle empty key")
    void testSnapshotEmptyKey() {
        String result = Aggregates.snapshot("");

        assertThat(result).contains("\"aggregateKey\":\"\"");
    }

    @Test
    @DisplayName("snapshot - should create valid JSON")
    void testSnapshotValidJson() {
        String result = Aggregates.snapshot("test-key");

        // Should be parseable as JSON
        assertThatNoException().isThrownBy(() -> Jsons.fromJson(result, java.util.Map.class));
    }
}
