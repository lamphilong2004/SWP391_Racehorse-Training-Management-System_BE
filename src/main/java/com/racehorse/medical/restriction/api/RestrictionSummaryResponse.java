package com.racehorse.medical.restriction.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.racehorse.medical.restriction.RestrictionLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RestrictionSummaryResponse(
        long id,
        long horseId,
        RestrictionLevel level,
        String reason,
        Instant effectiveAt,
        Instant liftedAt,
        LocalDate reviewDate,
        List<ExerciseRuleResponse> exerciseRules) {
}