package com.racehorse.medical.incident;

import java.util.Set;

/**
 * The one list of incident types, kept equal to {@code ck_incident__incident_type} (V20261109_01).
 * The first five come from 1_Scope!C15 (stable-side observations); the in-ride types are added by DEC-013 so a
 * Work Rider or Trainer can name what happened in the saddle.
 */
public final class IncidentTypes {
    public static final Set<String> ALL = Set.of(
            "OFF_FEED", "COLIC_SIGNS", "FEVER", "HOOF_OR_LEG_PROBLEM", "OTHER",
            "LAMENESS", "INJURY", "BREATHING_PROBLEM", "BEHAVIOUR_PROBLEM", "EXHAUSTION");

    private IncidentTypes() {}
}
