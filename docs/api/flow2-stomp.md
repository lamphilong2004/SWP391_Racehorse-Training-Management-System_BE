# Flow 2 realtime — STOMP contract (CORE-005)

- **Owner:** Tiến. **Reviewers:** Hoàng, Dũng; Long (interlock).
- **Pipeline (DEC-002 §D-03, Q-06):** manual input (Work Rider) or the demo simulator → Spring Boot → threshold evaluation → STOMP → React realtime dashboard. Real IoT is out of scope. The simulator uses the same pipeline and marks its data `simulated: true`.
- **Transport:** WebSocket endpoint `/ws` (see shared.openapi.yaml). The client sends `Authorization: Bearer <jwt>` in the STOMP `CONNECT` headers. A missing or invalid token is rejected at CONNECT.
- **Clients only subscribe.** All writes go through REST (`POST /api/training-sessions/{id}/metrics`, lifecycle endpoints, alert acknowledge). Client `SEND` frames are rejected.
- **Timestamps** are ISO-8601 UTC. Units follow the REST contract (heart rate bpm, speed km/h, distance m — units are an ASSUMPTION, physical-schema §11 #4).

## Subscription authorization

Checked on every `SUBSCRIBE` (a `ChannelInterceptor` in CORE-007 / CORE-030), not only at CONNECT:

| Topic | Allowed subscribers |
|---|---|
| `/topic/sessions/{id}/metrics` | HEAD_TRAINER, VETERINARIAN (all sessions); TRAINER (sessions of assigned programs); WORK_RIDER (own session) |
| `/topic/sessions/{id}/alerts` | same as metrics |
| `/topic/sessions/{id}/state` | same as metrics |
| `/user/queue/notifications` | the signed-in user only (shared contract) |

CLUB_MANAGER, GROOM and HORSE_OWNER have no session topic access (role-feature-matrix MF-07). A refused subscription receives a STOMP `ERROR` frame. The connection is not trusted for other topics.

## `/topic/sessions/{id}/metrics`

Published for every stored sample, MANUAL or SIMULATOR.

```json
{
  "sessionId": 42,
  "sampleId": 9001,
  "recordedAt": "2026-10-10T08:15:03.000Z",
  "heartRate": 172,
  "speedKmh": 38.5,
  "distanceM": 1250.0,
  "source": "MANUAL",
  "simulated": false,
  "realtimeState": "WARNING",
  "thresholds": { "metric": "HEART_RATE", "warning": 170, "critical": 190, "overridden": false }
}
```

| Field | Notes |
|---|---|
| `heartRate`, `speedKmh`, `distanceM` | nullable; at least one is present |
| `source` | `MANUAL` \| `SIMULATOR` (ck_training_metric_sample__source) |
| `realtimeState` | `NORMAL` \| `WARNING` \| `CRITICAL`, evaluated on heart rate (the only MF-07 metric, ck_monitoring_alert__metric) |
| `thresholds` | the effective values: per-horse override (Veterinarian, critical only) or the global default (BR-D11). `warning` always comes from the global default configuration |

## `/topic/sessions/{id}/alerts`

| `type` | When | Stored? |
|---|---|---|
| `WARNING` | a sample crosses the warning value (AF-3) | No — the schema stores CRITICAL only. It is a transient, non-blocking banner |
| `CRITICAL_RAISED` | a sample crosses the critical value (EF-3) | Yes: `monitoring_alert` row; a notification `MONITORING_ALERT_CRITICAL` goes to the Trainer and the Veterinarian |
| `CRITICAL_ACKNOWLEDGED` | `POST /api/monitoring-alerts/{id}/acknowledge` | updates the row |
| `CRITICAL_ESCALATED` | still unacknowledged after the configured time | sets `escalated_to_vet_at`; notification `MONITORING_ALERT_ESCALATED` to the Veterinarian |

```json
{
  "type": "CRITICAL_RAISED",
  "sessionId": 42,
  "alertId": 77,
  "severity": "CRITICAL",
  "metric": "HEART_RATE",
  "metricValue": 196,
  "thresholdApplied": 190,
  "raisedAt": "2026-10-10T08:16:10.000Z",
  "currentExerciseOrder": 3,
  "suggestedActions": ["ACKNOWLEDGE", "REDUCE_INTENSITY", "PAUSE", "STOP"],
  "acknowledgedAt": null,
  "acknowledgedBy": null,
  "actionTaken": null,
  "simulated": false
}
```

A `WARNING` event has `alertId: null`, `severity: "WARNING"` and `suggestedActions: ["REDUCE_INTENSITY"]`. The Trainer decides; there is no automatic interruption (AF-3).
`currentExerciseOrder` is an ASSUMPTION: the backend derives it from the planned exercise order and elapsed time. It is nullable.

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

## Client rules

1. Subscribe after loading the session over REST. REST is the source of truth; STOMP is a live overlay.
2. On reconnect, reload the session (`GET /api/training-sessions/{id}`) and the samples (`GET /api/training-sessions/{id}/result`) before trusting new frames.
3. Show the **Simulated** badge whenever `simulated` is true.
4. Never compute thresholds or severity in the client. Display the backend values.
