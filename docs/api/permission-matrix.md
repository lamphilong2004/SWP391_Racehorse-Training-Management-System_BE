# API permission matrix — 7 roles (CORE-003)

- **Owner:** Long (security). **Reviewers:** Tiến (BE), Hoàng (FE consumer).
- **Derived from:** `project-docs/requirements/role-feature-matrix.md` (binding, DEC-002 §Q-04, DEC-004), DEC-005, DEC-007, DEC-008 §6, DEC-009.
- **Enforced by:** Spring Security in the backend (CORE-007): method or URL rules per role, plus a data-scope check in the service layer. Frontend route guards (CORE-008) mirror this table for usability only.
- **Roles (exactly 7):** `CLUB_MANAGER` (CM), `HEAD_TRAINER` (HT), `TRAINER` (TR), `WORK_RIDER` (WR), `VETERINARIAN` (VET), `GROOM` (GR), `HORSE_OWNER` (OW).
- **There is no `SYSTEM` role.** Automatic behaviour (activation, threshold evaluation, notification fan-out, aggregation) runs inside the backend and never authenticates.

## Legend

| Mark | Meaning |
|---|---|
| ✔ | Allowed for the role (role-level) |
| A | Allowed, **assignment-scoped**: TR only for programs assigned to them (open `trainer_assignment`); WR only for sessions where `work_rider_user_id` = self; GR only for sessions/tasks of their work list |
| O | Allowed, **row-scoped to owned horses**: the query joins `horse_ownership.owner_user_id` = current user (open period). A non-owned horse → **403** |
| S | Allowed, **summary projection only** (BR-D06): no diagnosis detail, prescription or treatment protocol |
| R | Read only |
| — | Denied → **403** |
| P | Public (`permitAll`, DEC-008 §6 R10 list only) |

The scope rules A/O/S are checked server-side on **every** read and write path. A frontend never fetches a wider set and filters it.

## Shared (shared.openapi.yaml, CORE-003)

| Endpoint group | CM | HT | TR | WR | VET | GR | OW |
|---|---|---|---|---|---|---|---|
| `POST /api/auth/login`, `/register`, `/forgot-password`, `/reset-password` | P | P | P | P | P | P | P |
| `GET /api/health`, Swagger UI (dev profile only) | P | P | P | P | P | P | P |
| `GET /api/auth/me`, `POST /api/auth/logout` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `GET|PUT /api/account`, `PUT /api/account/password` (own record) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `/api/admin/users/**` (MF-17) | ✔ | — | — | — | — | — | — |
| `/api/admin/registrations/**` (DEC-008) | ✔ | — | — | — | — | — | — |
| `GET /api/admin/roles` (MF-18, read-only) | R | — | — | — | — | — | — |
| `GET /api/admin/skills` (read-only catalogue for the MF-17 user form) | R | — | — | — | — | — | — |
| `GET /api/admin/audit-log` (MF-20) | R | — | — | — | — | — | — |
| `GET /api/dashboard/club-manager` (SCR-DASH-001) | R | — | — | — | — | — | — |
| `/api/notifications/**` (own notifications only) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| STOMP `/ws` + `/user/queue/notifications` (own queue) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |

## Flow 1 — Horse (flow1-horse.openapi.yaml, CORE-004)

| Endpoint group | CM | HT | TR | WR | VET | GR | OW |
|---|---|---|---|---|---|---|---|
| `GET /api/horses`, `GET /api/horses/{id}`, timeline, photos (read) | R | R | A (R) | — | R | R | — |
| `POST /api/horses`, `PUT /api/horses/{id}`, pedigree, ownerships, photos (write) | ✔ | — | — | — | — | — | — |
| `GET /api/stable-boxes`, stable assignments | ✔ | R | — | — | — | — | — |
| `GET /api/horses/pending-activation`, `/activation-status` | R | R | — | — | R | — | — |
| `POST /api/horses/{id}/readiness-review` (acknowledgement, 204) | — | ✔ | — | — | — | — | — |
| `/api/owner/horses/**` | — | — | — | — | — | — | O (R) |

No role has an activate/approve endpoint: activation is automatic (DEC-009 §1).

## Flow 2 — Training (flow2-training.openapi.yaml + flow2-stomp.md, CORE-005)

