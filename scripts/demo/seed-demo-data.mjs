#!/usr/bin/env node
// Demo data for the review, one independent set per member (docs/DEMO-DATA.md).
// Uses only the running backend API (no SQL business writes). Every run creates new horses named with a run tag,
// so it can be repeated for rehearsals. Passwords come from the environment and are never printed.
//
//   RH_DEMO_CM_PASSWORD=... RH_DEMO_PASSWORD=... node scripts/demo/seed-demo-data.mjs
//
// Env: RH_API (default http://127.0.0.1:8080), RH_DEMO_CM_EMAIL (default clubmanager@gmail.com),
//      RH_DEMO_CM_PASSWORD (required), RH_DEMO_PASSWORD (required, password of the demo accounts),
//      RH_DEMO_HOURS (default 4, how long the training sessions stay startable), RH_DEMO_SETS (default D,H,T,Y,L),
//      RH_DEMO_START (optional ISO start of the review window), RH_DEMO_PHOTO_DIR (optional folder of distinct horse photos).

const API = process.env.RH_API ?? 'http://127.0.0.1:8080';
const CM_EMAIL = process.env.RH_DEMO_CM_EMAIL ?? 'clubmanager@gmail.com';
const CM_PASSWORD = process.env.RH_DEMO_CM_PASSWORD;
const DEMO_PASSWORD = process.env.RH_DEMO_PASSWORD;
const HOURS = Number(process.env.RH_DEMO_HOURS ?? 4);
const SETS = new Set((process.env.RH_DEMO_SETS ?? 'D,H,T,Y,L').split(',').map((s) => s.trim().toUpperCase()));
// RH_DEMO_START (ISO with offset, e.g. 2026-11-11T09:30:00+07:00): schedule the review sessions and care slots for a
// later review window instead of "now". Pre-checks done by this script must still fall within 12 h before that start.
const START = process.env.RH_DEMO_START ? new Date(process.env.RH_DEMO_START) : null;
if (START && !Number.isFinite(START.getTime())) { console.error('RH_DEMO_START must be an ISO date-time.'); process.exit(2); }
// RH_DEMO_PHOTO_DIR: folder of JPEG/PNG/WebP files; every created horse gets the next unused photo (distinct images).
const PHOTO_DIR = process.env.RH_DEMO_PHOTO_DIR ?? null;
if (!CM_PASSWORD || !DEMO_PASSWORD) { console.error('Set RH_DEMO_CM_PASSWORD and RH_DEMO_PASSWORD.'); process.exit(2); }

const VN = 7 * 3600_000;
const vnNow = () => new Date(Date.now() + VN);
const today = () => vnNow().toISOString().slice(0, 10);
const plusDays = (n) => new Date(Date.parse(`${today()}T00:00:00Z`) + n * 86_400_000).toISOString().slice(0, 10);
const demoDay = () => (START ? new Date(START.getTime() + VN).toISOString().slice(0, 10) : today());
const hhmm = (d) => new Date(d.getTime() + VN).toISOString().slice(11, 16);
const TAG = vnNow().toISOString().slice(5, 16).replace(/[-T:]/g, '').replace(/^(\d{4})(\d{4})$/, '$1-$2'); // MMDD-HHMM
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const summary = [];
const note = (set, label, value) => { summary.push(`${set} | ${label} | ${value}`); console.log(`  ${set} ${label}: ${value}`); };

