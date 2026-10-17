# Prompt cho Nguyễn Đình Minh Đức — UI-first: CORE-017 + bàn giao HorsePages (2026-10-17)

**Tin nhắn Zalo (gửi kèm file này):**

> Nhóm chuyển sang UI-first / API-later. Màn danh sách và hồ sơ ngựa (CORE-017 của em) đã có sẵn một bản implementation trong nhánh tích hợp của Long (`src/features/horses/HorsePages.tsx`), viết lúc port CORE-013. Phần đó thuộc CORE-017, nên anh bàn giao cho em tận dụng: không viết lại từ đầu, chỉ hoàn thiện phần còn thiếu (timeline/lịch sử, HT readiness review) theo Figma. Prompt bên dưới. Thêm vài góp ý UX nhỏ cho CORE-036/046 ở cuối, em tự quyết có làm hay không. Không merge, không sửa code CORE-013 của Long; xong gửi Long link nhánh và kết quả test.

---

```text
PROJECT: SWP391 Racehorse Training & Management System — FE
REPO: lamphilong2004/SWP391_Racehorse-Training-Management-System_FE
TASK: CORE-017 — Horse List / Profile / Pedigree History + Head Trainer readiness review
OWNER: Nguyễn Đình Minh Đức     STRATEGY: UI-first / API-later (Leader decision 2026-10-17)
BRANCH: feat/CORE-017-horse-ht-fe, created from origin/feat/dev-ui-harness (stacked; do not PR into dev yet,
do not merge, never force-push)
DEPENDENCIES: CORE-002 (merged). CORE-004 Flow 1 contract is NOT merged yet (BE PR #9, changes requested).
→ Build against the PR #9 contract (docs/api/flow1-horse.openapi.yaml on branch feat/CORE-004-contract-flow1-owner)
and mark anything that may still change as NEEDS CLARIFICATION.

HANDOVER — reuse, do not duplicate
- src/features/horses/HorsePages.tsx (HorseListPage, HorseProfilePage) was written during the CORE-013 port and
  belongs to CORE-017. Take it over: keep the working list/filters/paging and the profile, and extend it.
- Shared pieces you can reuse: src/features/horses/api/* (horsesApi.ts, types.ts, HorsesApiContext.tsx),
  shared/format.ts, shared/options.ts, shared/horses.module.css, HorseManagementLayout.
- Do NOT change CORE-013 screens (create/RegisterHorsePage.tsx, create/PedigreeOwnershipPage.tsx,
  create/AssignBoxModal.tsx, activation/PendingActivationPage.tsx) except through a change agreed with Long.
- Sample adapter: src/shared/data/horseSamples.ts already serves /api/horses, /api/horses/{id}, ownerships,
  stable-assignments, photos, activation-status, pending-activation. Add timeline and readiness-review handlers
  there (contract shapes only).

SCOPE (tracker CORE-017)
1. Horse list (CLUB_MANAGER, HEAD_TRAINER, TRAINER, VETERINARIAN, GROOM read; x-roles per permission-matrix.md).
2. Horse profile with pedigree/ownership/box history and lifecycle.
3. Horse history / timeline: GET /api/horses/{id}/timeline. Starts at activatedAt (DEC-009 §1, BR-05); events carry
   titles only, no clinical detail for non-Vet callers.
4. Head Trainer readiness review: POST /api/horses/{id}/readiness-review → 204. Non-blocking acknowledgement in the
   HT profile context, traced as audit HEAD_TRAINER_READINESS_REVIEWED. No assessment content is stored and it never
   approves activation.
5. Pending activation is a view without an approve button (already in CORE-013; link to it, do not re-implement).

FIGMA (live file tm7dpgAQbHReZ7tlQeQJlc): Horse List / Profile / Pedigree History + Head Trainer readiness review
(Page 04 horse frames; CORE-013's nodes 281:2158, 200:8678 are the list/profile context). Read the exact nodes
(design context + screenshot), use src/design-system + src/layout only, and make every control work with loading /
error / empty / success states.

ARCHITECTURE: UI → src/features/horses/api (service) → requestApi → sample adapter OR real API (same types).
No hard-coded business data in components; no automatic fallback to samples on API errors.

RULES: 7 roles; web only; no new rules, fields, endpoints or schema; HT never sees unrestricted clinical records
(DEC-002 Q-04); no tracker edits; no secrets.

RUN: npm ci; npm run dev:harness → http://localhost:5190, choose roles in the "DEV HARNESS — SAMPLE DATA" bar.
See docs/UI-COVERAGE-MATRIX.md.

VERIFY: node node_modules/eslint/bin/eslint.js . ; node node_modules/typescript/bin/tsc -b --noEmit ;
node node_modules/vitest/vitest.mjs run --maxWorkers=1 ; build with tsc -b + vite build. Add tests. Do not raise
timeouts or weaken assertions. Walk through it in the browser as CM, HT, Trainer, Vet and Groom.

REPORT TO LONG: branch + HEAD; Figma nodes covered; sample handlers added; test/lint/typecheck/build output;
browser steps; NEEDS CLARIFICATION list (especially anything that depends on the unmerged CORE-004 contract).
```

---

## Góp ý UX cho CORE-036 / CORE-046 (không bắt buộc, em quyết)

Anh thấy những điểm này khi chạy thử trên trình duyệt bằng sample adapter (FE PR #8 / PR #5 trong nhánh tích hợp):

1. Danh sách và chi tiết Incident đang hiện ID thô ("Horse 7", "Reporter 16") thay vì tên ngựa hoặc người báo cáo. Kiểm tra Figma 256:1015 / 257:1112, xem contract có trả về tên không. Nếu không có, ghi NEEDS CLARIFICATION, đừng tự thêm field.
2. Treatment plan đang chọn Groom bằng ô nhập số "Groom user ID". Figma 270:1588 có thể là ô chọn (picker). Kiểm tra lại với contract.
3. Body map (`InjuryMap`) chỉ để xem; bấm chọn vùng không tự điền form "Body region". Nếu Figma muốn hai phần liên kết thì nên nối lại.
4. PR #5 đang trỏ base vào `feat/CORE-046-incident-report-modal-shared`, không phải dev, và đang xung đột router với dev. Em cần đổi base và giải quyết xung đột khi sẵn sàng.
