# Leader-authorized local demo integration — 21 October 2026

Authority: current Leader request and accompanying bounded CORE-024/026 execution requirements. DEC-009 remains binding for the minimum read-only Owner workspace. The canonical DB-01 schema is retained.

CORE-024 uses POST `/api/training-sessions/{id}/reduce-intensity`, TRAINER only, current program assignment, strict decrease LOW < MEDIUM < MEDIUM_HIGH < HIGH, IN_PROGRESS/PAUSED only. Serializable transaction and session lock guard the change. Current program, precheck, phase bounds and medical gates remain enforced. The persisted session is returned and SESSION_INTENSITY_REDUCED is audited. Alert action REDUCE_INTENSITY now calls the same service with an explicit intensity; it cannot acknowledge an action without changing the session.

CORE-026 uses a technical `training_session_groom_assignment` table, one row per session with Groom, assigner and time. Only the currently assigned Trainer may assign an ACTIVE Groom before the first precheck while SCHEDULED. Reassignment and precheck share the session lock. Assignment emits a notification addressed to that Groom, category SCHEDULE, type GROOM_PRECHECK_ASSIGNED, target TRAINING_SESSION. Notifications are signals; the explicit assignment is the authorization source. Old notifications grant no permission after reassignment.

GET `/api/groom/sessions?date=YYYY-MM-DD` scopes to the authenticated Groom and local Vietnam calendar day, returning SCHEDULED sessions including their precheck status. Groom detail and precheck enforce the same assignment. POST `/api/training-sessions/{id}/groom-assignment` accepts `{groomUserId}`; GET `/api/training-sessions/{id}/groom-candidates` returns active Groom references for the assigned Trainer. These two supporting operations extend the contract without altering the original 100-flow denominator.

FE adds assignment inside the existing Session Detail Staff & horse card, reusing SelectField/Button. Live Monitoring adds a lower-intensity control inside its existing Trainer actions card. This is the bounded compatible control authorized by the Leader; exact Figma visual review remains a separate gate.

Migration V20261021_01 is additive, with no backfill or overwrite. It is never automatically applied by Spring. Dry-run and first apply are restricted to racehorse_integration_test by the operator script. racehorse_demo remains untouched until a verified backup and explicit migration/provisioning step.

STOMP notifications from CORE-030 now publish only after transaction commit so rolled-back writes cannot appear successful to connected clients.
