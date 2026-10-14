package com.racehorse.medical;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record WeightRecordRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 2)
        BigDecimal weightKg,
        @PastOrPresent Instant recordedAt) {}
