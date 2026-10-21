package com.racehorse.training.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public final class Core028Api {
    private Core028Api() {}
    public record StopRequest(@NotBlank @Size(max=1000) String reason, boolean medicallyRelevant,
                              String incidentType, String observedSeverity) {}
    public record ReportIssueRequest(@NotBlank @Size(max=1000) String description,
                                     @NotBlank String incidentType,@NotBlank String observedSeverity,Instant observedAt) {}
    public record RiderFeedbackRequest(@NotNull Map<String,Object> responses) {}
    public record IssueResult(long incidentId,String incidentCode,String sessionStatus) {}
    public record Feedback(long id,long sessionId,long submittedBy,Instant submittedAt,Map<String,Object> responses) {}
}
