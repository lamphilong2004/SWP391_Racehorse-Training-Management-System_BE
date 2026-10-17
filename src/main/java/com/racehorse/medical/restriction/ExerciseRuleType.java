package com.racehorse.medical.restriction;

public enum ExerciseRuleType {
    ALLOW("ALLOWED"),
    PROHIBIT("PROHIBITED");

    private final String databaseValue;

    ExerciseRuleType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static ExerciseRuleType fromDatabaseValue(String databaseValue) {
        return switch (databaseValue) {
            case "ALLOWED" -> ALLOW;
            case "PROHIBITED" -> PROHIBIT;
            default -> throw new IllegalArgumentException("Unsupported persisted exercise rule type.");
        };
    }
}