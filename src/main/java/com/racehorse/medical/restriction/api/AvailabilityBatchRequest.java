package com.racehorse.medical.restriction.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AvailabilityBatchRequest(
        @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> horseIds) {
}