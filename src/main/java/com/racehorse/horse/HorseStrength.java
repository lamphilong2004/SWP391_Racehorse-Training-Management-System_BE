package com.racehorse.horse;

import java.util.*;

/**
 * Racing strengths a Club Manager may declare (V20261027_01). Codes are API values; labels are used only in
 * recommendation reasons. A declaration is not a Head Trainer confirmation and never certifies suitability.
 */
public enum HorseStrength {
    SPRINT("Nước rút"), MILE("Cự ly dặm"), MIDDLE_DISTANCE("Cự ly trung bình"), STAYING("Đường dài"),
    TURF("Sân cỏ"), DIRT("Sân đất"), SYNTHETIC("Sân tổng hợp"),
    FRONT_RUNNER("Dẫn đầu"), STALKER("Bám đuổi"), CLOSER("Bứt phá cuối"),
    SPEED("Tốc độ"), ENDURANCE("Sức bền"), STRENGTH("Sức mạnh"), BALANCE("Thăng bằng");

    private final String label;
    HorseStrength(String label) { this.label = label; }
    public String label() { return label; }

    /** Distinct, known codes in catalogue order; null when {@code codes} is null. Unknown codes are returned in {@code invalid}. */
    public static List<String> normalize(Collection<String> codes, List<String> invalid) {
        if (codes == null) return null;
        Set<String> wanted = new HashSet<>();
        for (String code : codes) {
            String value = code == null ? "" : code.strip();
            try { wanted.add(valueOf(value).name()); } catch (IllegalArgumentException unknown) { invalid.add(value); }
        }
        return Arrays.stream(values()).map(Enum::name).filter(wanted::contains).toList();
    }

    public static String labelOf(String code) {
        try { return valueOf(code).label; } catch (IllegalArgumentException unknown) { return code; }
    }
}
