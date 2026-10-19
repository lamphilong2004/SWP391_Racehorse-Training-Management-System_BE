package com.racehorse.horse.activation;

import java.time.Instant;
import java.util.List;

public record ActivationStatus(
        long horseId,
        String horseCode,
        String name,
        String lifecycle,
        Instant activatedAt,
        List<ActivationChecklistItem> checklist) {

    public ActivationStatus {
        checklist = List.copyOf(checklist);
    }
}
