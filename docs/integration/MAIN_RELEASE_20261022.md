# Backend main release candidate — 22 October 2026

Phase 1 of the Leader-adopted five-phase main release directive. This candidate preserves PR25 ancestry and original authored commits. It does not dismiss its outstanding CHANGES_REQUESTED review.

## Sources and bounded changes

- Parent PR25: `0fcba422f199cc3896d2dc4b6f660d6d76e06078`, based on dev `5867f07ad521f2862eb80d5aa85c0bd615826855`.
- Retains existing Auth, Horse, Training and Medical feature ancestry; does not reapply superseded legacy PRs.
- Carries selected API128 source/tests from preserved local WIP at `4a76a4511b395405d83d2652a1ddc168ee240866`.
- Adds the 20 required registration/account/audit/notification routes and their persistence/security implementation. Adds required management summary/directory code from the existing WIP.
- Includes explicit Groom assignment, reduce-intensity, medical assignment-date scope fixes, after-commit notifications and checksum-pinned local installer work.
- Fixes two additional Training defects: category A exercises must be rejected before replacement; suitability reads require the assigned Trainer. Adds regression tests for Phase 4.
- Corrects PUT session exercises to the approved `{exercises:[...]}` DTO. The prior raw-array input was outside the approved contract; no current FE source caller was found. Tests now use the approved envelope.
- Secret password/reset DTO properties are write-only. Concurrent ordinary password changes report a current-password error rather than a reset-token error.
- Dev simulator now defaults OFF and requires explicit `REALTIME_SIMULATOR_ENABLED=true`; no normal API-mode synthetic telemetry.

## Review holds

The Leader decided to retain the existing SCHEDULED precheck contract for this local release and request the reviewer to waive the freshness finding. No TTL or PAUSED recheck rule is invented. The reviewer must confirm the waiver/re-review; the author cannot dismiss CHANGES_REQUESTED.

Owner summary selection, local photo filesystem storage, recommendation ordering and audit catalogue Q15 remain explicitly limited by their approved contracts. Password reset has a private dev-only outbox, not a production email provider. No cloud/production-readiness claim is made.

The release PR must remain Draft while inherited review findings and Phase 1 quick gates are unresolved. Opening a main-target PR does not bypass PR25 review. Merge commits only; no force push, squash or authored-history rewrite.

## Evidence separation

`API-GAP-MATRIX-20261021.json` retains all 128 operations and records one controller mapping per operation. Mapping is not business verification. Prior WIP has positive SQL-backed HTTP paths for 77/128 operations and all 128 authentication checks. Prior BE suite: 292 total, 277 executed passed, 15 skipped. Those results do not verify this new release SHA.

Per Leader instruction, no unit/full/E2E suites execute in Phases 1–2. Quick Java 21 `mvn -DskipTests package`, source/route collision, pinned checksum, diff/secret and ancestry checks are required. Full BE/FE/SQL/browser verification begins in Phase 4. No operation is marked verified COMPLETE yet.

## Database and restart safety

No database or SQL service was changed during Phase 1. Canonical schema and historical migration bytes retain approved hashes. New migration is additive, has no backfill and is never run at Spring startup. Installer refuses existing targets and checks the actual ordered plan; external receipts do not authorize upgrades of existing databases.

The prior isolated DB was resumed after SQL Server compiled a CREATE INDEX before its ALTER ADD. Installer now pre-adds that same approved nullable column in a separate batch inside the transaction. A complete fresh install after this fix has NOT been verified; Phase 4 must use a separately authorized new DB. No reset/drop is a recovery step.

The original demo WIP, credentials, runtime scripts and databases remain outside this release commit. Build output is isolated so packaging cannot overwrite its running JAR.