| Endpoint group | CM | HT | TR | WR | VET | GR | OW |
|---|---|---|---|---|---|---|---|
| Program templates: list, detail | R | ✔ | A (R, source template of assigned programs) | — | — | — | — |
| Program templates: create, new version, duplicate, archive | — | ✔ | — | — | — | — | — |
| Template and trainer recommendations | — | ✔ | — | — | — | — | — |
| Horse training programs: list, detail, progress, history | R | ✔ | A (R) | — | R | R | — |
| Programs: create, status, trainer assignment | — | ✔ | — | — | — | — | — |
| Adjustment requests: create | — | — | A | — | — | — | — |
| Adjustment requests: list | — | ✔ | A (own) | — | — | — | — |
| Adjustment requests: approve / reject | — | ✔ | — | — | — | — | — |
| Exercises: list, detail | R | R | R | R | R | — | — |
| Exercises: create, edit | — | ✔ | ✔ | — | — | — | — |
| Sessions: list, detail, calendar | R | R | A (assigned programs only) | A (own sessions) | R | A (assigned/notified pre-check sessions only) | — |
| Sessions: create, exercises, suitability check, rider recommendations | — | — | A | — | — | — | — |
| Sessions: start | — | — | A | A | — | — | — |
| Sessions: pause, resume, stop | — | — | A (Trainer only) | — | — | — | — |
| Sessions: reduce intensity | — | — | A (Trainer only; operation signature proposed, needs confirmation) | — | — | — | — |
| Sessions: complete | — | — | — | A | — | — | — |
| Sessions: report issue (MF-12 in-session) | — | — | A | A | — | — | — |
| Groom pre-session check list + submit | — | — | — | — | — | A | — |
| Work Rider assignments, history, rider feedback | — | — | — | A | — | — | — |
| Metrics submit (manual; IN_PROGRESS or PAUSED; `speedKmh` in km/h, `distanceM` in metres; distance semantics OPEN) | — | — | — | A | — | — | — |
| Simulator start / stop (demo, same pipeline) | — | — | A | — | — | — | — |
| Monitoring alerts: acknowledge + action | — | — | A | — | ✔ (CRITICAL acknowledge) | — | — |
| Evaluation submit | — | — | A | — | — | — | — |
| Session result, horse performance (read) | R | R | A | A (own sessions) | R | — | — |
| STOMP `/topic/sessions/{id}/metrics|alerts|state` | — | ✔ | A | A | ✔ | — | — |

Approved decisions: Q-32 Resume is Trainer-only; Q-33 Work Riders use Report Issue/Incident rather than Trainer Pause/Resume/Stop controls; Q-34 early start is not allowed and the Trainer must reschedule; Q-35 Trainers see sessions belonging to assigned programs, and Head Trainers may filter by `trainerId`. Groom REST access is limited to the assigned/notified Groom; persistence for that assignment is a model gap and does not permit broader access.

## Flow 3 — Medical & interlock (flow3-medical.openapi.yaml, CORE-006)

| Endpoint group | CM | HT | TR | WR | VET | GR | OW |
|---|---|---|---|---|---|---|---|
| Baseline examination (create) | — | — | — | — | ✔ | — | — |
| Weights: read | R | R | A (R) | — | ✔ | R | — |
| Weights: record | — | — | — | — | ✔ | — | — |
| Incidents: create | — | ✔ | A | A (in session) | — | ✔ | — |
| Incidents: list, detail | R | R | A (own reports) | A (own reports) | ✔ | own reports | — |
| Incidents: review (OPEN_CASE / LINK_CASE / RESOLVE) | — | — | — | — | ✔ | — | — |
| Medical cases: list, detail | R (summary) | S | S (assigned horses) | — | ✔ | — | — |
| Medical cases: create, close, examinations, injuries, treatment plan | — | — | — | — | ✔ | — | — |
| Treatment care tasks: Groom work list + complete | — | — | — | — | — | A | — |
| Treatment care tasks of a case (read, follow-up) | — | — | — | — | ✔ | — | — |
| Re-examinations | — | — | — | — | ✔ | — | — |
| Herd health overview | — | — | — | — | ✔ | — | — |
| Training availability (read) | R | R | A (R) | A (R) | R | R | — |
| Restrictions: club list | R | R | A (R) | — | ✔ | — | — |
| Restrictions: create, lift, exercise rules | — | — | — | — | ✔ (sole writer, BR-11) | — | — |
| Thresholds: global defaults (read) | — | R | R | — | R | — | — |
| Per-horse threshold override (read / set / end) | — | R | A (R) | — | ✔ (sole writer, D-04) | — | — |

Owner health and training data are reached only through `/api/owner/horses/**` (non-clinical summaries).

## Status

The matrix encodes the confirmed role-feature matrix, including the approved CORE-005 decisions listed above. Remaining CORE-005 open questions are not authorization rules. A change to any cell requires an update to `requirements/role-feature-matrix.md` first.
