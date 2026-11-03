package com.racehorse.training.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class EvaluationApi {
    private EvaluationApi() {}
    public record EvaluationRequest(@NotBlank @Pattern(regexp = "MET|PARTIALLY_MET|NOT_MET") String result,
            @Size(max = 2000) String evaluatedSummary, @Size(max = 2000) String evaluationNotes,
            @Size(max = 2000) String recommendation) {}
    public record UserRef(long id, String userCode, String fullName) {}
    public record PerformancePoint(long sessionId, String sessionCode, java.time.LocalDate date, String status,
            java.math.BigDecimal totalDistanceM, Double durationMin, Integer maxHeartRate,
            java.math.BigDecimal avgSpeedKmh, String result) {}
}
