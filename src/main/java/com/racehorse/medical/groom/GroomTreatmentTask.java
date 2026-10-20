package com.racehorse.medical.groom;

import java.time.Instant;
import java.time.LocalDate;

/** Operational task projection. It deliberately contains no medical case or treatment-plan fields. */
public record GroomTreatmentTask(
        long id,
        long horseId,
        long assignedGroomUserId,
        String instruction,
        LocalDate taskDate,
        String scheduledSlot,
        String status,
        Instant completedAt) {
}
