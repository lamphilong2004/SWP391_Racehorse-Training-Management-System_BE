package com.racehorse.medical.incident;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class IncidentApi {
    private IncidentApi() {}

    public record CreateRequest(@Positive Long horseId, @Positive Long preSessionCheckId,
                                @Positive Long trainingSessionId, @Positive Long careTaskId,
                                @NotNull String incidentType, @NotNull String observedSeverity,
                                @Size(max=2000) String description, @NotNull OffsetDateTime observedAt) {}

    public record ReviewRequest(@NotNull String action, @Positive Long medicalCaseId) {}

    public record UserRef(long id, String fullName) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Incident(long id, String incidentCode, long horseId, long reportedByUserId,
                           Long medicalCaseId, Long preSessionCheckId, Long trainingSessionId, Long careTaskId,
                           String incidentType, String observedSeverity, String description,
                           OffsetDateTime observedAt, OffsetDateTime reportedAt, String status) {}

    public record Page(List<Incident> items, int page, int size, long totalElements) {}

    public record PreSessionCheckRequest(@NotNull Map<String, @Valid CheckItem> checks) {}
    public record CheckItem(@NotNull String result, @Size(max=1000) String observation) {}
    public record PreSessionCheck(long id, long sessionId, Map<String, CheckItem> checks,
                                  String overallResult, UserRef performedBy, OffsetDateTime performedAt) {}
}
