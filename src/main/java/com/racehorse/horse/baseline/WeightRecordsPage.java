package com.racehorse.horse.baseline;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "WeightRecordsPage")
public record WeightRecordsPage(
        List<WeightRecord> items,
        int page,
        int size,
        long totalElements) {

    public WeightRecordsPage {
        items = List.copyOf(items);
    }
}
