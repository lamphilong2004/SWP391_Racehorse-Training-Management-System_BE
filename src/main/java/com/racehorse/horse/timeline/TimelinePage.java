package com.racehorse.horse.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "TimelinePage")
public record TimelinePage(
        List<TimelineEvent> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public TimelinePage {
        items = List.copyOf(items);
    }
}
