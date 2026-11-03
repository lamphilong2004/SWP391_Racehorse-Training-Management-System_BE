package com.racehorse.training.adjustment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AdjustmentApi {
    private AdjustmentApi() {}

    public record CreateRequest(@Positive Long horseProgramPhaseId, @NotNull String requestType,
            @Size(max = 1000) String currentValue, @NotBlank @Size(max = 1000) String requestedValue,
            @NotBlank @Size(max = 2000) String reason, @Size(max = 2000) String evidenceNotes) {}
    public record DecisionRequest(@Size(max = 2000) String decisionReason) {}
    public record RejectRequest(@NotBlank @Size(max = 2000) String decisionReason) {}
    public record UserRef(long id, String userCode, String fullName) {}
    public record HorseRef(long id, String horseCode, String name) {}
    public record AdjustmentRequest(long id, long programId, Long horseProgramPhaseId, String requestType,
            String currentValue, String requestedValue, String reason, String evidenceNotes, String status,
            UserRef requestedBy, Instant requestedAt, UserRef reviewedBy, Instant reviewedAt,
            String decisionReason, HorseRef horse) {}
    public record Page(java.util.List<AdjustmentRequest> items, int page, int size, long totalItems, int totalPages) {}
}
