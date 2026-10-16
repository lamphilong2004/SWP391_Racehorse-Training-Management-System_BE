package com.racehorse.core035;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class MedicalDtos {
    private MedicalDtos() {}
    public record CaseCreate(@NotNull @Positive Long horseId, @Positive Long incidentId,
            @NotBlank @Size(max = 2000) String reason) {
        @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("Unsupported field: " + name); }
    }
    public record ExamCreate(@NotBlank String symptoms, @Size(max = 1000) String diagnosis,
            ClinicalSeverity severity, @NotNull Instant examinedAt, HealthStatus healthStatus,
            @Size(max = 500) String bodyCondition) {
        @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("Unsupported field: " + name); }
    }
    public record ReexamCreate(@NotNull RecoveryStatus recoveryStatus, @NotBlank String findings,
            @NotNull Instant examinedAt, HealthStatus healthStatus, @Size(max = 1000) String diagnosis,
            ClinicalSeverity severity, @Size(max = 500) String bodyCondition) {
        @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("Unsupported field: " + name); }
    }
    public record InjuryCreate(@NotBlank @Size(max = 100) String bodyRegion,
            @NotNull ClinicalSeverity severity, @NotNull RecoveryStatus recoveryStatus,
            @Positive Long veterinaryExaminationId, @Size(max = 100) String position3d,
            @Size(max = 20) String side) {
        @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("Unsupported field: " + name); }
    }
    public record GroomInstruction(@NotBlank @Size(max = 2000) String instruction,
            @NotNull LocalDate taskDate, @Size(max = 50) String scheduledSlot) {}
    public record PlanPut(@NotBlank String treatmentMedicationInstructions,
            @Size(max = 100) String restInstructions, @Size(max = 2000) String careInstructions,
            List<@Valid GroomInstruction> groomTasks, LocalDate reexaminationDate) {
        @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("Unsupported field: " + name); }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaseSummary(long id, long horseId, CaseStatus status, Instant openedAt, Instant closedAt) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CaseDetail(long id, long horseId, CaseStatus status, Instant openedAt, Instant closedAt,
            String reason, List<Long> incidentIds, List<ExamView> examinations, List<InjuryView> injuries,
            PlanView treatmentPlan, List<ReexamView> reexaminations) {}
    public record Page<T>(List<T> items, int page, int size, long totalElements) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExamView(long id, long caseId, long horseId, Instant examinedAt, String symptoms,
            String diagnosis, ClinicalSeverity severity, HealthStatus healthStatus, String bodyCondition,
            RecoveryStatus recoveryStatus) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReexamView(long id, long caseId, long horseId, RecoveryStatus recoveryStatus,
            String findings, Instant examinedAt, HealthStatus healthStatus, String diagnosis,
            ClinicalSeverity severity, String bodyCondition) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InjuryView(long id, long caseId, String bodyRegion, ClinicalSeverity severity,
            RecoveryStatus recoveryStatus, Long veterinaryExaminationId, String position3d, String side) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlanView(long id, long caseId, String treatmentMedicationInstructions,
            String restInstructions, String careInstructions, LocalDate reexaminationDate, Instant createdAt) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TaskView(long id, long horseId, String instruction, LocalDate taskDate,
            String scheduledSlot, String status, Instant completedAt) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VetTaskView(long id, long horseId, long caseId, long treatmentPlanId,
            String instruction, LocalDate taskDate, String scheduledSlot, String status,
            Instant completedAt, Long completedByUserId) {}
}
