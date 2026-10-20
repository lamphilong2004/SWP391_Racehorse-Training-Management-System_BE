package com.racehorse.training.program;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProgramApi {
    private ProgramApi() {}

    public record Page<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}
    public record UserRef(long id, String userCode, String fullName) {}
    public record HorseRef(long id, String horseCode, String name) {}

    public record TemplatePhaseInput(
            @Positive int phaseOrder,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String objective,
            @Size(max = 100) String recommendedDuration,
            @Size(max = 500) String intensityGuidance) {}

    public record TemplateVersionRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String goals,
            @Size(max = 1000) String suitableHorseProfile,
            @Size(max = 500) String recommendedDistanceUseCase,
            @Size(max = 100) String recommendedDuration,
            @Size(max = 1000) String workloadIntensityGuidance,
            @Size(max = 500) String preferredSurfaces,
            @Size(max = 1000) String recoveryGuidance,
            List<@Positive Long> requiredSkillIds,
            @NotEmpty List<@Valid TemplatePhaseInput> phases) {}

    public record DuplicateTemplateRequest(@NotBlank @Size(max = 150) String name, @Positive Integer sourceVersionNo) {}

    public record TemplateSummary(long id, String templateCode, String status, int latestVersionNo,
                                  String name, String goals, Long duplicatedFromTemplateId, long usedByProgramCount) {}
    public record TemplateVersion(long id, int versionNo, String name, String goals, String suitableHorseProfile,
                                  String recommendedDistanceUseCase, String recommendedDuration,
                                  String workloadIntensityGuidance, String preferredSurfaces, String recoveryGuidance,
                                  List<SkillRef> requiredSkills, List<TemplatePhase> phases, UserRef createdBy,
                                  Instant createdAt) {}
    public record SkillRef(long id, String name) {}
    public record TemplatePhase(long id, int phaseOrder, String name, String objective, String recommendedDuration,
                                String intensityGuidance) {}
    public record TemplateDetail(TemplateSummary template, TemplateVersion version, List<VersionRef> versions) {}
    public record VersionRef(long id, int versionNo, Instant createdAt) {}
    public record TemplateRecommendation(long templateId,String templateCode,long templateVersionId,int versionNo,
                                         String name,String label,List<String> reasons) {}
    public record RecommendationCandidate(UserRef user,String experienceLevel,List<String> skills,String label,
                                          List<String> reasons,List<String> warnings,String hardBlock) {}
    public record CandidateList(List<RecommendationCandidate> candidates,String emptyReason) {}

    public record TrainerAssignmentRequest(@Positive long trainerUserId, @NotNull LocalDate effectiveFrom,
                                           @Size(max = 500) String reason, Boolean warningsAcknowledged) {}
    public record ProgramPhaseInput(Long id, Long templateVersionPhaseId, @Positive int phaseOrder,
                                    @NotBlank @Size(max = 150) String name, @Size(max = 1000) String objective,
                                    @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                    String intensityMin, String intensityMax, @Size(max = 500) String workload) {}
    public record CreateProgramRequest(@Positive long horseId, @Positive long templateVersionId,
                                       @NotBlank @Size(max = 150) String name, @Size(max = 2000) String goals,
                                       @Size(max = 1000) String targetMetrics, @NotNull LocalDate periodStart,
                                       @NotNull LocalDate periodEnd, @Size(max = 2000) String notes,
                                       @NotEmpty List<@Valid ProgramPhaseInput> phases,
                                       @NotNull @Valid TrainerAssignmentRequest trainer) {}

    public record ProgramSummary(long id, HorseRef horse, String name, String status, LocalDate periodStart,
                                 LocalDate periodEnd, long sourceTemplateId, int sourceTemplateVersion,
                                 UserRef currentTrainer, String currentPhaseName, String trainingAvailability) {}
    public record ProgramPhase(long id, Long templateVersionPhaseId, int phaseOrder, String name, String objective,
                               LocalDate startDate, LocalDate endDate, String intensityMin, String intensityMax,
                               String workload, boolean isCustomized) {}
    public record ProgramDetail(long id, HorseRef horse, String name, String status, LocalDate periodStart,
                                LocalDate periodEnd, long sourceTemplateId, int sourceTemplateVersion,
                                UserRef currentTrainer, String currentPhaseName, String trainingAvailability,
                                String sourceTemplateCode, String sourceTemplateName, String goals, String targetMetrics,
                                String notes, Instant activatedAt, UserRef createdBy, List<ProgramPhase> phases,
                                int pendingAdjustmentRequests) {}

    public record PhaseProgress(long phaseId, int phaseOrder, String name, LocalDate startDate, LocalDate endDate,
                                int scheduled, int completed, int interrupted, Double totalDistanceM,
                                String lastEvaluationLabel) {}
    public record ProgramProgress(long programId, List<PhaseProgress> phases) {}
    public record HistoryEntry(Instant occurredAt, String entryType, UserRef actor, String summary) {}
    public record SetProgramStatusRequest(@NotBlank String status, @Size(max = 2000) String reason) {}
    public record AdjustProgramPhasesRequest(Long adjustmentRequestId, @NotEmpty List<@Valid ProgramPhaseInput> phases) {}
}
