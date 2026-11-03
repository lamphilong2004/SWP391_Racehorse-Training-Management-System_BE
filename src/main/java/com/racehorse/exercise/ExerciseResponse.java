package com.racehorse.exercise;

public record ExerciseResponse(
        long id,
        String name,
        String exerciseType,
        String description,
        String targetMetric) {
}
