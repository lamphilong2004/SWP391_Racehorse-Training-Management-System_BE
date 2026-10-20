package com.racehorse.medical.restriction.api;

import com.racehorse.medical.restriction.RestrictionLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MedicalRestrictionCreateRequest(
        @NotNull RestrictionLevel level,
        @NotBlank @Size(max = 1000) String reason,
        @NotNull OffsetDateTime effectiveAt,
        LocalDate reviewDate,
        @Positive Long medicalCaseId) {
}