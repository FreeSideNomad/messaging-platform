package com.acme.reliable.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.HashMap;
import java.util.Map;

public final class Jsons {
    private static final ObjectMapper M = new ObjectMapper().registerModule(new JavaTimeModule());

    public static String toJson(Object o) {
        try {
            return M.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String of(String k, String v) {
        return toJson(Map.of(k, v));
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return M.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, String> merge(Map<String, String> a, Map<String, String> b) {
        var m = new HashMap<String, String>();
        m.putAll(a);
        m.putAll(b);
        return m;
    }

    /**
     * Convert an object to a Map<String, Object> by serializing through Jackson. Useful for
     * converting domain objects to process data.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object o) {
        try {
            return M.convertValue(o, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
