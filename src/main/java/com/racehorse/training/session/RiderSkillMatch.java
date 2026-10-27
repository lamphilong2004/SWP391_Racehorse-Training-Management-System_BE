package com.racehorse.training.session;

import com.racehorse.horse.HorseStrength;

import java.util.*;

/**
 * BR-D22 Work Rider matching against the horse's declared strengths (V20261027_01). Rider skills are RIDER_SKILL
 * catalogue rows; the names below are the reference catalogue seeded for the club. Matching is by name, case-insensitive,
 * and only produces labelled reasons; it never blocks a rider.
 */
final class RiderSkillMatch {
    static final Map<String, Set<String>> SKILL_STRENGTHS = new LinkedHashMap<>();
    static {
        SKILL_STRENGTHS.put("Tập tốc độ và breeze", Set.of("SPRINT", "SPEED", "FRONT_RUNNER"));
        SKILL_STRENGTHS.put("Giữ nhịp cự ly dài", Set.of("MILE", "MIDDLE_DISTANCE", "STAYING", "ENDURANCE", "STALKER", "CLOSER"));
        SKILL_STRENGTHS.put("Tập trên sân cỏ", Set.of("TURF"));
        SKILL_STRENGTHS.put("Tập trên sân đất và sân tổng hợp", Set.of("DIRT", "SYNTHETIC"));
        SKILL_STRENGTHS.put("Kiểm soát sức mạnh và thăng bằng", Set.of("STRENGTH", "BALANCE"));
        SKILL_STRENGTHS.put("Xử lý ngựa khó", Set.of());
    }
    static final Set<String> JUNIOR_LEVELS = Set.of("JUNIOR", "BEGINNER", "MỚI", "TẬP SỰ");
    static final Set<String> HIGH_INTENSITY = Set.of("MEDIUM_HIGH", "HIGH");
    static final int DAILY_SESSION_WARNING = 3;

    private RiderSkillMatch() {}

    /** Strength labels covered by the rider's skills, in catalogue order. */
    static List<String> matchedStrengths(Collection<String> riderSkills, Collection<String> horseStrengths) {
        Set<String> covered = new HashSet<>();
        for (String skill : riderSkills) {
            for (Map.Entry<String, Set<String>> entry : SKILL_STRENGTHS.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(skill.strip())) covered.addAll(entry.getValue());
            }
        }
        return Arrays.stream(HorseStrength.values()).map(Enum::name)
                .filter(code -> covered.contains(code) && horseStrengths.contains(code)).map(HorseStrength::labelOf).toList();
    }

    static boolean junior(String experienceLevel) {
        return experienceLevel != null && JUNIOR_LEVELS.contains(experienceLevel.strip().toUpperCase(Locale.ROOT));
    }
}
