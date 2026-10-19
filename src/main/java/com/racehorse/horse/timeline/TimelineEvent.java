package com.racehorse.horse.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "TimelineEvent")
public record TimelineEvent(
        Instant occurredAt,
        TimelineEventType eventType,
        String title,
        String targetType,
        Long targetId) {

    public TimelineEvent withoutTarget() {
        return new TimelineEvent(occurredAt, eventType, title, null, null);
    }
}
