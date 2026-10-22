package com.racehorse.medical.restriction.api;

import com.racehorse.medical.restriction.ExerciseRuleType;

public record ExerciseRuleResponse(Long exerciseId, ExerciseRuleType ruleType, String exerciseTypeNote) {
}