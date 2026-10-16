package com.racehorse.medical;

import java.math.BigDecimal;
import java.time.Instant;

public record BaselineExaminationResponse(long id, long horseId, BigDecimal weightKg,
                                          HealthStatus healthStatus, Instant recordedAt) {}
