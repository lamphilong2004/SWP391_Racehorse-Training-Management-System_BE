package com.racehorse.horse.stable;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StableBox")
public record StableBoxResponse(
        long id,
        String boxCode,
        String yard,
        BoxStatus status,
        CurrentHorse currentHorse) {

    @Schema(name = "CurrentHorse")
    public record CurrentHorse(
            long id,
            String horseCode,
            String name) {
    }
}
