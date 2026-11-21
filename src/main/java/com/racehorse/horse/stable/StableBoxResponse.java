package com.racehorse.horse.stable;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code yardCode}, {@code rowNo}, {@code columnNo} and {@code boxStatus} are additive (DEC-016). A box outside every
 * yard has null position fields; it is listed only while a horse still occupies it.
 */
@Schema(name = "StableBox")
public record StableBoxResponse(
        long id,
        String boxCode,
        String yard,
        BoxStatus status,
        CurrentHorse currentHorse,
        String yardCode,
        Integer rowNo,
        Integer columnNo,
        String boxStatus) {

    @Schema(name = "CurrentHorse")
    public record CurrentHorse(
            long id,
            String horseCode,
            String name) {
    }
}
