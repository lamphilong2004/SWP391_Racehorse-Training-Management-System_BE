# Horse strengths and training goals — scope decision

## Leader decision 2026-10-27 — implemented (declare + recommend)

The Leader chose "declare + recommend" with a racing-industry catalogue. This supersedes the "not yet migrated" note below for the declared part only.

- Additive migration `V20261027_01__horse_strength_and_template_focus.sql`: `dbo.horse_strength` (horse, code, declaring user, time) and `dbo.template_version_strength_focus` (immutable with its template version). No backfill, no existing column changed.
- 14 codes in four groups: distance aptitude SPRINT (< 1,400 m), MILE (1,400–1,999 m), MIDDLE_DISTANCE (2,000–2,399 m), STAYING (≥ 2,400 m) following the Raceform stamina bands; surface TURF, DIRT, SYNTHETIC; run style FRONT_RUNNER, STALKER, CLOSER (Brisnet run-style FAQ, presser and stalker merged); physical SPEED, ENDURANCE, STRENGTH, BALANCE.
- Club Manager declares on POST/PUT `/api/horses` (`strengths`; null keeps, [] clears). Every change writes audit action `HORSE_STRENGTHS_DECLARED`. Unknown stays valid (empty list).
- Head Trainer sees the declaration in the program wizard. `GET /api/horses/{id}/template-recommendations` adds a Vietnamese reason when declared strengths intersect the latest version's `strengthFocus`, lists more matches first, and returns labels only, no score. The reason states that the declaration is not confirmed by the Head Trainer.
- Template editor and library show and edit `strengthFocus`. A new version saved without the field keeps the previous version's focus; duplicates copy it.
- NOT implemented: Head Trainer confirmation (DECLARED vs CONFIRMED), development goals and history of superseded declarations. The section below remains the design for that later step.


Leader confirmed 2026-10-27: Club Manager may declare reported strengths when registering a horse; Head Trainer confirms or updates them after observation. Unknown is valid. Pedigree/country of origin do not determine ability.

## Existing implementation and immediate delivery

CORE-013 identity currently contains code, name, breed, sex, birth date, country of origin and registration number. There is no structured capability field. CORE-021 template recommendation currently matches breed/sex text against suitableHorseProfile; it cannot honestly claim a SPEED/ENDURANCE match. CORE-022 already allows choosing a template and customizing a horse-specific copy. CORE-024/025 session adaptations are already available to the assigned Trainer.

The immediate reference catalog adds six optional templates via existing schema/DTO fields. It does not change horse assessments, recommendation labels, clinical gates, API contracts or the canonical schema. New profiles deliberately contain no breed/sex keywords that would make the existing matcher claim a capability match. Required skill ids remain empty rather than inventing trainer qualifications. Operators must review Trainer suitability separately.

## Proposed additive profile contract — not yet migrated

Keep reported strengths distinct from confirmed strengths and development goals. Candidate tags: SPEED, ENDURANCE, STRENGTH, BALANCE. No numeric score, genetic prediction or automatic suitability certification.

- Club Manager declares strengths plus an optional source note, or leaves the assessment unknown.
- Head Trainer sees the declaration, records confirmed strengths and development goals plus a short observation note.
- Confirmed assessments retain author/time and the declaration revision they confirm. Editing a declaration invalidates confirmation of that revision; it must not silently change a previously approved program.
- Recommendations may later explain explicit matches between confirmed goals/profile and template focus. Missing/unconfirmed assessments return no ability-based recommendation. Medical restrictions and ACTIVE/availability gates still override any recommendation.
- Trainer may adjust sessions inside the assigned program/phase range and approved intensity/medical constraints. Strategic changes remain with Head Trainer.
- This assessment is not a new mandatory condition for DRAFT→ACTIVE and does not alter DEC-009 activation.

Suggested normalized storage: an assessment header referencing horse, assessment kind (DECLARED/CONFIRMED), author/time and optional superseded declaration; typed tag rows for strengths/goals. Keep history and row scope. Do not place hidden JSON in notes or repurpose breed, skill, health status or pedigree columns.

Schema/API migration remains a separate reviewed change under the Leader's canonical-DB policy. No DDL or existing-horse backfill is authorized by this catalog import. This file is a concrete scope proposal, not a claim that the new registration fields already exist.

Before implementation: approve the additive schema/API contract and review with the existing Horse/Training owners. Then add FE fields and confirmation UI, BE role/row validation and conflict checks, migrations and focused SQL/API/browser evidence. No CORE status or Independent Gate is changed here.
