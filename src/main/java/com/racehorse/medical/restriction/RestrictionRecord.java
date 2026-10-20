package com.racehorse.medical.restriction;

import java.time.Instant;
import java.time.LocalDate;

public record RestrictionRecord(
        long id,
        long horseId,
        Long medicalCaseId,
        long setByUserId,
        RestrictionLevel level,
        String reason,
        Instant effectiveAt,
        Instant liftedAt,
        LocalDate reviewDate) {
}