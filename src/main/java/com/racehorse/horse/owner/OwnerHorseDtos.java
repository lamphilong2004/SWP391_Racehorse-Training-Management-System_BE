package com.racehorse.horse.owner;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class OwnerHorseDtos {

    private OwnerHorseDtos() {
    }

    @Schema(name = "HorseLifecycle")
    public enum Lifecycle {
        DRAFT,
        ACTIVE,
        INACTIVE
    }

    @Schema(name = "HealthStatus")
    public enum HealthStatus {
        FIT,
        MONITORING,
        INJURED,
        QUARANTINED
    }

    @Schema(name = "TrainingAvailability", description = "Derived from the horse's active medical restriction.")
    public enum TrainingAvailability {
        AVAILABLE,
        LIMITED,
        LOCKED
    }

    @Schema(name = "RecoveryStatus")
    public enum RecoveryStatus {
        NOT_RECOVERED,
        IMPROVING,
        RECOVERED
    }

    @Schema(name = "ProgramStatus")
    public enum ProgramStatus {
        ACTIVE,
        PAUSED,
        COMPLETED
    }

    @Schema(name = "SessionStatus")
    public enum SessionStatus {
        SCHEDULED,
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        INTERRUPTED
    }

    @Schema(name = "OwnerHorseCard")
    public record HorseCard(
            long id,
            String horseCode,
            String name,
            @Schema(nullable = true) String breed,
            @Schema(nullable = true) String sex,
            @Schema(nullable = true) LocalDate foaledDate,
            Lifecycle lifecycle,
            @Schema(nullable = true) HealthStatus healthStatus,
            TrainingAvailability trainingAvailability) {
    }

    @Schema(name = "ParentRef")
    public record ParentRef(
            @Schema(nullable = true) Long horseId,
            @Schema(nullable = true) String horseCode,
            @Schema(nullable = true) String name) {
    }

    @Schema(name = "Pedigree")
    public record Pedigree(ParentRef sire, ParentRef dam) {
    }

    @Schema(name = "OwnerHorseOwnership")
    public record Ownership(BigDecimal sharePercentage, LocalDate effectiveFrom, boolean isManagingOwner) {
    }

    @Schema(name = "OwnerHorseProfile")
    public record HorseProfile(
            HorseCard horse,
            @Schema(nullable = true) String countryOfOrigin,
            @Schema(nullable = true) String registrationNo,
            @Schema(nullable = true) Instant activatedAt,
            @Schema(nullable = true) String currentBoxCode,
            @Schema(nullable = true) LocalDate stableEffectiveFrom,
            Pedigree pedigree,
            Ownership myOwnership) {
    }

    @Schema(name = "WeightPoint")
    public record WeightPoint(BigDecimal weightKg, Instant recordedAt) {
    }

    @Schema(name = "OwnerHealthSummary", description = "Non-clinical owner projection; excludes findings and treatment details.")
    public record HealthSummary(
            @Schema(nullable = true) HealthStatus healthStatus,
            TrainingAvailability trainingAvailability,
            @Schema(nullable = true) LocalDate restrictionReviewDate,
            @Schema(nullable = true) RecoveryStatus recoveryStatus,
            @Schema(nullable = true) Instant lastExaminedAt,
            List<WeightPoint> weights) {

        public HealthSummary {
            weights = List.copyOf(weights);
        }
    }

    @Schema(name = "OwnerProgramSummary")
    public record ProgramSummary(
            String name,
            ProgramStatus status,
            LocalDate periodStart,
            LocalDate periodEnd,
            @Schema(nullable = true) String currentPhaseName,
            int sessionsCompleted,
            int sessionsPlanned) {
    }

    @Schema(name = "OwnerSessionSummary")
    public record SessionSummary(
            String sessionCode,
            Instant scheduledStart,
            SessionStatus status,
            @Schema(nullable = true) String evaluationLabel) {
    }

    @Schema(name = "OwnerTrainingSummary")
    public record TrainingSummary(
            @Schema(nullable = true) ProgramSummary currentProgram,
            List<SessionSummary> recentSessions) {

        public TrainingSummary {
            recentSessions = List.copyOf(recentSessions);
        }
    }
}
