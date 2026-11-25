package com.racehorse.horse.stable;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** DEC-016 stable layout contract: yards are fixed grids (rows x columns); every cell is one box. */
public final class StableLayoutDtos {

    private StableLayoutDtos() {
    }

    /** Facility limits enforced by the API and by CHECK constraints (stable_yard row/column counts). */
    public static final int MAX_YARDS = 6;
    public static final int MAX_ROWS = 4;
    public static final int MAX_COLUMNS = 10;

    @Schema(name = "StableMap")
    public record StableMap(
            int totalCapacity,
            int activeBoxes,
            int occupied,
            int free,
            int maintenance,
            Limits limits,
            List<Yard> yards,
            List<OutsideBox> outsideLayout) {
    }

    @Schema(name = "StableLayoutLimits")
    public record Limits(int maxYards, int maxRows, int maxColumns) {
    }

    @Schema(name = "StableYard")
    public record Yard(
            long id,
            String yardCode,
            String name,
            int rowCount,
            int columnCount,
            int occupied,
            int free,
            int maintenance,
            List<Cell> boxes) {
    }

    @Schema(name = "StableMapBox")
    public record Cell(long id, String boxCode, int rowNo, int columnNo, String boxStatus, Occupant horse) {
    }

    @Schema(name = "StableMapHorse", description = "Horse in a box. healthStatus is the horse summary status "
            + "(FIT / MONITORING / INJURED / QUARANTINED, null when not assessed yet) shown on the stable map.")
    public record Occupant(long id, String horseCode, String name, String lifecycle, String healthStatus) {
    }

    @Schema(name = "StableBoxOutsideLayout",
            description = "Legacy box outside every yard that still holds a horse; move the horse to a box in a yard.")
    public record OutsideBox(long id, String boxCode, Occupant horse) {
    }

    @Schema(name = "CreateStableYardRequest")
    public record CreateYardRequest(String yardCode, String name, Integer rowCount, Integer columnCount) {
    }

    @Schema(name = "ReleaseStableBoxRequest")
    public record ReleaseBoxRequest(String reason) {
    }

    @Schema(name = "UpdateStableBoxStatusRequest")
    public record UpdateBoxStatusRequest(String status, String reason) {
    }
}
