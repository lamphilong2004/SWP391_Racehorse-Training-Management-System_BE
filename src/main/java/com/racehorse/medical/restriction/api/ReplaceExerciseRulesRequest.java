package com.racehorse.medical.restriction.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceExerciseRulesRequest(@NotNull List<@Valid ExerciseRuleRequest> rules) {
}