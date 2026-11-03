# CORE-037 dependency readiness (14 Oct 2026)

Evidence: `project-context/Racehorse_Core_Flow_Execution.xlsx`, sheet `Core Flow Execution` (read-only); local `origin/dev` and feature refs in the two repositories; read-only GitHub PR search on 14 Oct 2026. `git merge-base --is-ancestor` was used where a feature ref exists. A PR is review evidence, never an approved interface or a merge. Local remote-tracking refs are a snapshot; recheck remote `dev` before changing this status.

| Official dependency | Implemented on feature branch | PR opened | Merged into inspected `dev` | Readiness / decision |
|---|---|---|---|---|
| CORE-009 Auth integration | No matching ref in either worktree | No matching PR found | No | **Missing**. CORE-007/008 also absent; no real JWT-to-route integration. |
| CORE-011 Demo master data | No matching BE ref | No matching PR found | No | **Missing**. No approved 7-role accounts or test fixtures. |
| CORE-018 Flow 1 integration | No matching ref | No matching PR found | No | **Missing**. No approved active horse fixture or reload evidence. |
| CORE-019 MedicalRestriction BE | No matching BE ref | No matching PR found | No | **Missing**. Availability, exercise rule and lift operations cannot run. G5 effective-time/rule policy remains a decision gap. |
| CORE-020 Restriction FE | No matching FE ref | No matching PR found | No | **Missing**. No restriction UI or training availability consumer. |
| CORE-026 Incident/pre-check BE | `origin/feat/CORE-026-incident-presession-gate` | [BE PR #5](https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_BE/pull/5), open search result | No | **Branch implementation; blocked** by CORE-007 actor, Groom scope, CORE-024 session projection/start transaction, and BR-23/DEC-007 conflict. |
| CORE-035 Medical BE | `origin/feat/CORE-035-medical-case-treatment-recovery` | [BE PR #6](https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_BE/pull/6), open search result | No | **Branch implementation; blocked** by CORE-007/026/019, approved Groom assignment and task materialization, SQL Server proof. PR explicitly says do not merge. |
| CORE-036 Medical FE | `origin/feat/CORE-036-vet-medical-groom-ui` | [FE PR #2](https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_FE/pull/2), open search result | No | **Mock UI on branch; blocked** by CORE-008, approved CORE-006, real adapter and backend. PR explicitly says do not merge. |

Additional upstream gates: BE CORE-001 foundation, CORE-006 Flow 3 contract, CORE-014 baseline, and FE CORE-002 design system have feature branches or review PRs but are not in inspected `dev`. CORE-003 shared auth contract and permission matrix **are merged** into BE `dev` (commit `84729d8`). FE `origin/dev` is still initial commit `6041b0a`. The workbook marks all eight official dependencies `To Do`; this is planning state, not proof of current merge state.

Branch inventory is limited to fetched refs visible in these worktrees and the PR search. “No matching PR found” means no matching result was returned, not proof that another repository or private PR does not exist. Approved BR-23/DEC-007 source text, role-feature source matrix, physical schema/ERD, Task Playbook and detailed DoD were not present in the available `project-context` files. Only the `Core Flow Execution` workbook sheet was present.
