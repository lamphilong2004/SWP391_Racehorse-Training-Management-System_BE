package com.racehorse.medical.restriction.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.racehorse.medical.restriction.TrainingAvailability;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrainingAvailabilityResponse(
        long horseId,
        TrainingAvailability availability,
        String reason,
        Long activeRestrictionId,
        List<ExerciseRuleResponse> exerciseRules) {
}