package com.acme.reliable.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Jsons utility class
 */
class JsonsTest {

    @Nested
    @DisplayName("toJson Tests")
    class ToJsonTests {

        @Test
        @DisplayName("Should serialize simple object to JSON")
        void testToJsonSimpleObject() {
            Map<String, String> map = Map.of("key", "value");
            String json = Jsons.toJson(map);

            assertThat(json).contains("key");
            assertThat(json).contains("value");
        }

        @Test
        @DisplayName("Should serialize empty map to JSON")
        void testToJsonEmptyMap() {
            Map<String, String> map = Map.of();
            String json = Jsons.toJson(map);

            assertThat(json).isEqualTo("{}");
        }

        @Test
        @DisplayName("Should serialize null values")
        void testToJsonWithNull() {
            Map<String, Object> map = new HashMap<>();
            map.put("key", null);
            String json = Jsons.toJson(map);

            assertThat(json).contains("key");
        }

        @Test
        @DisplayName("Should handle temporal types with JavaTimeModule")
        void testToJsonInstant() {
            Instant now = Instant.parse("2025-01-01T00:00:00Z");
            String json = Jsons.toJson(now);

            // Jackson serializes Instant as epoch seconds with nanoseconds
            assertThat(json).isNotEmpty();

            // Verify we can deserialize it back
            Instant deserialized = Jsons.fromJson(json, Instant.class);
            assertThat(deserialized).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("of Tests")
    class OfTests {

        @Test
        @DisplayName("Should create JSON from key-value pair")
        void testOf() {
            String json = Jsons.of("name", "John");

            assertThat(json).contains("name");
            assertThat(json).contains("John");
        }

        @Test
        @DisplayName("Should handle empty values")
        void testOfEmptyValue() {
            String json = Jsons.of("empty", "");

            assertThat(json).contains("empty");
        }
    }

    @Nested
    @DisplayName("fromJson Tests")
    class FromJsonTests {

        @Test
        @DisplayName("Should deserialize JSON to object")
        void testFromJson() {
            String json = "{\"key\":\"value\"}";

            @SuppressWarnings("unchecked")
            Map<String, String> map = Jsons.fromJson(json, Map.class);

            assertThat(map).containsEntry("key", "value");
        }

        @Test
        @DisplayName("Should deserialize complex object")
        void testFromJsonComplex() {
            String json = "{\"name\":\"John\",\"age\":30}";

            @SuppressWarnings("unchecked")
            Map<String, Object> map = Jsons.fromJson(json, Map.class);

            assertThat(map).containsEntry("name", "John");
            assertThat(map).containsKey("age");
        }

        @Test
        @DisplayName("Should throw exception for invalid JSON")
        void testFromJsonInvalid() {
            String invalidJson = "{invalid}";

            assertThatThrownBy(() -> Jsons.fromJson(invalidJson, Map.class))
                .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("merge Tests")
    class MergeTests {

        @Test
        @DisplayName("Should merge two maps")
        void testMerge() {
            Map<String, String> map1 = Map.of("a", "1", "b", "2");
            Map<String, String> map2 = Map.of("c", "3", "d", "4");

            Map<String, String> merged = Jsons.merge(map1, map2);

            assertThat(merged)
                .hasSize(4)
                .containsEntry("a", "1")
                .containsEntry("b", "2")
                .containsEntry("c", "3")
                .containsEntry("d", "4");
        }

        @Test
        @DisplayName("Should override values from first map with second")
        void testMergeOverride() {
            Map<String, String> map1 = Map.of("a", "1", "b", "2");
            Map<String, String> map2 = Map.of("b", "override", "c", "3");

            Map<String, String> merged = Jsons.merge(map1, map2);

            assertThat(merged)
                .hasSize(3)
                .containsEntry("a", "1")
                .containsEntry("b", "override")
                .containsEntry("c", "3");
        }

        @Test
        @DisplayName("Should handle empty maps")
        void testMergeEmpty() {
            Map<String, String> map1 = Map.of();
            Map<String, String> map2 = Map.of("a", "1");

            Map<String, String> merged = Jsons.merge(map1, map2);

            assertThat(merged)
                .hasSize(1)
                .containsEntry("a", "1");
        }

        @Test
        @DisplayName("Should merge both empty maps")
        void testMergeBothEmpty() {
            Map<String, String> map1 = Map.of();
            Map<String, String> map2 = Map.of();

            Map<String, String> merged = Jsons.merge(map1, map2);

            assertThat(merged).isEmpty();
        }
    }

    @Nested
    @DisplayName("toMap Tests")
    class ToMapTests {

        @Test
        @DisplayName("Should convert object to map")
        void testToMap() {
            TestObject obj = new TestObject("value1", 42);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = Jsons.toMap(obj);

            assertThat(map)
                .containsEntry("field1", "value1")
                .containsEntry("field2", 42);
        }

        @Test
        @DisplayName("Should convert nested object to map")
        void testToMapNested() {
            NestedObject nested = new NestedObject("outer", new TestObject("inner", 10));

            @SuppressWarnings("unchecked")
            Map<String, Object> map = Jsons.toMap(nested);

            assertThat(map)
                .containsKey("name")
                .containsKey("nested");
        }

        @Test
        @DisplayName("Should handle null fields")
        void testToMapNullFields() {
            TestObject obj = new TestObject(null, 0);

            @SuppressWarnings("unchecked")
            Map<String, Object> map = Jsons.toMap(obj);

            assertThat(map).containsKey("field2");
        }
    }

    // Test helper classes
    public record TestObject(String field1, int field2) {}
    public record NestedObject(String name, TestObject nested) {}
}
