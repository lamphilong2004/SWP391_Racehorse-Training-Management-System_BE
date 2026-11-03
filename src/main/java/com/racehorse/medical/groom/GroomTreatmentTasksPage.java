package com.racehorse.medical.groom;

import java.util.List;

public record GroomTreatmentTasksPage(
        List<GroomTreatmentTask> items,
        int page,
        int size,
        long totalElements) {
    public GroomTreatmentTasksPage {
        items = List.copyOf(items);
    }
}
