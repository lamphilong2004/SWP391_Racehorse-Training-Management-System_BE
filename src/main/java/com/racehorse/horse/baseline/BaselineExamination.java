package com.racehorse.horse.baseline;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "BaselineExamination")
public record BaselineExamination(
        long id,
        long horseId,
        BigDecimal weightKg,
        HorseHealthStatus healthStatus,
        Instant recordedAt) {
}
