package com.racehorse.horse;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OwnershipRequest(
        @Positive long ownerUserId,
        @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "100.00") BigDecimal sharePercentage,
        @NotNull LocalDate effectiveFrom,
        Boolean isManagingOwner) {
}
