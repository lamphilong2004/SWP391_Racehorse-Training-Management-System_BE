package com.racehorse.training.realtime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class Core030Api {
    private Core030Api(){}
    public record MetricRequest(@NotNull Instant recordedAt,Integer heartRate,BigDecimal speedKmh,BigDecimal distanceM){}
    public record MetricResult(long sampleId,String realtimeState,Long alertId){}
    public record AcknowledgeRequest(@NotNull String action,@Size(max=1000) String stopReason,Boolean medicallyRelevant,
                                    String incidentType,String observedSeverity){}
    public record UserRef(long id,String userCode,String fullName){}
    public record Alert(long id,long sessionId,String metric,BigDecimal metricValue,BigDecimal thresholdApplied,String severity,
                        Instant raisedAt,Instant acknowledgedAt,UserRef acknowledgedBy,String actionTaken,Instant escalatedToVetAt){}
}
