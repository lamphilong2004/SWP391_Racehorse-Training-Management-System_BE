package com.racehorse.medical.restriction.api;

import com.racehorse.medical.restriction.ExerciseRuleType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ExerciseRuleRequest(
        @Positive Long exerciseId,
        @NotNull ExerciseRuleType ruleType,
        @Size(min = 1, max = 200) String exerciseTypeNote) {
}