package com.racehorse.medical.threshold;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class ThresholdApi {
    private ThresholdApi() {}

    public record Threshold(String metricType, BigDecimal thresholdValue, String scope, Long horseId,
                            OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo) {}

    public record OverrideRequest(
            @NotNull String metricType,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) @DecimalMax("99999.99")
            @Digits(integer = 5, fraction = 2) BigDecimal thresholdValue,
            @jakarta.validation.constraints.Size(max = 1000) String reason,
            @NotNull OffsetDateTime effectiveFrom) {}

    public record EndRequest(@NotNull OffsetDateTime effectiveTo) {}
}
