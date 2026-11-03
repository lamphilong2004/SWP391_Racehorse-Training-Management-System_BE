#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Blocked, assertTestTarget, classifyHttp, controllerOperations, envelope, operations, positiveId, required, source, testUrl } from './core037-lib.mjs';

const backend = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const frontend = process.env.CORE037_FE_REPO || resolve(backend, '../SWP391_Racehorse-Training-Management-System_FE_CORE037');
const BE_CONTRACT = 'origin/feat/CORE-006-flow3-medical-contract';
const BE_026 = 'origin/feat/CORE-026-incident-presession-gate';
const BE_035 = 'origin/feat/CORE-035-medical-case-treatment-recovery';
const FE_036 = 'origin/feat/CORE-036-vet-medical-groom-ui';

function result(label, status, detail = '') {
  console.log(`${status.padEnd(8)} ${label}${detail ? ` — ${detail}` : ''}`);
  return status;
}

function contract() {
  const spec = source(backend, BE_CONTRACT, 'docs/api/flow3-medical.openapi.yaml');
  const flow2 = source(backend, 'origin/feat/CORE-005-contract-flow2', 'docs/api/flow2-training.openapi.yaml');
  const incident = source(backend, BE_026, 'src/main/java/com/racehorse/core026/Core026Controller.java');
  const incidentNotes = source(backend, BE_026, 'docs/implementation/CORE-026.md');
  const medical = source(backend, BE_035, 'src/main/java/com/racehorse/core035/MedicalController.java');
  const dto = source(backend, BE_035, 'src/main/java/com/racehorse/core035/MedicalDtos.java');
  const feModel = source(frontend, FE_036, 'src/features/medical/models.ts');
  const feService = source(frontend, FE_036, 'src/features/medical/service.ts');
  const feRuntime = source(frontend, FE_036, 'src/features/medical/runtimeClient.ts');
  const expected = operations(spec);
  const implemented = new Map([...controllerOperations(incident), ...controllerOperations(medical)]);
  const checks = [];
  for (const operation of [
    'POST /api/incidents', 'GET /api/incidents', 'GET /api/incidents/{incidentId}',
    'POST /api/incidents/{incidentId}/review', 'POST /api/medical/cases',
    'GET /api/medical/cases', 'GET /api/medical/cases/{caseId}',
    'POST /api/medical/cases/{caseId}/examinations', 'POST /api/medical/cases/{caseId}/injuries',
    'PUT /api/medical/cases/{caseId}/treatment-plan', 'POST /api/medical/cases/{caseId}/re-examinations',
    'GET /api/groom/treatment-tasks', 'POST /api/groom/treatment-tasks/{taskId}/complete',
    'GET /api/horses/{horseId}/training-availability',
    'POST /api/horses/{horseId}/medical-restrictions',
    'POST /api/medical/restrictions/{restrictionId}/lift',
  ]) {
    const status = !expected.has(operation) ? 'FAIL' : implemented.has(operation) ? 'MATCHED' : operation.includes('/review') ? 'MISMATCHED' : 'BLOCKED';
    checks.push(result(operation, status, ['BLOCKED', 'MISMATCHED'].includes(status) ? 'contract only; absent from inspected CORE-026/035 controllers' : ''));
  }
  const predicates = [
    ['FE medical HTTP adapter', /mode: 'unavailable'/.test(feService) && !/fetch\([^)]*\/api\/medical/.test(feService), 'BLOCKED', 'real mode explicitly unavailable'],
    ['FE numeric IDs vs BE int64', /horseId: string/.test(feModel) && /Long horseId/.test(dto), 'MISMATCHED', 'conversion and validation needed'],
    ['FE injury position vs BE opaque marker', /position3d\?: \{ x: number/.test(feModel) && /String position3d/.test(dto), 'MISMATCHED', 'G4 serialization decision needed'],
    ['FE medication array vs BE instruction text', /medications\?: \{ name: string/.test(feModel) && /String treatmentMedicationInstructions/.test(dto), 'MISMATCHED', 'clinical data mapping needed'],
    ['FE completion notes vs BE empty body', /completionNotes\?: string/.test(feModel) && /if \(!body\.isEmpty\(\)\)/.test(medical), 'MISMATCHED', 'G2 storage decision needed'],
    ['CORE-005 abnormal auto Incident vs CORE-026 explicit', /ABNORMAL: an incident is created/.test(flow2) && /không tự tạo Incident/.test(incidentNotes), 'BLOCKED', 'BR-23/DEC-007 unresolved'],
    ['FE auth/me bootstrap', /\/api\/auth\/me/.test(feRuntime), 'MATCHED', 'shared contract path; runtime integration unverified'],
  ];
  for (const [label, observed, ifTrue, detail] of predicates)
    checks.push(result(label, observed ? ifTrue : 'UNVERIFIED', detail));
  if (checks.some(x => ['MISMATCHED', 'BLOCKED', 'FAIL', 'UNVERIFIED'].includes(x))) process.exitCode = 1;
}

async function request(base, path, token) {
  const response = await fetch(`${base}${path}`, {
    headers: { Accept: 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    signal: AbortSignal.timeout(8000),
    redirect: 'manual',
  });
  const body = await response.json().catch(() => null);
  return { status: response.status, body };
}

async function allGroomTasks(base, token) {
  const items = [];
  for (let page = 0; page < 50; page++) {
    const r = await request(base, `/api/groom/treatment-tasks?page=${page}&size=100`, token);
    if (r.status !== 200 || !envelope(r.body, true) || !Array.isArray(r.body.data?.items) ||
        !Number.isSafeInteger(r.body.data.totalElements) || r.body.data.totalElements < 0)
      throw new Blocked(`Groom work list is unavailable or has incompatible envelope (HTTP ${r.status})`);
    items.push(...r.body.data.items);
    if (items.length >= r.body.data.totalElements) return items;
    if (r.body.data.items.length === 0) throw new Blocked('Groom pagination ended before totalElements');
  }
  throw new Blocked('Groom work list exceeds the bounded 50-page verification limit');
}

async function live() {
  const base = assertTestTarget();
  const vet = required('CORE037_VET_JWT');
  const groom = required('CORE037_GROOM_JWT');
  const invalid = 'core037.invalid.jwt';
  const health = await request(base, '/api/health');
  if (health.status !== 200) throw new Blocked(`Backend health returned ${health.status}`);
  result('Backend health', 'PASS');
  const actors = {};
  for (const [name, token, role] of [['VET', vet, 'VETERINARIAN'], ['GROOM', groom, 'GROOM']]) {
    const r = await request(base, '/api/auth/me', token);
    if (r.status !== 200 || !envelope(r.body, true) || !r.body.data?.roles?.includes(role) || r.body.data.accountStatus !== 'ACTIVE')
      throw new Blocked(`${name} JWT is not a verified ACTIVE ${role} via GET /api/auth/me (HTTP ${r.status})`);
    actors[name] = r.body.data;
    result(`GET /api/auth/me ${name}`, 'PASS');
  }
  if (actors.VET.id === actors.GROOM.id) throw new Blocked('VET and GROOM JWT resolve to the same actor');
  const vetCases = await request(base, '/api/medical/cases?page=0&size=1', vet);
  if (vetCases.status !== 200 || !envelope(vetCases.body, true) || !Array.isArray(vetCases.body.data?.items))
    throw new Blocked(`Medical case list is unavailable or has incompatible envelope (HTTP ${vetCases.status})`);
  const groomTasks = await allGroomTasks(base, groom);
  result('Vet case list and Groom work list', 'PASS');
  let failed = false;
  for (const [label, path, token, expected] of [
    ['Missing JWT', '/api/medical/cases?page=0&size=1', null, 401],
    ['Invalid JWT', '/api/medical/cases?page=0&size=1', invalid, 401],
    ['Wrong role Groom -> cases', '/api/medical/cases?page=0&size=1', groom, 403],
    ['Wrong role Vet -> Groom tasks', '/api/groom/treatment-tasks?page=0&size=1', vet, 403],
  ]) {
    const r = await request(base, path, token);
    const status = !envelope(r.body, false) && r.status === expected ? 'FAIL' : classifyHttp(r.status, expected);
    result(label, status, `HTTP ${r.status}; expected ${expected}`);
    if (status !== 'PASS') failed = true;
  }
  for (const task of groomTasks) {
    const serialized = JSON.stringify(task).toLowerCase();
    if (/diagnosis|prescription|treatmentmedicationinstructions|clinicalseverity|medicalcase/.test(serialized)) {
      result('Groom clinical projection', 'FAIL', 'clinical field exposed'); failed = true; break;
    }
  }
  if (!failed) result('Groom clinical projection', 'PASS');
  if (process.env.CORE037_CASE_ID) {
    const id = positiveId('CORE037_CASE_ID');
    const r = await request(base, `/api/medical/cases/${id}`, vet);
    const status = r.status === 200 && (!envelope(r.body, true) || String(r.body.data.id) !== id) ? 'FAIL' : classifyHttp(r.status, 200);
    result('Persisted case API read', status, `HTTP ${r.status}`);
    if (status !== 'PASS') failed = true;
  } else result('Persisted case API read', 'BLOCKED', 'CORE037_CASE_ID absent');
  if (process.env.CORE037_INCIDENT_ID) {
    const id = positiveId('CORE037_INCIDENT_ID');
    const r = await request(base, `/api/incidents/${id}`, vet);
    const status = r.status === 200 && (!envelope(r.body, true) || String(r.body.data.id) !== id) ? 'FAIL' : classifyHttp(r.status, 200);
    result('Persisted Incident API read', status, `HTTP ${r.status}`);
    if (status !== 'PASS') failed = true;
  } else result('Persisted Incident API read', 'BLOCKED', 'CORE037_INCIDENT_ID absent');
  if (process.env.CORE037_GROOM2_JWT && process.env.CORE037_TASK_ID_OWNED_BY_GROOM1) {
    const id = positiveId('CORE037_TASK_ID_OWNED_BY_GROOM1');
    const second = await request(base, '/api/auth/me', required('CORE037_GROOM2_JWT'));
    const otherList = await allGroomTasks(base, process.env.CORE037_GROOM2_JWT);
    if (second.status !== 200 || second.body?.data?.id === actors.GROOM.id ||
      groomTasks.every(x => String(x.id) !== id) ||
      otherList.some(x => String(x.id) === id)) {
      result('Cross-Groom row scope', 'FAIL'); failed = true;
    } else result('Cross-Groom row scope', 'PASS');
  } else result('Cross-Groom row scope', 'BLOCKED', 'second Groom JWT and approved task fixture required');
  if (process.env.CORE037_FRONTEND_URL) {
    const fe = testUrl('CORE037_FRONTEND_URL');
    const page = await fetch(`${fe}/medical`, { signal: AbortSignal.timeout(8000), redirect: 'manual' });
    result('Frontend availability', page.status === 200 ? 'PASS' : 'BLOCKED', `HTTP ${page.status}; route authorization needs browser verification`);
  } else result('Frontend availability', 'BLOCKED', 'CORE037_FRONTEND_URL absent');
  if (failed) process.exitCode = 1;
}

function sql() {
  if (process.env.CORE037_TEST_ENV !== 'isolated-test' || process.env.CORE037_SQL_READONLY !== 'yes')
    throw new Blocked('SQL mode requires CORE037_TEST_ENV=isolated-test and CORE037_SQL_READONLY=yes');
  const server = required('CORE037_SQL_SERVER');
  const database = required('CORE037_SQL_DATABASE');
  const user = required('CORE037_SQL_USER');
  required('SQLCMDPASSWORD');
  const caseId = positiveId('CORE037_CASE_ID');
  const incidentId = positiveId('CORE037_INCIDENT_ID');
  const taskId = positiveId('CORE037_TASK_ID_OWNED_BY_GROOM1');
  const restrictionId = positiveId('CORE037_RESTRICTION_ID');
  const query = `SET NOCOUNT ON;
SELECT 'incident|' + CONVERT(varchar(20), COUNT_BIG(*)) FROM dbo.incident WHERE id=${incidentId} AND medical_case_id=${caseId};
SELECT 'case|' + CONVERT(varchar(20), COUNT_BIG(*)) FROM dbo.medical_case WHERE id=${caseId};
SELECT 'exam|' + CONVERT(varchar(20), COUNT_BIG(*)) FROM dbo.veterinary_examination WHERE medical_case_id=${caseId} AND exam_type='EXAMINATION';
SELECT 'injury|' + CONVERT(varchar(20), COUNT_BIG(*)) FROM dbo.injury WHERE medical_case_id=${caseId};
SELECT 'plan|' + CONVERT(varchar(20), COUNT_BIG(*)) FROM dbo.treatment_plan WHERE medical_case_id=${caseId};
SELECT 'task|' + CONVERT(varchar(20), COUNT_BIG(*)) FROM dbo.care_task t JOIN dbo.treatment_plan p ON p.id=t.treatment_plan_id WHERE p.medical_case_id=${caseId} AND t.id=${taskId} AND t.completed_at IS NOT NULL AND t.completed_by_user_id IS NOT NULL;
SELECT 'reexam|' + CONVERT(varchar(20), COUNT_BIG(*)) FROM dbo.veterinary_examination WHERE medical_case_id=${caseId} AND exam_type='RE_EXAMINATION';
SELECT 'restriction|' + CONVERT(varchar(20), COUNT_BIG(*)) FROM dbo.medical_restriction r JOIN dbo.medical_case c ON c.horse_id=r.horse_id WHERE c.id=${caseId} AND r.id=${restrictionId};`;
  const out = execFileSync('sqlcmd', ['-S', server, '-d', database, '-U', user, '-b', '-l', '5', '-h', '-1', '-W', '-Q', query],
    { encoding: 'utf8', timeout: 20000, env: process.env });
  const counts = new Map([...out.matchAll(/^(incident|case|exam|injury|plan|task|reexam|restriction)\|(\d+)\s*$/gm)].map(m => [m[1], Number(m[2])]));
  let failed = false;
  for (const key of ['incident', 'case', 'exam', 'injury', 'plan', 'task', 'reexam', 'restriction']) {
    const count = counts.get(key);
    result(`SQL Server ${key}`, count > 0 ? 'PASS' : 'FAIL', `rows=${count ?? 'missing result'}`);
    if (!(count > 0)) failed = true;
  }
  if (failed) process.exitCode = 1;
}

try {
  const mode = process.argv[2];
  if (mode === 'contract') contract();
  else if (mode === 'live') await live();
  else if (mode === 'sql') sql();
  else throw new Blocked('Usage: node scripts/integration/core037.mjs contract|live|sql');
} catch (error) {
  if (error instanceof Blocked || error?.cause instanceof Blocked) {
    result('CORE-037', 'BLOCKED', error.message); process.exitCode = 2;
  } else {
    result('CORE-037', 'FAIL', error?.message || String(error)); process.exitCode = 1;
  }
}
