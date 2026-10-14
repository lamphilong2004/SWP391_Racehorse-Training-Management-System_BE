package com.racehorse.medical;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BaselineExaminationRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 2)
        BigDecimal weightKg,
        @NotNull HealthStatus healthStatus,
        String clinicalNotes) {}
