package com.racehorse.exercise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExerciseInput(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 50) String exerciseType,
        @Size(max = 2000) String description,
        @Size(max = 100) String targetMetric) {
}
