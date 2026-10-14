package com.racehorse.core026;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * CORE-005/006 wire models; actors and incident status are never supplied by
 * clients.
 */
public final class Contracts {
    private Contracts() {
    }

    public enum Role {
        CLUB_MANAGER, HEAD_TRAINER, TRAINER, WORK_RIDER, VETERINARIAN, GROOM, HORSE_OWNER
    }

    public enum IncidentType {
        OFF_FEED, COLIC_SIGNS, FEVER, HOOF_OR_LEG_PROBLEM, OTHER
    }

    public enum Severity {
        LOW, MEDIUM, HIGH
    }

    public enum IncidentStatus {
        REPORTED, UNDER_REVIEW, RESOLVED
    }

    public enum CheckResult {
        ALL_NORMAL, ABNORMAL
    }

    public record Actor(long id, Set<Role> roles) {
        public Actor {
            if (id <= 0)
                throw new IllegalArgumentException("Positive actor ID required");
            roles = Set.copyOf(roles);
        }

        public boolean has(Role role) {
            return roles.contains(role);
        }
    }

    public record IncidentCreateRequest(@NotNull @Positive Long horseId, @Positive Long preSessionCheckId,
            @Positive Long trainingSessionId, @Positive Long careTaskId, @NotNull IncidentType incidentType,
            @NotNull Severity observedSeverity, @Size(max = 2000) String description, @NotNull Instant observedAt) {
        @JsonAnySetter
        public void rejectUnknown(String field, Object value) {
            throw new IllegalArgumentException("Unsupported incident field: " + field);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Incident(long id, String incidentCode, long horseId, long reportedByUserId, Long medicalCaseId,
            Long preSessionCheckId, Long trainingSessionId, Long careTaskId, IncidentType incidentType,
            Severity observedSeverity, String description, Instant observedAt, Instant reportedAt,
            IncidentStatus status) {
    }

    public record IncidentsPage(List<Incident> items, int page, int size, long totalElements) {
    }

    public record IncidentInput(@NotNull IncidentType incidentType, @NotNull Severity observedSeverity,
            @Size(max = 2000) String description) {
    }

    /** DEMO INTEGRATION: approved 5-item checklist (Figma SCR-TRAIN-007); overall result is derived server-side. */
    public enum ItemResult {
        NORMAL, ABNORMAL
    }

    public record CheckItem(@NotNull ItemResult result, @Size(max = 500) String observation) {
    }

    public record CheckItems(@NotNull @Valid CheckItem generalCondition, @NotNull @Valid CheckItem appetiteBehaviour,
            @NotNull @Valid CheckItem legsHooves, @NotNull @Valid CheckItem movement,
            @NotNull @Valid CheckItem temperature, @DecimalMin("30") @DecimalMax("45") BigDecimal temperatureC) {
        public CheckResult derived() {
            boolean abnormal = java.util.stream.Stream.of(generalCondition, appetiteBehaviour, legsHooves, movement,
                    temperature).anyMatch(i -> i != null && i.result() == ItemResult.ABNORMAL);
            return abnormal ? CheckResult.ABNORMAL : CheckResult.ALL_NORMAL;
        }
    }

    public record PreSessionCheckRequest(@NotNull @Valid CheckItems items, @Size(max = 1000) String notes,
            @Valid IncidentInput incident) {
        @JsonAnySetter
        public void rejectUnknown(String field, Object value) {
            throw new IllegalArgumentException("Unsupported pre-session check field: " + field);
        }
    }

    public record UserRef(long id, String userCode, String fullName) {
    }

    public record HorseRef(long id, String horseCode, String name) {
    }

    public record PreSessionCheck(long id, long sessionId, CheckResult overallResult, String observations,
            UserRef performedBy, Instant performedAt, Long incidentId) {
    }

    public record SessionSummary(long id, String sessionCode, HorseRef horse, long programId, String phaseName,
            Instant scheduledStart, Instant scheduledEnd, String status, UserRef workRider, UserRef trainer,
            CheckResult preCheckResult, boolean blocked, List<String> blockedReasons, String blockedMessage) {
    }
}
