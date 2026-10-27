# Training calendar correction

Leader request, 2026-10-27: keep the three main flows as the immediate review scope. Daily stable cleaning is approved as a separate future delivery; it is not a new session-start prerequisite.

## Implemented

- New horse programs: periodStart is today or later, evaluated in Asia/Ho_Chi_Minh. A program may start today. No arbitrary six-month maximum is imposed.
- New or rescheduled sessions: scheduledStart must be strictly later than server now. A later time today is valid. Start and end calendar dates must remain within both the program and selected phase.
- Suitability and rider recommendation drafts use the same future-time and end-boundary validation.
- Assignment scope is checked before returning clinical restriction reasons during session creation.
- Groom planning uses the Trainer assignment covering the session's scheduled day, just like session creation. A Trainer assigned from tomorrow can prepare that session's Groom today; this grants no unrelated Trainer access.
- Historical programs, sessions, results and evaluations are not rewritten or hidden. Existing overdue SCHEDULED sessions must be rescheduled before editing their schedule/details.
- There is no schema, migration, seed or demo-data change.

## Existing responsibilities

Club Manager declares identity, pedigree and ownership and assigns the stable box. Veterinarian records the initial baseline. Activation remains automatic after all mandatory requirements; the Head Trainer readiness acknowledgement is not a manual activation switch.

Head Trainer defines the program and phases. The assigned Trainer schedules sessions, selects an eligible Work Rider and explicitly assigns a Groom. Groom Today lists those assigned sessions for the selected Vietnam calendar day. A training program alone does not assign all its horses to every Groom.

Work Rider start still requires the scheduled time, the assigned role/horse scope, ALL_NORMAL precheck, an active program and passing medical gates. Treatment completion does not lift restrictions or convert INTERRUPTED to COMPLETED.

## Remaining policy/implementation findings

These findings are not silently converted into new business rules:

1. The existing contract defines no late-start expiry or automatic expiry/cancellation for overdue SCHEDULED sessions. Old demo sessions retain their real dates. A separate decision is needed for a late-start window; do not alter old records just to make a demo start.
2. Precheck submission currently requires SCHEDULED plus assigned Groom, but not the same calendar day. The previous local release explicitly waived an unspecified freshness rule on resume. A validity/recheck rule requires an approved contract change.
3. Session overlap checks currently block Work Rider conflicts. The contract explicitly calls these Work-Rider-only category B conflicts. Horse-wide overlap/required recovery gaps need a decision and tests before adding another hard gate.
4. HorseRepository's current stable projection selects effective_to IS NULL. It does not select today's inclusive interval. Future moves can therefore appear current early; this is a separate projection correction to review with stable-assignment/activation consumers.
5. Automated reminders, completion of program phases by time alone, stable cleaning and nutrition are not supplied by the three main flows. No new scheduler or business status has been invented.

## Verification

TrainingCalendarTest covers Vietnam midnight, yesterday/today/future and an unrestricted future horizon. Program/session API tests use a deterministic scheduling clock and retain real role/SQL-fixture checks. They verify invalid requests do not persist changes and unauthorized Trainers cannot obtain clinical reasons.

These API tests use H2 fixtures. They are not proof of a full seven-role SQL Server/browser journey. See the workspace checkpoint for exact runs and local runtime evidence.
