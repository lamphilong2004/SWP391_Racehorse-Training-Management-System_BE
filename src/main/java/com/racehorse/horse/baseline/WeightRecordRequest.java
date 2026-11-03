package com.racehorse.horse.baseline;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record WeightRecordRequest(
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("9999.99")
        @Digits(integer = 4, fraction = 2)
        BigDecimal weightKg,
        Instant recordedAt) {
}
