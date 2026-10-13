package com.racehorse.medical;

import java.math.BigDecimal;
import java.time.Instant;

public record WeightRecordResponse(long id, long horseId, BigDecimal weightKg, Instant recordedAt) {
    static WeightRecordResponse from(WeightRecord record) {
        return new WeightRecordResponse(record.id, record.horseId, record.weight, record.measuredAt);
    }
}
