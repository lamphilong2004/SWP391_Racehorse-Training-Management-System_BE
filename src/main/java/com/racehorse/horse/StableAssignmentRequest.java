package com.racehorse.horse;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StableAssignmentRequest(
        @NotNull @Positive Long stableBoxId,
        @NotNull LocalDate effectiveFrom,
        @Size(max = 500) String reason) {
}