// ---------- HTTP ----------
const tokens = new Map();
async function call(token, method, path, body, ok = [200, 201, 202, 204]) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const json = res.status === 204 ? null : await res.json().catch(() => null);
  if (!ok.includes(res.status)) throw new Error(`${method} ${path} -> ${res.status} ${json?.error?.code ?? ''} ${json?.error?.message ?? ''}`.trim());
  return { status: res.status, data: json?.data };
}
async function tryLogin(email, password) {
  const res = await fetch(`${API}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password, rememberMe: false }) });
  if (res.status !== 200) return null;
  return (await res.json()).data.accessToken;
}
const as = (email) => { const t = tokens.get(email); if (!t) throw new Error(`No session for ${email}`); return t; };
const items = (page) => page?.items ?? page ?? [];

// ---------- accounts ----------
const ACCOUNTS = [
  { email: 'headtrainer@gmail.com', role: 'HEAD_TRAINER', name: 'headtrainer' },
  { email: 'trainer1@gmail.com', role: 'TRAINER', name: 'Minh Quân · Tốc độ', level: 'SENIOR', years: 6, skill: 'TRAINER' },
  { email: 'veterinarian@gmail.com', role: 'VETERINARIAN', name: 'veterinarian', level: 'SENIOR', years: 8 },
  { email: 'groom@gmail.com', role: 'GROOM', name: 'groom' },
  { email: 'workrider1@gmail.com', role: 'WORK_RIDER', name: 'Phạm Đức Anh', level: 'INTERMEDIATE', years: 4, skill: 'RIDER' },
  { email: 'workrider2@gmail.com', role: 'WORK_RIDER', name: 'Võ Thanh Tùng', level: 'SENIOR', years: 6, skill: 'RIDER' },
  { email: 'workrider3@gmail.com', role: 'WORK_RIDER', name: 'Đặng Khánh Linh', level: 'INTERMEDIATE', years: 3, skill: 'RIDER' },
  { email: 'horseowner1@gmail.com', role: 'HORSE_OWNER', name: 'Nguyễn Minh Khang' },
  { email: 'horseowner2@gmail.com', role: 'HORSE_OWNER', name: 'Trần Bảo Ngọc' },
];
const ids = {};

async function ensureAccounts() {
  const cm = await tryLogin(CM_EMAIL, CM_PASSWORD);
  if (!cm) throw new Error(`Club Manager ${CM_EMAIL} cannot sign in; check RH_DEMO_CM_EMAIL / RH_DEMO_CM_PASSWORD.`);
  tokens.set('cm', cm);
  const skills = items((await call(cm, 'GET', '/api/admin/skills')).data);
  const skillOf = (kind) => skills.filter((s) => (s.kind ?? '').includes(kind)).map((s) => s.id).slice(0, 1);
  for (const a of ACCOUNTS) {
    let token = await tryLogin(a.email, DEMO_PASSWORD);
    if (!token) {
      const found = items((await call(cm, 'GET', `/api/admin/users?q=${encodeURIComponent(a.email)}&size=5`)).data).find((u) => u.email?.toLowerCase() === a.email);
      const body = { fullName: a.name, email: a.email, roleCode: a.role, experienceYears: a.years ?? null, experienceLevel: a.level ?? null,
        skillIds: a.skill === 'TRAINER' ? skillOf('TRAINER') : a.skill === 'RIDER' ? skillOf('RIDER') : [], password: DEMO_PASSWORD, confirmPassword: DEMO_PASSWORD };
      if (found) {
        const current = (await call(cm, 'GET', `/api/admin/users/${found.id}`)).data;
        await call(cm, 'PUT', `/api/admin/users/${found.id}`, { ...body, fullName: current.fullName ?? a.name, experienceYears: current.experienceYears ?? body.experienceYears,
          experienceLevel: current.experienceLevel ?? body.experienceLevel, skillIds: (current.skills ?? []).map((s) => s.id) });
        console.log(`  account ${a.email}: password set to RH_DEMO_PASSWORD`);
      } else {
        await call(cm, 'POST', '/api/admin/users', body);
        console.log(`  account ${a.email}: created`);
      }
      token = await tryLogin(a.email, DEMO_PASSWORD);
      if (!token) throw new Error(`${a.email} still cannot sign in`);
    }
    tokens.set(a.email, token);
    ids[a.email] = (await call(token, 'GET', '/api/auth/me')).data.id;
  }
}

// ---------- reference data ----------
let exerciseIds = [];
let template;
async function ensureReference() {
  const ht = as('headtrainer@gmail.com');
  let exercises = items((await call(ht, 'GET', '/api/exercises?size=100')).data);
  const wanted = [['Khởi động đi bộ', 'WALK', '10 phút'], ['Nước kiệu nhẹ', 'TROT', '2 × 800 m'], ['Breeze nước rút', 'GALLOP', '600 m']];
  for (const [name, exerciseType, targetMetric] of wanted) {
    if (!exercises.some((e) => e.name === name)) await call(ht, 'POST', '/api/exercises', { name, exerciseType, targetMetric, description: 'Bài tập mẫu cho demo.' });
  }
  exercises = items((await call(ht, 'GET', '/api/exercises?size=100')).data);
  exerciseIds = wanted.map(([name]) => exercises.find((e) => e.name === name).id);
  const list = items((await call(ht, 'GET', '/api/program-templates?status=ACTIVE&size=50')).data);
  let summaryRow = list.find((t) => t.name?.startsWith('Demo · Nền tảng')) ?? null;
  if (!summaryRow) {
    const created = (await call(ht, 'POST', '/api/program-templates', {
      name: 'Demo · Nền tảng thể lực', goals: 'Làm quen và xây nền thể lực ở cường độ thấp', suitableHorseProfile: 'Ngựa mới hoạt động hoặc vừa hồi phục',
      recommendedDistanceUseCase: 'Mọi cự ly', recommendedDuration: '4 tuần', workloadIntensityGuidance: 'Thấp', preferredSurfaces: 'Cát', recoveryGuidance: 'Đi bộ thả lỏng sau buổi',
      requiredSkillIds: [], phases: [{ phaseOrder: 1, name: 'Làm quen', objective: 'Quan sát có giám sát', recommendedDuration: '2 tuần', intensityGuidance: 'Thấp' }], strengthFocus: [],
    })).data;
    summaryRow = created.template;
  }
  const detail = (await call(ht, 'GET', `/api/program-templates/${summaryRow.id}`)).data;
  template = { versionId: detail.version.id, phaseId: detail.version.phases[0].id };
}

// ---------- photos ----------
let photoQueue = null;
async function uploadPhoto(horse) {
  if (!PHOTO_DIR) return;
  const { readdirSync, readFileSync } = await import('node:fs');
  const { join, extname } = await import('node:path');
  const types = { '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp' };
  photoQueue ??= readdirSync(PHOTO_DIR).filter((f) => types[extname(f).toLowerCase()]).sort();
  const file = photoQueue.shift();
  if (!file) { console.log(`  (no photo left for ${horse.name})`); return; }
  const form = new FormData();
  form.append('file', new Blob([readFileSync(join(PHOTO_DIR, file))], { type: types[extname(file).toLowerCase()] }), file);
  const res = await fetch(`${API}/api/horses/${horse.id}/photos`, { method: 'POST', headers: { Authorization: `Bearer ${as('cm')}` }, body: form });
  if (res.status !== 201 && res.status !== 200) throw new Error(`photo upload ${file} -> ${res.status}`);
}

// ---------- building blocks ----------
async function freeBoxes() { return items((await call(as('cm'), 'GET', '/api/stable-boxes?status=FREE&size=200')).data); }
async function draftHorse(label, owner) {
  const cm = as('cm');
  const horse = (await call(cm, 'POST', '/api/horses', { name: `${label} ${TAG}`, breed: 'Thoroughbred', sex: 'Gelding', foaledDate: '2021-03-15', countryOfOrigin: 'Việt Nam' })).data;
  await call(cm, 'PUT', `/api/horses/${horse.id}/pedigree`, { sire: { name: 'Demo Sire' }, dam: { name: 'Demo Dam' } });
  await call(cm, 'POST', `/api/horses/${horse.id}/ownerships`, { ownerUserId: ids[owner], sharePercentage: 100, effectiveFrom: today(), isManagingOwner: true });
  const box = (await freeBoxes())[0];
  if (!box) throw new Error('No free stable box left; run scripts/demo/add-demo-stable-boxes.sql');
  await call(cm, 'POST', `/api/horses/${horse.id}/stable-assignments`, { stableBoxId: box.id, effectiveFrom: today(), reason: 'Chuẩn bị dữ liệu demo' });
  await uploadPhoto(horse);
  return { ...horse, box: box.boxCode };
}
async function activeHorse(label, owner = 'horseowner1@gmail.com', weightKg = 480) {
  const horse = await draftHorse(label, owner);
  await call(as('veterinarian@gmail.com'), 'POST', `/api/horses/${horse.id}/baseline-examinations`, { weightKg, healthStatus: 'FIT', clinicalNotes: 'Khám ban đầu cho dữ liệu demo.' });
  return horse;
}
async function program(horse, name) {
  const p = (await call(as('headtrainer@gmail.com'), 'POST', '/api/training-programs', {
    horseId: horse.id, templateVersionId: template.versionId, name: `${horse.name} · ${name}`, goals: 'Làm quen có giám sát', targetMetrics: 'Quan sát', periodStart: today(), periodEnd: plusDays(30), notes: 'Dữ liệu demo',
    phases: [{ templateVersionPhaseId: template.phaseId, phaseOrder: 1, name: 'Làm quen', objective: 'Quan sát có giám sát', startDate: today(), endDate: plusDays(30), intensityMin: 'LOW', intensityMax: 'LOW', workload: 'Đi bộ, nước kiệu nhẹ' }],
    trainer: { trainerUserId: ids['trainer1@gmail.com'], effectiveFrom: today(), reason: 'Phân công cho demo', warningsAcknowledged: true },
  })).data;
  return { id: p.id, phaseId: p.phases[0].id };
}
async function session(prog, rider, start, end, focus) {
  const t = as('trainer1@gmail.com');
  const s = (await call(t, 'POST', '/api/training-sessions', { horseProgramPhaseId: prog.phaseId, scheduledStart: start.toISOString(), scheduledEnd: end.toISOString(), intensity: 'LOW',
    workRiderUserId: ids[rider], riderWarningAcknowledged: true, surface: 'SAND', focus,
    exercises: [{ exerciseDefinitionId: exerciseIds[0], exerciseOrder: 1, durationMin: 10, intensity: 'LOW', surface: 'SAND' }, { exerciseDefinitionId: exerciseIds[1], exerciseOrder: 2, durationMin: 15, intensity: 'LOW', surface: 'SAND' }] })).data;
  await call(t, 'POST', `/api/training-sessions/${s.id}/groom-assignment`, { groomUserId: ids['groom@gmail.com'] });
  return s;
}
const CHECKS = ['GENERAL_CONDITION', 'APPETITE_BEHAVIOUR', 'LEGS_HOOVES', 'MOVEMENT', 'TEMPERATURE'];
const precheck = (s) => call(as('groom@gmail.com'), 'POST', `/api/training-sessions/${s.id}/pre-session-check`, { checks: Object.fromEntries(CHECKS.map((k) => [k, { result: 'NORMAL' }])) });
const incident = (horse, incidentType, observedSeverity, description) => call(as('groom@gmail.com'), 'POST', '/api/incidents', { horseId: horse.id, incidentType, observedSeverity, description, observedAt: new Date().toISOString() });
const restrict = (horse, level, reason, medicalCaseId) => call(as('veterinarian@gmail.com'), 'POST', `/api/horses/${horse.id}/medical-restrictions`, { level, reason, effectiveAt: new Date().toISOString(), reviewDate: plusDays(14), medicalCaseId: medicalCaseId ?? null });
async function openCase(horse, reason, health) {
  const vet = as('veterinarian@gmail.com');
  const c = (await call(vet, 'POST', '/api/medical/cases', { horseId: horse.id, reason })).data;
  await call(vet, 'POST', `/api/medical/cases/${c.id}/examinations`, { symptoms: reason, diagnosis: 'Theo dõi', severity: 'MODERATE', examinedAt: new Date().toISOString(), healthStatus: health, bodyCondition: 'Bình thường' });
  return c;
}
const plan = (c, instruction) => call(as('veterinarian@gmail.com'), 'PUT', `/api/medical/cases/${c.id}/treatment-plan`, {
  treatmentMedicationInstructions: 'Chườm lạnh 15 phút, ngày 2 lần', restInstructions: 'Nghỉ tập 3 ngày', careInstructions: 'Theo dõi dáng đi',
  groomTasks: [{ instruction, taskDate: demoDay(), scheduledSlot: '07:00', assignedGroomUserId: ids['groom@gmail.com'] }], reexaminationDate: plusDays(3) });

// ---------- sets ----------
async function setD() {
  const waiting = await draftHorse('D-chờ-khám', 'horseowner1@gmail.com');
  note('D', 'D-chờ-khám (nháp, chỉ chờ Vet khám ban đầu)', `${waiting.name} · ${waiting.horseCode} · ${waiting.box}`);
  const active = await activeHorse('D-hoạt-động', 'horseowner1@gmail.com', 465);
  note('D', 'D-hoạt-động (của horseowner1)', `${active.name} · ${active.horseCode}`);
  const other = await activeHorse('D-của-chủ-2', 'horseowner2@gmail.com', 470);
  note('D', 'D-của-chủ-2 (horseowner1 không thấy)', `${other.name} · ${other.horseCode}`);
}
async function setH() {
  const recovered = await activeHorse('H-chưa-có-chương-trình');
  const r = (await restrict(recovered, 'LIMITED', 'Theo dõi sau va chạm nhẹ')).data;
  await call(as('veterinarian@gmail.com'), 'POST', `/api/medical/restrictions/${r.id}/lift`, {});
  note('H', 'H-chưa-có-chương-trình (hạn chế đã gỡ, dùng cho wizard và chương trình phục hồi)', `${recovered.name} · ${recovered.horseCode}`);
  const withProgram = await activeHorse('H-có-chương-trình');
  const p = await program(withProgram, 'nền tảng');
  const t1 = as('trainer1@gmail.com');
  await call(t1, 'POST', `/api/training-programs/${p.id}/adjustment-requests`, { horseProgramPhaseId: p.phaseId, requestType: 'PHASE_OBJECTIVE', currentValue: 'Quan sát có giám sát', requestedValue: 'Thêm 5 phút đi bộ thả lỏng', reason: 'Ngựa hồi phục nhịp thở chậm' });
  await call(t1, 'POST', `/api/training-programs/${p.id}/adjustment-requests`, { horseProgramPhaseId: p.phaseId, requestType: 'PHASE_TIMELINE', currentValue: '30 ngày', requestedValue: 'Kéo dài thêm 3 ngày', reason: 'Ngựa mới chuyển chuồng' });
  note('H', 'H-có-chương-trình (giao trainer1, 2 đề xuất chờ duyệt)', `${withProgram.name} · ${withProgram.horseCode}`);
  const locked = await activeHorse('H-khóa');
  await restrict(locked, 'LOCKED', 'Nghi tổn thương gân, chờ chụp chiếu');
  note('H', 'H-khóa (Head Trainer lập chương trình bị từ chối)', `${locked.name} · ${locked.horseCode}`);
  const limited = await activeHorse('H-hạn-chế');
  await program(limited, 'nền tảng');
  const lr = (await restrict(limited, 'LIMITED', 'Không chạy nhanh trong 2 tuần')).data;
  await call(as('veterinarian@gmail.com'), 'PUT', `/api/medical/restrictions/${lr.id}/exercise-rules`, { rules: [{ ruleType: 'PROHIBITED', exerciseId: exerciseIds[2] }] });
  note('H', 'H-hạn-chế (cấm "Breeze nước rút"; trainer1 tạo buổi có bài này sẽ bị chặn)', `${limited.name} · ${limited.horseCode}`);
}
async function setT() {
  const a = await activeHorse('T-tập-đủ'); const pa = await program(a, 'thực hiện');
  // No global heart-rate default is configured (app.medical.thresholds.* unset), so the Vet sets a per-horse critical
  // threshold (Q-07 override) to make the realtime alert visible in the demo. 180 bpm is the Figma SCR-MED-014 prototype value.
  await call(as('veterinarian@gmail.com'), 'PUT', `/api/horses/${a.id}/medical-thresholds`, { metricType: 'HEART_RATE', thresholdValue: 180, reason: 'Ngưỡng nhịp tim riêng cho buổi giám sát demo', effectiveFrom: new Date().toISOString() });
  const b = await activeHorse('T-kiểm-tra-bất-thường'); const pb = await program(b, 'thực hiện');
  const c = await activeHorse('T-báo-vấn-đề'); const pc = await program(c, 'thực hiện');
  // Completed history on horse A, finished before A's demo session starts (no overlap).
  const histStart = new Date(Date.now() + 25_000);
  const hist = await session(pa, 'workrider1@gmail.com', histStart, new Date(histStart.getTime() + 4 * 60_000), 'Buổi đã hoàn thành chờ đánh giá');
  await precheck(hist);
  const start = START ?? new Date(Date.now() + 6 * 60_000), end = new Date(start.getTime() + HOURS * 3600_000);
  const sa = await session(pa, 'workrider1@gmail.com', start, end, 'Groom kiểm tra, Work Rider tập đủ, giám sát realtime');
  const sb = await session(pb, 'workrider3@gmail.com', start, end, 'Groom kiểm tra phát hiện bất thường');
  const sc = await session(pc, 'workrider2@gmail.com', start, end, 'Work Rider báo vấn đề khi đang cưỡi');
  await precheck(sc);
  console.log('  waiting for the history session to start…');
  await sleep(Math.max(0, histStart.getTime() - Date.now()) + 2_000);
  const r1 = as('workrider1@gmail.com');
  await call(r1, 'POST', `/api/training-sessions/${hist.id}/start`, {});
  await call(r1, 'POST', `/api/training-sessions/${hist.id}/complete`, {});
  await call(r1, 'POST', `/api/training-sessions/${hist.id}/rider-feedback`, { responses: { horseResponse: 'Bình tĩnh, hợp tác', balance: 'Ổn định', breathing: 'Đều', notes: 'Buổi làm quen cho demo.' } });
  const at = (d) => new Date(d.getTime() + VN).toISOString().slice(11, 16);
  note('T', `T-tập-đủ ${at(start)}–${at(end)} (chưa kiểm tra; workrider1; ngưỡng nhịp tim 180 do Vet đặt)`, `${a.name} · ${sa.sessionCode}`);
  note('T', `T-kiểm-tra-bất-thường ${at(start)}–${at(end)} (chưa kiểm tra; workrider3)`, `${b.name} · ${sb.sessionCode}`);
  note('T', `T-báo-vấn-đề ${at(start)}–${at(end)} (đã kiểm tra đạt; workrider2)`, `${c.name} · ${sc.sessionCode}`);
  note('T', 'T-đã-hoàn-thành (chờ trainer1 đánh giá)', `${a.name} · ${hist.sessionCode}`);
}
async function setY() {
  const y1 = await activeHorse('Y-nhẹ-và-tối-thiểu', 'horseowner2@gmail.com');
  await incident(y1, 'OFF_FEED', 'LOW', 'Bỏ nửa bữa sáng, uống nước bình thường.');
  await incident(y1, 'HOOF_OR_LEG_PROBLEM', 'MEDIUM', 'Bước hơi ngắn chân trước trái khi dắt ra sân.');
  note('Y', 'Y-nhẹ + Y-tối-thiểu (2 sự cố Mới báo)', `${y1.name} · ${y1.horseCode}`);
  const y2 = await activeHorse('Y-đầy-đủ', 'horseowner2@gmail.com');
  await incident(y2, 'LAMENESS', 'HIGH', 'Khập khiễng rõ chân sau phải sau khi vận động.');
  note('Y', 'Y-đầy-đủ (sự cố Mức độ cao)', `${y2.name} · ${y2.horseCode}`);
  const y3 = await activeHorse('Y-ca-đang-mở');
  const c3 = await openCase(y3, 'Sưng nhẹ khớp cổ chân trước phải', 'MONITORING');
  await plan(c3, 'Chườm lạnh khớp cổ chân trước phải 15 phút');
  await incident(y3, 'HOOF_OR_LEG_PROBLEM', 'MEDIUM', 'Khớp cổ chân vẫn còn ấm khi kiểm tra buổi chiều.');
  note('Y', `Y-ca-đang-mở (ca #${c3.id}, 1 việc Groom chưa làm) + Y-gắn-ca (sự cố Mới báo cùng ngựa)`, `${y3.name} · ${y3.horseCode}`);
  const y4 = await activeHorse('Y-ca-sẵn-sàng-đóng');
  const c4 = await openCase(y4, 'Viêm gân nhẹ chân trước trái', 'INJURED');
  await plan(c4, 'Ngâm chân nước lạnh 10 phút');
  const groom = as('groom@gmail.com');
  const tasks = items((await call(groom, 'GET', '/api/groom/treatment-tasks?size=100')).data).filter((t) => t.horseId === y4.id && !t.completedAt);
  for (const t of tasks) await call(groom, 'POST', `/api/groom/treatment-tasks/${t.id}/complete`, {});
  await restrict(y4, 'LOCKED', 'Viêm gân, nghỉ tập đến khi tái khám', c4.id);
  note('Y', `Y-ca-sẵn-sàng-đóng (ca #${c4.id}, việc Groom đã xong, đang Khóa)`, `${y4.name} · ${y4.horseCode}`);
  // Two incidents the Vet triages that come from Flow 2, created through the real gates (pre-check, in-ride report).
  const y5 = await activeHorse('Y-groom-báo-kiểm-tra'); const p5 = await program(y5, 'theo dõi');
  const y6 = await activeHorse('Y-rider-báo-khi-cưỡi'); const p6 = await program(y6, 'theo dõi');
  const rideStart = new Date(Date.now() + 25_000);
  const ride = await session(p6, 'workrider3@gmail.com', rideStart, new Date(rideStart.getTime() + 4 * 60_000), 'Buổi tập ngắn, Work Rider báo vấn đề');
  await precheck(ride);
  const checkStart = new Date(Date.now() + 10 * 60_000);
  const checked = await session(p5, 'workrider3@gmail.com', checkStart, new Date(checkStart.getTime() + 30 * 60_000), 'Groom kiểm tra trước buổi tập');
  const abnormal = (await call(as('groom@gmail.com'), 'POST', `/api/training-sessions/${checked.id}/pre-session-check`, { checks: Object.fromEntries(CHECKS.map((k) => [k,
    k === 'LEGS_HOOVES' ? { result: 'ABNORMAL', observation: 'Móng trước phải nóng, ngựa né khi chạm.' } : { result: 'NORMAL' }])) })).data;
  await call(as('groom@gmail.com'), 'POST', '/api/incidents', { horseId: y5.id, preSessionCheckId: abnormal.id,
    incidentType: 'HOOF_OR_LEG_PROBLEM', observedSeverity: 'MEDIUM', description: 'Kiểm tra trước buổi tập: móng trước phải nóng, ngựa né khi chạm.', observedAt: new Date().toISOString() });
  note('Y', `Y-groom-báo-kiểm-tra (sự cố từ kiểm tra trước buổi tập, buổi ${checked.sessionCode} bị chặn)`, `${y5.name} · ${y5.horseCode}`);
  console.log('  waiting for the in-ride session to start…');
  await sleep(Math.max(0, rideStart.getTime() - Date.now()) + 2_000);
  const r3 = as('workrider3@gmail.com');
  await call(r3, 'POST', `/api/training-sessions/${ride.id}/start`, {});
  await call(r3, 'POST', `/api/training-sessions/${ride.id}/report-issue`, { description: 'Đang chạy nước kiệu thì ngựa hụt nhịp chân sau trái, đã dừng.', incidentType: 'LAMENESS', observedSeverity: 'HIGH', observedAt: new Date().toISOString() });
  note('Y', `Y-rider-báo-khi-cưỡi (Work Rider báo Khập khiễng khi đang cưỡi, buổi ${ride.sessionCode} bị gián đoạn)`, `${y6.name} · ${y6.horseCode}`);
}
async function setL() {
  const l = await activeHorse('L-chăm-sóc');
  const cm = as('cm');
  // Slots are plain HH:mm (Vietnam time). Overdue = still open more than 60 minutes after its slot.
  const at = (minutes) => new Date(Date.now() + VN + minutes * 60_000).toISOString().slice(11, 16);
  // With RH_DEMO_START the slots sit around the review window: one overdue before it, one due late in it (Flow 4 is last).
  const late = START ? hhmm(new Date(START.getTime() - 90 * 60_000)) : at(-90);
  const due = START ? hhmm(new Date(START.getTime() + 90 * 60_000)) : at(-10);
  await call(cm, 'POST', '/api/stable-care/care-plans', { horseId: l.id, groomUserId: ids['groom@gmail.com'], from: today(), to: plusDays(7), notes: 'Kế hoạch demo',
    items: [{ taskType: 'LEG_SOAKING', timeSlot: late, instructions: 'Ngâm chân nước mát 10 phút, lau khô kỹ.' },
      { taskType: 'CLEANING', timeSlot: due, instructions: 'Dọn chất độn, kiểm tra máng nước.' },
      { taskType: 'BATHING', timeSlot: '23:30', instructions: 'Tắm và lau khô.' }] });
  await call(cm, 'POST', '/api/stable-care/nutrition-plans', { horseId: l.id, groomUserId: ids['groom@gmail.com'], from: today(), to: plusDays(7), notes: 'Khẩu phần demo',
    items: [{ meal: due, component: 'HAY', quantity: 4, unit: 'kg' }, { meal: due, component: 'GRAIN', quantity: 2, unit: 'kg' }] });
  note('L', `L-chăm-sóc (quá giờ ${late}, cần làm ${due}, chưa tới 23:30; khẩu phần ${due})`, `${l.name} · ${l.horseCode}`);
  const stock = items((await call(cm, 'GET', '/api/stable-care/inventory')).data);
  let item = stock.find((s) => s.name === 'Cỏ khô demo');
  if (!item) {
    item = (await call(cm, 'POST', '/api/stable-care/inventory', { name: 'Cỏ khô demo', category: 'FEED', unit: 'kg', threshold: 50, active: true })).data;
    await call(cm, 'POST', `/api/stable-care/inventory/${item.id}/movements`, { changeQuantity: 200, expectedQuantity: 0, note: 'Nhập kho đầu kỳ cho demo' });
  }
  note('L', 'Kho', 'Cỏ khô demo');
  for (const n of [1, 2]) {
    const email = `demo.dangky${n}.${TAG.replace('-', '')}@gmail.com`;
    await call(null, 'POST', '/api/auth/register', { fullName: `Người đăng ký demo ${n}`, email, password: DEMO_PASSWORD, confirmPassword: DEMO_PASSWORD });
    note('L', `L-đăng-ký-${n} (chờ duyệt)`, email);
  }
}

// ---------- run ----------
(async () => {
  console.log(`Racehorse demo data · tag ${TAG} · API ${API}`);
  await ensureAccounts();
  await ensureReference();
  const need = (SETS.has('D') ? 3 : 0) + (SETS.has('H') ? 4 : 0) + (SETS.has('T') ? 3 : 0) + (SETS.has('Y') ? 6 : 0) + (SETS.has('L') ? 1 : 0) + 1;
  const free = (await freeBoxes()).length;
  if (free < need) throw new Error(`Need ${need} free stable boxes (one spare for Flow 1), found ${free}. Run scripts/demo/add-demo-stable-boxes.sql first.`);
  for (const [key, fn] of [['D', setD], ['H', setH], ['T', setT], ['Y', setY], ['L', setL]]) {
    if (!SETS.has(key)) continue;
    console.log(`Set ${key}`);
    await fn();
  }
  console.log('\nSUMMARY (set | role | horse / value)');
  for (const line of summary) console.log(line);
  console.log(`DEMO_DATA_PASS tag ${TAG}`);
})().catch((e) => { console.error(`DEMO_DATA_FAILED ${e.message}`); process.exitCode = 1; });
