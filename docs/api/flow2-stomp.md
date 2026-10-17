# Flow 2 realtime — STOMP contract (CORE-005)

- **Owner:** Tiến. **Reviewers:** Hoàng, Dũng; Long (interlock).
- **Pipeline (DEC-002 §D-03, Q-06):** manual input (Work Rider) or the demo simulator → Spring Boot → threshold evaluation → STOMP → React realtime dashboard. Real IoT is out of scope. The simulator uses the same pipeline and marks its data `simulated: true`.
- **Transport:** WebSocket endpoint `/ws` (see shared.openapi.yaml). The client sends `Authorization: Bearer <jwt>` in the STOMP `CONNECT` headers. A missing or invalid token is rejected at CONNECT.
- **Clients only subscribe.** All writes go through REST (`POST /api/training-sessions/{id}/metrics`, lifecycle endpoints, alert acknowledge). Client `SEND` frames are rejected.
- **Timestamps** are ISO-8601 UTC. Heart rate is bpm. The field names encode speed in km/h (`speedKmh`) and distance in metres (`distanceM`); distance semantics remain OPEN pending approval.

## Subscription authorization

Checked on every `SUBSCRIBE` (a `ChannelInterceptor` in CORE-007 / CORE-030), not only at CONNECT:

| Topic | Allowed subscribers |
|---|---|
| `/topic/sessions/{id}/metrics` | CLUB_MANAGER (read-only), HEAD_TRAINER, VETERINARIAN (all sessions); TRAINER (sessions of assigned programs); WORK_RIDER (own session) |
| `/topic/sessions/{id}/alerts` | CLUB_MANAGER (read-only), HEAD_TRAINER, VETERINARIAN (all sessions); TRAINER (sessions of assigned programs) |
| `/topic/sessions/{id}/state` | CLUB_MANAGER (read-only), HEAD_TRAINER, VETERINARIAN (all sessions); TRAINER (sessions of assigned programs); WORK_RIDER (own session) |
| `/user/queue/notifications` | the signed-in user only (shared contract) |

The confirmed role-feature-matrix MF-07 grants CLUB_MANAGER read-only access to all three session topics. WORK_RIDER may subscribe only to `metrics` and `state` for their own session; alerts go to the Trainer and Veterinarian (BR-25), and the WR-D3/WR-D9 Figma frames have no alert panel. GROOM and HORSE_OWNER have no session topic access. Groom REST access to pre-session checks is limited to the assigned/notified Groom; the current contract branch has a persistence model gap for that assignment and must not widen access. A refused subscription receives a STOMP `ERROR` frame. The connection is not trusted for other topics.

## `/topic/sessions/{id}/metrics`

Published for every stored sample, MANUAL or SIMULATOR.

This structural excerpt includes an illustrative heart-rate sample; it does not imply a threshold. The field names encode
speed in km/h and distance in metres; distance semantics remain OPEN, and runtime-derived classification fields are omitted.

```json
{
  "sessionId": 42,
  "sampleId": 9001,
  "recordedAt": "2026-10-10T08:15:03.000Z",
  "heartRate": 120,
  "speedKmh": null,
  "distanceM": null,
  "source": "MANUAL",
  "simulated": false
}
```

| Field | Notes |
|---|---|
| `heartRate` | nullable; bpm |
| `speedKmh` | nullable; field name denotes km/h |
| `distanceM` | nullable; field name denotes metres; distance semantics remain OPEN pending approval |
| `source` | `MANUAL` \| `SIMULATOR` (ck_training_metric_sample__source) |
| `realtimeState` | `NORMAL` \| `WARNING` \| `CRITICAL`, evaluated on heart rate (the only MF-07 metric, ck_monitoring_alert__metric) |
| `thresholds` | The event includes effective runtime values from the global medical threshold configuration combined with any applicable horse override. Exact merge semantics are not specified here. No universal values are implied; warning is not derived from critical. |

The sample omits runtime `realtimeState` and threshold values intentionally; publishers must include the effective runtime values, not constants.

## `/topic/sessions/{id}/alerts`

