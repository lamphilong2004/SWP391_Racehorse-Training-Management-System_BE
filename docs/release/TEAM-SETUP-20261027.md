# Team setup — three-flow review release (27 October 2026)

BE `main` contains everything below. The FE changes are in PR #29 (`fix/landing-session-guidance`) until one teammate approves and merges it (the FE `main` ruleset requires a review).

## 1. Database

The release adds migration `V20261027_01` (tables `horse_strength`, `template_version_strength_focus`) and reference data.

**New local database** (recommended): the installer applies `schema.sql` plus all four migrations and verifies `expected-schema-v20261027.json`.

```powershell
$env:RH_DATABASE='racehorse_local_<yourname>'   # plus the usual RH_SQL_* variables
pwsh scripts/db/Install-LocalDatabase.ps1
```

**Existing local database**: back it up, then apply only the new migration.

```powershell
sqlcmd -S localhost -E -N -C -d <your_db> -b -f 65001 -i database/migrations/V20261027_01__horse_strength_and_template_focus.sql
```

**Reference data** (both cases; safe to run again — inserts only what is missing):

```powershell
sqlcmd -S localhost -E -N -C -d <your_db> -b -f 65001 -i database/seeds/reference-catalog-20261027.sql
```

It adds 11 reference training templates with strength focus (sources in `database/reference/training-catalog-20261027-v2.json`) and 6 Work Rider skills. Verified on a fresh install: 11 templates, 18 focus rows, 6 rider skills.

Then, as Club Manager → *Nhân sự & người dùng*, give each Work Rider the skills they really have, so rider recommendations have something to match.

## 2. What changed in behaviour (test these)

| Area | Rule |
| --- | --- |
| Horse registration | Club Manager may declare racing strengths (optional, audited). Stable boxes show as "Chuồng số N". |
| Initial exam | INJURED or QUARANTINED horses cannot get a program, a session or a start until the Veterinarian records a new health status. |
| Program | Starts today or later (Vietnam time). One ACTIVE/PAUSED program per horse per period. Template suggestions follow declared strengths, with reasons and no score. |
| Session | Must start in the future and stay inside phase and program. A horse cannot have two overlapping sessions. Work Rider suggestions consider skills vs strengths, past sessions with the horse, junior rider + high intensity, 3+ sessions that day; schedule conflict is the only hard block; warnings must be acknowledged. |
| Pre-session check | Groom can check from 12 h before the start until the planned end. ABNORMAL blocks start and notifies the Veterinarian and Trainer. |
| Start | Only from the scheduled time until the planned end; a check older than 12 h before the start is refused. |
| Incidents | Every report notifies the Veterinarian and current Trainer. Groom can report "bỏ ăn"/abnormal at the stable outside any session (Groom Today → *Báo bất thường tại chuồng*). |

Details: `docs/TRAINING-CALENDAR-RULES.md`, `docs/HORSE-STRENGTHS-PROPOSAL.md`.

## 3. Review tip

Sessions cannot be backdated or started early. To show a full session live, schedule it 10–15 minutes ahead, let the Groom do the pre-session check, and present existing data while waiting.
