package com.racehorse.training.session;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SessionApi {
    private SessionApi() {}

    public record ExerciseInput(@Positive long exerciseDefinitionId, @Positive int exerciseOrder,
                                Double distanceM, Double durationMin, Integer repetitions, Double restIntervalMin,
                                String intensity, @Size(max = 50) String surface,
                                @Size(max = 1000) String acknowledgementNote) {}
    public record CreateRequest(@Positive long horseProgramPhaseId, @NotNull Instant scheduledStart,
                                Instant scheduledEnd, @Size(max = 50) String surface,
                                @Size(max = 200) String focus, String intensity,
                                @Positive long workRiderUserId, Boolean riderWarningAcknowledged,
                                @NotEmpty List<@Valid ExerciseInput> exercises) {}
    public record DraftRequest(@Positive long horseProgramPhaseId, @NotNull Instant scheduledStart,
                               Instant scheduledEnd, String intensity, Long workRiderUserId,
                               List<@Valid ExerciseInput> exercises) {}
    public record UpdateRequest(Instant scheduledStart, Instant scheduledEnd, @Size(max=50) String surface,
                                @Size(max=200) String focus, String intensity, @Positive Long workRiderUserId,
                                Boolean riderWarningAcknowledged) {}
    public record ReplaceExercisesRequest(@NotEmpty List<@Valid ExerciseInput> exercises) {}
    public record Exercise(long id, long exerciseDefinitionId, String exerciseName, String exerciseType,
                          int exerciseOrder, Double distanceM, Double durationMin, Integer repetitions,
                          Double restIntervalMin, String intensity, String surface, String suitabilityCategory,
                          String acknowledgementNote) {}
    public record UserRef(long id,String userCode,String fullName) {}
    public record IntensityRequest(@NotNull String intensity) {}
    public record GroomAssignmentRequest(@Positive long groomUserId) {}
    public record HorseRef(long id,String horseCode,String name) {}
    public record Session(long id, String sessionCode, HorseRef horse, long programId, String phaseName,
                          Instant scheduledStart, Instant scheduledEnd, String status, UserRef workRider,
                          UserRef trainer, String preCheckResult, boolean blocked, List<String> blockedReasons,
                          String blockedMessage, long horseProgramPhaseId, String surface, String focus, String intensity,
                          Instant actualStart, Instant actualEnd, String interruptionReason,
                          String trainingAvailability, List<Exercise> exercises, com.racehorse.medical.incident.IncidentApi.PreSessionCheck preSessionCheck,
                          List<Long> linkedIncidentIds, boolean hasRiderFeedback, boolean hasEvaluation, UserRef createdBy, UserRef groom) {}
    public record SuitabilityItem(int exerciseOrder, long exerciseDefinitionId, String category,
                                  List<String> messages, List<String> allowedActions) {}
    public record SessionIssue(String category, String field, String message) {}
    public record SuitabilityResult(String trainingAvailability, boolean sessionBlocked, String blockedMessage,
                                    List<SuitabilityItem> exercises, List<SessionIssue> sessionIssues) {}
    public record Candidate(UserRef user, String experienceLevel,
                            String label, List<String> reasons, List<String> warnings, String hardBlock,
                            List<String> skills) {}
    public record CandidateList(List<Candidate> candidates, String emptyReason) {}
}