| `type` | When | Stored? |
|---|---|---|
| `WARNING` | a sample crosses the warning value (AF-3) | No — the schema stores CRITICAL only. It is a transient, non-blocking banner |
| `CRITICAL_RAISED` | a sample crosses from below to at/above the effective critical value (EF-3) | One `monitoring_alert` row per crossing; no repeat while samples remain at/above threshold. Re-arm only after a sample falls below threshold. A notification `MONITORING_ALERT_CRITICAL` goes to the Trainer and the Veterinarian |
| `CRITICAL_ACKNOWLEDGED` | `POST /api/monitoring-alerts/{id}/acknowledge` | updates the row |
| `CRITICAL_ESCALATED` | still unacknowledged after the configured time | sets `escalated_to_vet_at`; notification `MONITORING_ALERT_ESCALATED` to the Veterinarian |

```json
{
  "type": "CRITICAL_RAISED",
  "sessionId": 42,
  "alertId": 77,
  "severity": "CRITICAL",
  "metric": "HEART_RATE",
  "raisedAt": "2026-10-10T08:16:10.000Z",
  "suggestedActions": ["ACKNOWLEDGE", "REDUCE_INTENSITY", "PAUSE", "STOP"],
  "acknowledgedAt": null,
  "acknowledgedBy": null,
  "actionTaken": null,
  "simulated": false
}
```

A `CRITICAL_RAISED` event also carries `metricValue` and `thresholdApplied`, populated from the sample and effective runtime threshold; these dynamic fields are omitted from this structural excerpt. Each crossing emits one alert event. `suggestedActions` are Trainer actions; Work Riders do not use Trainer Pause/Resume/Stop controls and report issues through the separate issue/incident flow. A `WARNING` event has `alertId: null`, `severity: "WARNING"` and `suggestedActions: ["REDUCE_INTENSITY"]`. The Trainer decides; there is no automatic interruption (AF-3). The intensity-reduction REST operation at `POST /api/training-sessions/{id}/reduce-intensity` is **PROPOSED — NEEDS CONFIRMATION**; its method, path, body and applicable states have no approved source found.
`currentExerciseOrder` derivation remains OPEN pending approval; no value or algorithm is implied here.

## `/topic/sessions/{id}/state`

Published after every lifecycle change and every change to the derived blocked flag.

```json
{
  "sessionId": 42,
  "status": "PAUSED",
  "previousStatus": "IN_PROGRESS",
  "changedAt": "2026-10-10T08:17:00.000Z",
  "changedBy": { "id": 3, "fullName": "Nguyen Minh Khoa" },
  "interruptionReason": null,
  "blocked": false,
  "blockedReasons": []
}
```

- `status` is one of `SCHEDULED`, `IN_PROGRESS`, `PAUSED`, `COMPLETED`, `INTERRUPTED` (the schema CHECK).
- **BLOCKED is never a status.** EF-2 (availability becomes LOCKED after scheduling) and an ABNORMAL pre-session check publish `blocked: true` with `blockedReasons` (`AVAILABILITY_LOCKED`, `PRE_CHECK_MISSING`, `PRE_CHECK_ABNORMAL`, `EXERCISE_MEDICAL_BLOCK`, `PROGRAM_NOT_ACTIVE`). The status stays `SCHEDULED`.
- `INTERRUPTED` always carries `interruptionReason`. It is never followed by `COMPLETED`.
- A pre-session check contains exactly `GENERAL_CONDITION`, `APPETITE_BEHAVIOUR`, `LEGS_HOOVES`, `MOVEMENT`, and `TEMPERATURE`; each has a `NORMAL` or `ABNORMAL` result and optional observation. The backend computes `overallResult`. An ABNORMAL check leaves the session `SCHEDULED` and blocked; it does not create an Incident. Incident reporting is a separate CORE-046 `IncidentReportModal` → `POST /api/incidents` flow and may reference the check's `id` as `preSessionCheckId`.
- Metrics may be ingested while the session is `IN_PROGRESS` or `PAUSED`.
- Warning/critical threshold values and escalation timing are resolved from approved runtime configuration; escalation timing remains OPEN where not specified.

## Client rules

1. Subscribe after loading the session over REST. REST is the source of truth; STOMP is a live overlay.
2. On reconnect, reload the session (`GET /api/training-sessions/{id}`) and the samples (`GET /api/training-sessions/{id}/result`) before trusting new frames.
3. Show the **Simulated** badge whenever `simulated` is true.
4. Never compute thresholds or severity in the client. Display the backend values.
