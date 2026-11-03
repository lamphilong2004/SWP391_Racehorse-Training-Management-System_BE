package com.racehorse.medical.restriction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ExerciseRuleType {
    ALLOW("ALLOWED"),
    PROHIBIT("PROHIBITED");

    private final String databaseValue;

    ExerciseRuleType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    @JsonValue
    public String databaseValue() {
        return databaseValue;
    }

    @JsonCreator
    public static ExerciseRuleType fromDatabaseValue(String databaseValue) {
        return switch (databaseValue) {
            case "ALLOWED" -> ALLOW;
            case "PROHIBITED" -> PROHIBIT;
            default -> throw new IllegalArgumentException("Unsupported persisted exercise rule type.");
        };
    }
}
