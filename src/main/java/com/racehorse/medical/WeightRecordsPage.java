package com.racehorse.medical;

import java.util.List;

public record WeightRecordsPage(List<WeightRecordResponse> items, int page, int size, long totalElements) {
    public WeightRecordsPage { items = List.copyOf(items); }
}
