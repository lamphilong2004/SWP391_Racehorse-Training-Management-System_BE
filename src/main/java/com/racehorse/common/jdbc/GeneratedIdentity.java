package com.racehorse.common.jdbc;

import org.springframework.jdbc.support.KeyHolder;

import java.util.List;
import java.util.Map;

public final class GeneratedIdentity {

    private GeneratedIdentity() {
    }

    public static long requireId(KeyHolder keyHolder) {
        if (keyHolder == null) {
            throw missingIdentity();
        }
        List<Map<String, Object>> rows = keyHolder.getKeyList();
        if (rows.size() != 1 || rows.get(0) == null) {
            throw missingIdentity();
        }

        Object value = null;
        int matchingColumns = 0;
        for (Map.Entry<String, Object> entry : rows.get(0).entrySet()) {
            if ("id".equalsIgnoreCase(entry.getKey())
                    || "GENERATED_KEYS".equalsIgnoreCase(entry.getKey())) {
                value = entry.getValue();
                matchingColumns++;
            }
        }
        if (matchingColumns != 1 || !(value instanceof Number id)) {
            throw missingIdentity();
        }
        return id.longValue();
    }

    private static IllegalStateException missingIdentity() {
        return new IllegalStateException(
                "The database did not return one generated identity under id or GENERATED_KEYS.");
    }
}
