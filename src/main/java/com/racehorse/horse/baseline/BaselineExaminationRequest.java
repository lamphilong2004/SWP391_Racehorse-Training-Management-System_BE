package com.racehorse.horse.baseline;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record BaselineExaminationRequest(
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("9999.99")
        @Digits(integer = 4, fraction = 2)
        BigDecimal weightKg,
        @NotNull HorseHealthStatus healthStatus,
        String clinicalNotes,
        /** When the Veterinarian actually examined the horse; null = now. Not in the future. */
        Instant examinedAt) {
    public BaselineExaminationRequest(BigDecimal weightKg, HorseHealthStatus healthStatus, String clinicalNotes) {
        this(weightKg, healthStatus, clinicalNotes, null);
    }
}
